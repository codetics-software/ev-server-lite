package fr.codetics.ev_server.services.stations

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.Csv
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.NotFound
import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.core.lock
import fr.codetics.ev_server.core.pageRequest
import fr.codetics.ev_server.core.randomToken
import fr.codetics.ev_server.models.api.MutationResponse
import fr.codetics.ev_server.models.api.PageResponse
import fr.codetics.ev_server.models.commands.CommandReceipt
import fr.codetics.ev_server.models.entities.ConnectorEntity
import fr.codetics.ev_server.models.entities.ConnectorKey
import fr.codetics.ev_server.models.entities.StationEntity
import fr.codetics.ev_server.models.entities.StationKey
import fr.codetics.ev_server.models.entities.TransactionEntity
import fr.codetics.ev_server.models.stations.AutoChargeSetting
import fr.codetics.ev_server.models.stations.ConnectorResponse
import fr.codetics.ev_server.models.stations.ParameterInput
import fr.codetics.ev_server.models.stations.ProvisionedStation
import fr.codetics.ev_server.models.stations.StationEventResponse
import fr.codetics.ev_server.models.stations.StationInError
import fr.codetics.ev_server.models.stations.StationInput
import fr.codetics.ev_server.models.stations.StationParameterResponse
import fr.codetics.ev_server.models.stations.StationResponse
import fr.codetics.ev_server.models.stations.StationUpdate
import fr.codetics.ev_server.models.stations.TemplateApplication
import fr.codetics.ev_server.ocpp.OcppCommandQueue
import fr.codetics.ev_server.ocpp.OcppGateway
import fr.codetics.ev_server.ocpp.STATION_ID
import fr.codetics.ev_server.ocpp.StationIdentity
import fr.codetics.ev_server.persistence.Condition
import fr.codetics.ev_server.persistence.Queries
import fr.codetics.ev_server.persistence.anyOf
import fr.codetics.ev_server.persistence.contains
import fr.codetics.ev_server.persistence.eq
import fr.codetics.ev_server.persistence.page
import fr.codetics.ev_server.persistence.within
import fr.codetics.ev_server.repositories.ConnectorRepository
import fr.codetics.ev_server.repositories.StationEventRepository
import fr.codetics.ev_server.repositories.StationParameterRepository
import fr.codetics.ev_server.repositories.StationRepository
import fr.codetics.ev_server.repositories.TransactionRepository
import fr.codetics.ev_server.repositories.UserRepository
import fr.codetics.ev_server.services.organization.Organization
import java.math.BigDecimal
import java.util.UUID
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

data class StationFilter(
    val search: String = "",
    val siteId: UUID? = null,
    val siteAreaId: UUID? = null,
    val state: String? = null,
)

