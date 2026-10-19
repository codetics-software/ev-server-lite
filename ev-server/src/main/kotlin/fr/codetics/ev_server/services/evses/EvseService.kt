package fr.codetics.ev_server.services.evses

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.models.billing.PaymentState
import fr.codetics.ev_server.models.entities.ConnectorKey
import fr.codetics.ev_server.models.entities.PublicEvseEntity
import fr.codetics.ev_server.models.entities.StationKey
import fr.codetics.ev_server.models.evses.EvseInput
import fr.codetics.ev_server.models.evses.EvseResponse
import fr.codetics.ev_server.models.evses.PortalEvseResponse
import fr.codetics.ev_server.repositories.ChargingPaymentRepository
import fr.codetics.ev_server.repositories.ConnectorRepository
import fr.codetics.ev_server.repositories.PublicEvseRepository
import fr.codetics.ev_server.repositories.StationRepository
import fr.codetics.ev_server.repositories.TenantRepository
import fr.codetics.ev_server.repositories.TransactionRepository
import fr.codetics.ev_server.repositories.WorkspacePaymentSettingsRepository
import fr.codetics.ev_server.security.SecretVault
import fr.codetics.ev_server.services.billing.TariffService
import fr.codetics.ev_server.stripe.PaymentGateway
import java.net.URI
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class EvseService(
    private val evses: PublicEvseRepository,
    private val stations: StationRepository,
    private val connectors: ConnectorRepository,
    private val tenants: TenantRepository,
    private val settings: WorkspacePaymentSettingsRepository,
    private val payments: ChargingPaymentRepository,
    private val tariffs: TariffService,
    private val gateway: PaymentGateway,
    private val db: Database,
    private val transactions: TransactionRepository,
    private val vault: SecretVault,
    @Value("\${app.public-url}") private val publicUrl: String,
) {
    fun list(tenantId: UUID) = evses.findAllByTenantIdOrderByName(tenantId).map { response(it) }

    fun find(tenantId: UUID, id: UUID): PublicEvseEntity =
        evses.findByTenantIdAndId(tenantId, id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Point de charge introuvable.")

    fun save(user: AppPrincipal, input: EvseInput, id: UUID? = null): EvseResponse =
        db.atomic {
            require(input.name.isNotBlank() && input.name.length <= 200) {
                "Renseignez le nom du point de charge."
            }
            require(input.connectorId > 0) { "Sélectionnez un connecteur physique." }
            val station =
                stations.findForUpdate(StationKey(user.tenantId, input.stationId))
                    ?: throw ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Borne introuvable dans cet espace.",
                    )
            require(station.enabled) { "La borne est désactivée." }
            require(
                connectors.existsById(
                    ConnectorKey(user.tenantId, input.stationId, input.connectorId)
                )
            ) {
                "Le connecteur doit avoir été déclaré par la borne."
            }
            tariffs.find(user.tenantId, input.pricingDefinitionId)
            val evse =
                if (id == null) PublicEvseEntity(tenantId = user.tenantId)
                else find(user.tenantId, id)
            val duplicate =
                evses.findByTenantIdAndStationIdAndConnectorId(
                    user.tenantId,
                    input.stationId,
                    input.connectorId,
                )
            require(duplicate == null || duplicate.id == evse.id) {
                "Ce connecteur possède déjà un point de charge public."
            }
            if (payments.existsByEvseIdAndStateIn(evse.id, PaymentState.WORKABLE)) {
                require(
                    evse.stationId == input.stationId && evse.connectorId == input.connectorId
                ) {
                    "Un point de charge avec une session en cours ne peut pas changer de connecteur."
                }
            }
            evse.name = input.name.trim()
            evse.stationId = input.stationId
            evse.connectorId = input.connectorId
            evse.pricingDefinitionId = input.pricingDefinitionId
            evse.published = input.published
            evses.saveAndFlush(evse)
            db.audit(user.tenantId, user.id.toString(), "EVSE_SAVE", evse.id.toString())
            response(evse)
        }

    fun portal(workspace: String, id: UUID): PortalEvseResponse {
        val tenant =
            tenants.findBySlugAndActiveTrue(workspace)
                ?: throw ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Point de charge introuvable.",
                )
        val evse = find(tenant.id, id)
        if (!evse.published)
            throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Ce point de charge n’est pas publié.",
            )
        val station = stations.findById(StationKey(tenant.id, evse.stationId)).orElseThrow()
        val connector =
            connectors
                .findById(ConnectorKey(tenant.id, evse.stationId, evse.connectorId))
                .orElseThrow()
        val paymentSettings = settings.findById(tenant.id).orElse(null)
        val available =
            gateway.configured &&
                vault.configured &&
                paymentSettings?.enabled == true &&
                paymentSettings.stripeAccountId != null &&
                station.connected &&
                station.enabled &&
                connector.status in setOf("Available", "Preparing") &&
                !payments.existsByEvseIdAndStateIn(evse.id, PaymentState.OPEN) &&
                !transactions.existsByTenantIdAndStationIdAndConnectorIdAndStoppedAtIsNull(
                    tenant.id,
                    evse.stationId,
                    evse.connectorId,
                )
        return PortalEvseResponse(
            evse.id,
            tenant.name,
            tenant.slug,
            evse.name,
            station.key.stationId,
            evse.connectorId,
            station.connected,
            connector.status,
            available,
            tariffs.find(tenant.id, evse.pricingDefinitionId),
        )
    }

    fun portalUrl(tenantId: UUID, id: UUID): String {
        val tenant = tenants.findById(tenantId).orElseThrow()
        val base = URI(publicUrl)
        require(
            base.scheme == "https" ||
                (base.scheme == "http" && base.host in setOf("localhost", "127.0.0.1"))
        ) {
            "L’URL publique doit utiliser HTTPS."
        }
        return "${publicUrl.trimEnd('/')}/charge/${tenant.slug}/$id"
    }

    private fun response(evse: PublicEvseEntity) =
        EvseResponse(
            evse.id,
            evse.name,
            evse.stationId,
            evse.connectorId,
            evse.pricingDefinitionId,
            evse.published,
            portalUrl(evse.tenantId, evse.id),
        )
}
