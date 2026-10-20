package fr.codetics.ev_server.services.assets

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.InvalidRequest
import fr.codetics.ev_server.core.NotFound
import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.core.randomToken
import fr.codetics.ev_server.core.sha256
import fr.codetics.ev_server.models.api.MutationResponse
import fr.codetics.ev_server.models.api.RecordResponse
import fr.codetics.ev_server.models.entities.AssetConnectionEntity
import fr.codetics.ev_server.models.entities.AssetConsumptionEntity
import fr.codetics.ev_server.models.organization.AssetData
import fr.codetics.ev_server.repositories.AssetConnectionRepository
import fr.codetics.ev_server.repositories.AssetConsumptionRepository
import fr.codetics.ev_server.repositories.TenantRepository
import fr.codetics.ev_server.security.SecretVault
import fr.codetics.ev_server.services.organization.Entry
import fr.codetics.ev_server.services.organization.Organization
import fr.codetics.ev_server.services.workspaces.WorkspaceService
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

data class AssetConsumptionInput(
    val at: Instant,
    val powerKw: BigDecimal,
    val energyKwh: BigDecimal? = null,
    val soc: Int? = null,
)

data class AssetSecretsInput(val password: String? = null, val clientSecret: String? = null, val apiKey: String? = null)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class AssetConsumptionResponse(val at: Instant, val powerKw: BigDecimal, val energyKwh: BigDecimal?, val soc: Int?)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class AssetInError(val asset: RecordResponse, val errors: List<String>)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class ConnectionCheck(val ok: Boolean, val message: String)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class AssetConnectionStatus(
    val configured: Boolean,
    val hasPassword: Boolean,
    val hasClientSecret: Boolean,
    val hasApiKey: Boolean,
    val hasPushToken: Boolean,
    val lastSuccessAt: Instant?,
    val lastAttemptAt: Instant?,
    val lastError: String?,
)