@Service
class StationService(
    private val stations: StationRepository,
    private val connectors: ConnectorRepository,
    private val transactions: TransactionRepository,
    private val users: UserRepository,
    private val stationEvents: StationEventRepository,
    private val parameters: StationParameterRepository,
    private val organization: Organization,
    private val configuration: StationConfigurationService,
    private val queue: OcppCommandQueue,
    private val gateway: OcppGateway,
    private val queries: Queries,
    private val passwords: PasswordEncoder,
    private val db: Database,
) {
    fun list(tenantId: UUID, offset: Int, limit: Int, filter: StationFilter = StationFilter()): PageResponse<StationResponse> {
        ensure(filter.search.length <= 200) { "La recherche est trop longue." }
        val conditions = mutableListOf<Condition>(eq("key.tenantId", tenantId), eq("deleted", false))
        if (filter.search.isNotBlank()) {
            conditions += anyOf(contains("name", filter.search.trim()), contains("key.stationId", filter.search.trim()))
        }
        filter.siteAreaId?.let { conditions += eq("siteAreaId", it) }
        filter.siteId?.let { site ->
            conditions +=
                within("siteAreaId", organization.siteAreas(tenantId).filter { it.data.siteId == site }.map { it.id })
        }
        when (filter.state) {
            "connected" -> conditions += eq("connected", true)
            "disconnected" -> conditions += eq("connected", false)
            "error" -> {
                val failing = inError(tenantId).map { it.station.id }
                conditions += within("key.stationId", failing)
            }
            null, "" -> Unit
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Filtre d’état inconnu.")
        }
        val page = queries.page<StationEntity>(conditions, pageRequest(offset, limit, Sort.by("name", "key.stationId")))
        return PageResponse(views(tenantId, page.content), page.totalElements)
    }

    fun find(tenantId: UUID, stationId: String): StationResponse {
        val station = station(tenantId, stationId)
        return views(tenantId, listOf(station)).first()
    }

    fun station(tenantId: UUID, stationId: String): StationEntity =
        stations.findById(StationKey(tenantId, stationId)).orElse(null)?.takeUnless { it.deleted }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Borne introuvable.")

    fun views(tenantId: UUID, page: List<StationEntity>): List<StationResponse> {
        if (page.isEmpty()) return emptyList()
        val ids = page.map { it.key.stationId }.toSet()
        val plugs =
            if (page.size == 1) {
                connectors.findAllByKeyTenantIdAndKeyStationIdOrderByKeyConnectorId(tenantId, page.first().key.stationId)
            } else {
                connectors.findAllByKeyTenantId(tenantId).filter { it.key.stationId in ids }
            }
        val active =
            transactions.findAllByTenantIdAndStoppedAtIsNull(tenantId).filter { it.stationId in ids }
                .associateBy { it.stationId to it.connectorId }
        val names =
            users.findAllByTenantIdAndIdIn(tenantId, active.values.mapNotNull { it.userId }.toSet())
                .associate { it.id to it.displayName }
        val areas = organization.siteAreas(tenantId).associate { it.id to it.data.siteId }
        val byStation = plugs.groupBy { it.key.stationId }
        return page.map { station ->
            val list =
                byStation[station.key.stationId].orEmpty().sortedBy { it.key.connectorId }.map { connector ->
                    val transaction = active[station.key.stationId to connector.key.connectorId]
                    ConnectorResponse.from(connector, transaction, transaction?.userId?.let(names::get))
                }
            StationResponse.from(station, list, station.siteAreaId?.let(areas::get))
        }
    }

    fun provision(user: AppPrincipal, input: StationInput): ProvisionedStation {
        ensure(input.id.matches(STATION_ID)) { "Identifiant de borne invalide." }
        ensure(input.name.isNotBlank() && input.name.length <= 200) {
            "Le nom de la borne doit contenir entre 1 et 200 caractères."
        }
        input.siteAreaId?.let { requireArea(user.tenantId, it) }

        return db.atomic {
            val key = StationKey(user.tenantId, input.id)
            val existing = stations.findById(key).orElse(null)
            if (existing != null && !existing.deleted) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Cet identifiant de borne existe déjà.")
            }
            val secret = randomToken()
            val station =
                (existing ?: StationEntity(key, input.name, "")).apply {
                    name = input.name.trim()
                    secretHash = requireNotNull(passwords.encode(secret))
                    siteAreaId = input.siteAreaId
                    enabled = true
                    deleted = false
                }
            stations.save(station).also { db.flush() }
            db.audit(user.tenantId, user.id.toString(), "STATION_CREATE", input.id)
            ProvisionedStation(input.id, secret)
        }
    }

    fun update(user: AppPrincipal, stationId: String, input: StationUpdate): StationResponse {
        ensure(input.name.isNotBlank() && input.name.length <= 200) {
            "Le nom de la borne doit contenir entre 1 et 200 caractères."
        }
        input.siteAreaId?.let { requireArea(user.tenantId, it) }
        ensure((input.latitude == null) == (input.longitude == null)) {
            "Renseignez la latitude et la longitude ensemble."
        }
        ensure(input.latitude == null || input.latitude.abs() <= BigDecimal(90)) { "Latitude invalide." }
        ensure(input.longitude == null || input.longitude.abs() <= BigDecimal(180)) { "Longitude invalide." }
        ensure(input.maximumPowerKw == null || input.maximumPowerKw.signum() > 0) {
            "La puissance maximale doit être positive."
        }
        input.connectors?.forEach { connector ->
            ensure(connector.connectorId in 1..100) { "Numéro de connecteur invalide." }
            ensure(connector.maximumPowerKw == null || connector.maximumPowerKw.signum() > 0) {
                "La puissance d’un connecteur doit être positive."
            }
            ensure(connector.phases == null || connector.phases in setOf(1, 3)) {
                "Un connecteur est monophasé ou triphasé."
            }
            ensure(connector.type == null || connector.type.length <= 30) { "Type de connecteur invalide." }
        }

        db.atomic {
            val station =
                db.lock<StationEntity>(StationKey(user.tenantId, stationId))?.takeUnless { it.deleted }
                    ?: throw NotFound("Borne introuvable.")
            val disabled = station.enabled && !input.enabled
            station.name = input.name.trim()
            station.siteAreaId = input.siteAreaId
            station.enabled = input.enabled
            station.publicStation = input.public
            station.latitude = input.latitude
            station.longitude = input.longitude
            station.maximumPowerKw = input.maximumPowerKw
            station.excludeFromSmartCharging = input.excludeFromSmartCharging
            stations.save(station)
            input.connectors?.forEach { definition ->
                val key = ConnectorKey(user.tenantId, stationId, definition.connectorId)
                val connector = connectors.findById(key).orElse(null) ?: ConnectorEntity(key = key)
                connector.type = definition.type
                connector.maximumPowerKw = definition.maximumPowerKw
                connector.phases = definition.phases
                connectors.save(connector)
            }
            db.audit(user.tenantId, user.id.toString(), "STATION_UPDATE", stationId)
            if (disabled) gateway.disconnect(StationIdentity(user.tenantId, stationId))
        }
        return find(user.tenantId, stationId)
    }

    fun delete(user: AppPrincipal, stationId: String): MutationResponse {
        db.atomic {
            val station =
                db.lock<StationEntity>(StationKey(user.tenantId, stationId))?.takeUnless { it.deleted }
                    ?: throw NotFound("Borne introuvable.")
            if (transactions.existsByTenantIdAndStationIdAndStoppedAtIsNull(user.tenantId, stationId)) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Une session est en cours sur cette borne.")
            }
            station.deleted = true
            station.enabled = false
            station.publicStation = false
            station.secretHash = requireNotNull(passwords.encode(randomToken()))
            stations.save(station)
            db.audit(user.tenantId, user.id.toString(), "STATION_DELETE", stationId)
        }
        gateway.disconnect(StationIdentity(user.tenantId, stationId))
        return MutationResponse()
    }

    fun rotateSecret(user: AppPrincipal, stationId: String): ProvisionedStation {
        val secret = randomToken()
        db.atomic {
            val station =
                db.lock<StationEntity>(StationKey(user.tenantId, stationId))?.takeUnless { it.deleted }
                    ?: throw NotFound("Borne introuvable.")
            station.secretHash = requireNotNull(passwords.encode(secret))
            stations.save(station)
            db.audit(user.tenantId, user.id.toString(), "STATION_SECRET_ROTATE", stationId)
        }
        gateway.disconnect(StationIdentity(user.tenantId, stationId))
        return ProvisionedStation(stationId, secret)
    }

    fun inError(tenantId: UUID): List<StationInError> {
        val all = stations.findAllByKeyTenantIdAndDeletedFalse(tenantId)
        val plugs = connectors.findAllByKeyTenantId(tenantId).groupBy { it.key.stationId }
        val failing =
            all.mapNotNull { station ->
                val errors = buildList {
                    if (station.lastSeen == null) add("NEVER_CONNECTED")
                    else if (!station.connected) add("DISCONNECTED")
                    if (plugs[station.key.stationId].orEmpty().any { it.status == "Faulted" || it.errorCode != "NoError" }) {
                        add("CONNECTOR_FAULTED")
                    }
                    if (station.siteAreaId == null) add("NO_SITE_AREA")
                    if (station.firmwareStatus in setOf("DownloadFailed", "InstallationFailed")) add("FIRMWARE_FAILED")
                }
                if (errors.isEmpty()) null else station to errors
            }
        val views = views(tenantId, failing.map { it.first }).associateBy { it.id }
        return failing.map { (station, errors) -> StationInError(requireNotNull(views[station.key.stationId]), errors) }
    }

    fun events(tenantId: UUID, stationId: String, type: String?, offset: Int, limit: Int): PageResponse<StationEventResponse> {
        station(tenantId, stationId)
        val request = pageRequest(offset, limit, Sort.by(Sort.Direction.DESC, "createdAt", "id"))
        val page =
            if (type.isNullOrBlank()) stationEvents.findAllByTenantIdAndStationId(tenantId, stationId, request)
            else stationEvents.findAllByTenantIdAndStationIdAndType(tenantId, stationId, type, request)
        return PageResponse(page.content.map(StationEventResponse::from), page.totalElements)
    }

    fun parameters(tenantId: UUID, stationId: String): List<StationParameterResponse> {
        station(tenantId, stationId)
        return parameters.findAllByKeyTenantIdAndKeyStationIdOrderByKeyKey(tenantId, stationId)
            .map(StationParameterResponse::from)
    }

    fun refreshParameters(user: AppPrincipal, stationId: String): CommandReceipt {
        station(user.tenantId, stationId)
        val id = queue.command(user.tenantId, stationId, "GetConfiguration", db.json.createObjectNode())
        db.audit(user.tenantId, user.id.toString(), "STATION_PARAMETERS_REFRESH", stationId)
        return CommandReceipt(id)
    }

    fun changeParameter(user: AppPrincipal, stationId: String, input: ParameterInput): CommandReceipt {
        station(user.tenantId, stationId)
        ensure(input.key.matches(Regex("[A-Za-z0-9_.:-]{1,100}"))) { "Nom de paramètre OCPP invalide." }
        ensure(input.value.length <= 500) { "La valeur contient 500 caractères au plus." }
        val existing =
            parameters.findAllByKeyTenantIdAndKeyStationIdOrderByKeyKey(user.tenantId, stationId)
                .firstOrNull { it.key.key == input.key }
        ensure(existing?.readonly != true) { "Ce paramètre est en lecture seule." }
        val id =
            queue.command(
                user.tenantId,
                stationId,
                "ChangeConfiguration",
                db.json.valueToTree(mapOf("key" to input.key, "value" to input.value)),
            )
        db.audit(user.tenantId, user.id.toString(), "STATION_PARAMETER_CHANGE", "$stationId/${input.key}")
        return CommandReceipt(id)
    }

    fun autoCharge(user: AppPrincipal, stationId: String, input: AutoChargeSetting): CommandReceipt =
        db.atomic {
            val station =
                db.lock<StationEntity>(StationKey(user.tenantId, stationId))?.takeUnless { it.deleted }
                    ?: throw NotFound("Borne introuvable.")
            station.autoCharge = input.enabled
            stations.save(station)
            if (!input.enabled) {
                connectors.findAllByKeyTenantIdAndKeyStationIdOrderByKeyConnectorId(user.tenantId, stationId).forEach {
                    it.vid = null
                    connectors.save(it)
                }
            }
            val id = configuration.syncAutoCharge(station, force = true)
            db.audit(user.tenantId, user.id.toString(), "STATION_AUTOCHARGE", "$stationId/${input.enabled}")
            CommandReceipt(requireNotNull(id))
        }

    fun applyTemplate(user: AppPrincipal, stationId: String): TemplateApplication =
        db.atomic {
            val station =
                db.lock<StationEntity>(StationKey(user.tenantId, stationId))?.takeUnless { it.deleted }
                    ?: throw NotFound("Borne introuvable.")
            val result = configuration.apply(station)
            db.audit(user.tenantId, user.id.toString(), "STATION_TEMPLATE_APPLY", stationId)
            result
        }

    fun exportStations(tenantId: UUID) =
        Csv.download(
            "bornes",
            listOf("identifiant", "nom", "fabricant", "modele", "firmware", "connectee", "derniere_activite", "zone", "site", "puissance_kw", "publique", "connecteurs"),
            views(tenantId, stations.findAllByKeyTenantIdAndDeletedFalse(tenantId).sortedBy { it.name })
                .asSequence()
                .map { station ->
                    listOf(
                        station.id,
                        station.name,
                        station.vendor,
                        station.model,
                        station.firmwareVersion,
                        if (station.connected) "oui" else "non",
                        station.lastSeen,
                        station.siteAreaId,
                        station.siteId,
                        station.maximumPowerKw,
                        if (station.public) "oui" else "non",
                        station.connectors.orEmpty().joinToString(" ") { "${it.connectorId}:${it.status}" },
                    )
                },
        )

    fun exportParameters(tenantId: UUID, stationId: String?) =
        Csv.download(
            "parametres-ocpp",
            listOf("borne", "parametre", "valeur", "lecture_seule", "mise_a_jour"),
            (if (stationId.isNullOrBlank()) parameters.findAllByKeyTenantIdOrderByKeyStationIdAscKeyKeyAsc(tenantId)
            else {
                station(tenantId, stationId)
                parameters.findAllByKeyTenantIdAndKeyStationIdOrderByKeyKey(tenantId, stationId)
            })
                .asSequence()
                .map { listOf(it.key.stationId, it.key.key, it.value, if (it.readonly) "oui" else "non", it.updatedAt) },
        )

    private fun requireArea(tenant: UUID, area: UUID) {
        ensure(organization.siteArea(tenant, area) != null) { "La zone sélectionnée n’existe pas dans cet espace." }
    }

    fun activeTransaction(tenantId: UUID, stationId: String, connectorId: Int): TransactionEntity? =
        transactions.findFirstByTenantIdAndStationIdAndConnectorIdAndStoppedAtIsNull(tenantId, stationId, connectorId)
}