@Service
class AssetService(
    private val db: Database,
    private val organization: Organization,
    private val consumptions: AssetConsumptionRepository,
    private val connections: AssetConnectionRepository,
    private val tenants: TenantRepository,
    private val vault: SecretVault,
    private val workspaces: WorkspaceService,
    connectors: List<AssetConnector>,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val byType = connectors.associateBy { it.type }

    private fun asset(tenant: UUID, id: UUID): Entry<AssetData> =
        organization.asset(tenant, id) ?: throw NotFound("Équipement introuvable.")

    fun list(user: AppPrincipal, id: UUID, from: Instant?, to: Instant?): List<AssetConsumptionResponse> {
        asset(user.tenantId, id)
        val end = to ?: Instant.now()
        val start = from ?: end.minus(Duration.ofDays(1))
        ensure(start.isBefore(end) && Duration.between(start, end) <= Duration.ofDays(31)) {
            "Choisissez une période de 31 jours au plus."
        }
        return consumptions.findAllByTenantIdAndAssetIdAndSampledAtBetweenOrderBySampledAt(user.tenantId, id, start, end).map(::response)
    }

    fun push(user: AppPrincipal, id: UUID, input: List<AssetConsumptionInput>): MutationResponse {
        asset(user.tenantId, id)
        store(user.tenantId, id, input)
        return MutationResponse()
    }

    fun pushWithToken(authorization: String?, id: UUID, input: List<AssetConsumptionInput>): MutationResponse {
        val raw = authorization?.takeIf { it.startsWith("Asset ") && it.length < 200 }?.removePrefix("Asset ")
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        val connection =
            connections.findByPushTokenHash(sha256(raw))?.takeIf { it.assetId == id }
                ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        val tenant = tenants.findById(connection.tenantId).orElse(null)
        if (tenant == null || !tenant.active) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        }
        asset(connection.tenantId, id)
        store(connection.tenantId, id, input)
        return MutationResponse()
    }

    private fun store(tenant: UUID, id: UUID, input: List<AssetConsumptionInput>) {
        ensure(input.size in 1..1000) { "Envoyez entre 1 et 1 000 mesures." }
        val horizon = Instant.now().plus(Duration.ofMinutes(5))
        input.forEach { point ->
            ensure(!point.at.isAfter(horizon)) { "Une mesure est datée dans le futur." }
            ensure(point.powerKw.abs() <= BigDecimal(100000)) { "Puissance invalide." }
            ensure(point.energyKwh == null || point.energyKwh.signum() >= 0) { "Énergie invalide." }
            ensure(point.soc == null || point.soc in 0..100) { "État de charge invalide." }
        }
        db.atomic {
            consumptions.saveAll(
                input.map {
                    AssetConsumptionEntity(
                        tenantId = tenant,
                        assetId = id,
                        sampledAt = it.at,
                        powerW = it.powerKw.multiply(KW),
                        energyWh = it.energyKwh?.multiply(KW),
                        soc = it.soc,
                    )
                }
            )
            success(tenant, id)
        }
    }

    fun status(user: AppPrincipal, id: UUID): AssetConnectionStatus {
        asset(user.tenantId, id)
        val connection = connections.findByTenantIdAndAssetId(user.tenantId, id)
        val secrets = connection?.let(::secrets) ?: AssetSecrets()
        return AssetConnectionStatus(
            connection != null,
            secrets.password != null,
            secrets.clientSecret != null,
            secrets.apiKey != null,
            connection?.pushTokenHash != null,
            connection?.lastSuccessAt,
            connection?.lastAttemptAt,
            connection?.lastError,
        )
    }

    fun updateSecrets(user: AppPrincipal, id: UUID, input: AssetSecretsInput): AssetConnectionStatus {
        asset(user.tenantId, id)
        listOfNotNull(input.password, input.clientSecret, input.apiKey).forEach {
            ensure(it.length <= 1000) { "Le secret est trop long." }
        }
        db.atomic {
            val connection = connections.findByTenantIdAndAssetId(user.tenantId, id) ?: AssetConnectionEntity(id, user.tenantId)
            val previous = secrets(connection)
            val merged =
                AssetSecrets(
                    input.password?.ifBlank { null } ?: previous.password,
                    input.clientSecret?.ifBlank { null } ?: previous.clientSecret,
                    input.apiKey?.ifBlank { null } ?: previous.apiKey,
                )
            connection.secretsEncrypted = vault.encrypt(db.encode(merged))
            connections.save(connection)
            db.audit(user.tenantId, user.id.toString(), "ASSET_CONNECTION_SECRETS", id.toString())
        }
        return status(user, id)
    }

    fun pushToken(user: AppPrincipal, id: UUID): Map<String, String> {
        asset(user.tenantId, id)
        val raw = randomToken()
        db.atomic {
            val connection = connections.findByTenantIdAndAssetId(user.tenantId, id) ?: AssetConnectionEntity(id, user.tenantId)
            connection.pushTokenHash = sha256(raw)
            connections.save(connection)
            db.audit(user.tenantId, user.id.toString(), "ASSET_PUSH_TOKEN", id.toString())
        }
        return mapOf("token" to raw, "url" to "/api/v1/public/assets/$id/consumptions")
    }

    fun check(user: AppPrincipal, id: UUID): ConnectionCheck {
        val asset = asset(user.tenantId, id)
        val connection = asset.data.connection ?: return ConnectionCheck(false, "Aucune connexion configurée.")
        val connector = byType[connection.type] ?: return ConnectionCheck(connection.type == "PUSH", "Connexion par envoi des mesures.")
        return try {
            connector.check(connection, secrets(user.tenantId, id))
            ConnectionCheck(true, "Connexion réussie.")
        } catch (error: AssetConnectionError) {
            ConnectionCheck(false, error.message ?: "Connexion impossible.")
        } catch (error: InvalidRequest) {
            ConnectionCheck(false, error.message)
        }
    }

    fun refresh(user: AppPrincipal, id: UUID): List<AssetConsumptionResponse> {
        val asset = asset(user.tenantId, id)
        val samples = poll(user.tenantId, asset, manual = true)
        return samples.map(::response)
    }

    @Scheduled(initialDelay = 45000, fixedDelay = 60000)
    fun scheduled() {
        tenants.findAll().filter { it.active && workspaces.components(it).assets }.forEach { tenant ->
            organization.assets(tenant.id).filter { (it.data.connection?.type ?: "NONE") in byType }.forEach { asset ->
                val connection = connections.findByTenantIdAndAssetId(tenant.id, asset.id)
                val refresh = Duration.ofSeconds(requireNotNull(asset.data.connection).refreshSeconds.toLong())
                if (connection?.lastAttemptAt?.isAfter(Instant.now().minus(refresh)) == true) return@forEach
                try {
                    poll(tenant.id, asset, manual = false)
                } catch (error: RuntimeException) {
                    log.debug("Relevé de l’équipement {} impossible", asset.id, error)
                }
            }
        }
    }

    private fun poll(tenant: UUID, asset: Entry<AssetData>, manual: Boolean): List<AssetConsumptionEntity> {
        val connection = asset.data.connection ?: throw InvalidRequest("Aucune connexion configurée.")
        val connector = byType[connection.type] ?: throw InvalidRequest("Cette connexion ne permet pas de relever les mesures.")
        val since = consumptions.findFirstByTenantIdAndAssetIdOrderBySampledAtDesc(tenant, asset.id)?.sampledAt
        return try {
            val samples =
                connector.fetch(asset.data, connection, secrets(tenant, asset.id), since, manual)
                    .filter { since == null || it.at.isAfter(since) }
                    .take(5000)
            db.atomic {
                val saved =
                    consumptions.saveAll(
                        samples.map {
                            AssetConsumptionEntity(
                                tenantId = tenant,
                                assetId = asset.id,
                                sampledAt = it.at,
                                powerW = it.powerW,
                                energyWh = it.energyWh,
                                soc = it.soc,
                            )
                        }
                    )
                success(tenant, asset.id)
                saved
            }
        } catch (error: RuntimeException) {
            db.atomic {
                val entity = connections.findByTenantIdAndAssetId(tenant, asset.id) ?: AssetConnectionEntity(asset.id, tenant)
                entity.lastAttemptAt = Instant.now()
                entity.lastError = (error.message ?: "Relevé impossible.").take(500)
                connections.save(entity)
            }
            throw error
        }
    }

    private fun success(tenant: UUID, id: UUID) {
        val entity = connections.findByTenantIdAndAssetId(tenant, id) ?: AssetConnectionEntity(id, tenant)
        entity.lastAttemptAt = Instant.now()
        entity.lastSuccessAt = Instant.now()
        entity.lastError = null
        connections.save(entity)
    }

    fun inError(user: AppPrincipal): List<AssetInError> {
        val stale = Instant.now().minus(Duration.ofHours(1))
        return organization.assets(user.tenantId).mapNotNull { asset ->
            val connection = connections.findByTenantIdAndAssetId(user.tenantId, asset.id)
            val type = asset.data.connection?.type ?: "NONE"
            val errors = buildList {
                if (asset.data.siteAreaId == null) add("NO_SITE_AREA")
                if (connection?.lastError != null) add("CONNECTION_FAILED")
                if (type != "NONE") {
                    val latest = consumptions.findFirstByTenantIdAndAssetIdOrderBySampledAtDesc(user.tenantId, asset.id)
                    if (latest == null || latest.sampledAt.isBefore(stale)) add("NO_RECENT_DATA")
                }
            }
            if (errors.isEmpty()) null
            else AssetInError(RecordResponse(asset.id, asset.name, db.json.valueToTree(asset.data), asset.revision, asset.updatedAt), errors)
        }
    }

    private fun secrets(tenant: UUID, id: UUID) =
        connections.findByTenantIdAndAssetId(tenant, id)?.let(::secrets) ?: AssetSecrets()

    private fun secrets(connection: AssetConnectionEntity): AssetSecrets =
        connection.secretsEncrypted?.let { encrypted ->
            runCatching { db.json.readValue(vault.decrypt(encrypted), AssetSecrets::class.java) }.getOrNull()
        } ?: AssetSecrets()

    private fun response(row: AssetConsumptionEntity) =
        AssetConsumptionResponse(
            row.sampledAt,
            row.powerW.divide(KW, 3, RoundingMode.HALF_UP),
            row.energyWh?.divide(KW, 3, RoundingMode.HALF_UP),
            row.soc,
        )

    private companion object {
        val KW = BigDecimal(1000)
    }
}
