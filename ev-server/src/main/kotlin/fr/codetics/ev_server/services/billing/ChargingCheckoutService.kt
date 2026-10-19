package fr.codetics.ev_server.services.billing

import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.sha256
import fr.codetics.ev_server.models.billing.CheckoutDetails
import fr.codetics.ev_server.models.billing.CheckoutRequest
import fr.codetics.ev_server.models.billing.CheckoutResponse
import fr.codetics.ev_server.models.billing.PaymentState
import fr.codetics.ev_server.models.entities.ChargingPaymentEntity
import fr.codetics.ev_server.models.entities.ChargingTokenEntity
import fr.codetics.ev_server.models.entities.ChargingTokenKey
import fr.codetics.ev_server.models.entities.StationKey
import fr.codetics.ev_server.repositories.ChargingPaymentRepository
import fr.codetics.ev_server.repositories.ChargingTokenRepository
import fr.codetics.ev_server.repositories.StationRepository
import fr.codetics.ev_server.repositories.TenantRepository
import fr.codetics.ev_server.repositories.WorkspacePaymentSettingsRepository
import fr.codetics.ev_server.security.PortalAccessToken
import fr.codetics.ev_server.security.SecretVault
import fr.codetics.ev_server.services.evses.EvseService
import fr.codetics.ev_server.stripe.PaymentGateway
import java.time.Instant
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class ChargingCheckoutService(
    private val payments: ChargingPaymentRepository,
    private val tokens: ChargingTokenRepository,
    private val stations: StationRepository,
    private val tenants: TenantRepository,
    private val settings: WorkspacePaymentSettingsRepository,
    private val evses: EvseService,
    private val gateway: PaymentGateway,
    private val vault: SecretVault,
    private val db: Database,
) {
    fun create(workspace: String, evseId: UUID, request: CheckoutRequest): CheckoutResponse {
        PortalAccessToken.validateFormat(request.accessToken)
        require(request.creditMinor in 500..10000) {
            "Le crédit doit être compris entre 5 € et 100 €."
        }
        if (!gateway.configured || !vault.configured)
            throw ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Le paiement n’est pas configuré.",
            )
        val tenant =
            tenants.findBySlugAndActiveTrue(workspace)
                ?: throw ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Point de charge introuvable.",
                )
        val existing = payments.findByTenantIdAndRequestId(tenant.id, request.requestId)
        if (existing != null) {
            PortalAccessToken.verify(existing.accessTokenHash, request.accessToken)
            require(existing.evseId == evseId && existing.prepaidMinor == request.creditMinor) {
                "Cette demande de paiement a déjà été utilisée."
            }
            return initialize(existing.id)
        }
        val paymentSettings = settings.findById(tenant.id).orElse(null)
        val accountId = paymentSettings?.stripeAccountId
        if (
            paymentSettings?.enabled != true ||
                accountId == null ||
                !gateway.accountReady(accountId)
        ) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "L’exploitant n’a pas encore activé les paiements.",
            )
        }

        val orderId =
            db.atomic {
                val evse = evses.find(tenant.id, evseId)
                stations.findForUpdate(StationKey(tenant.id, evse.stationId))
                    ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Borne introuvable.")
                val raced = payments.findByTenantIdAndRequestId(tenant.id, request.requestId)
                if (raced != null) {
                    PortalAccessToken.verify(raced.accessTokenHash, request.accessToken)
                    require(raced.evseId == evseId && raced.prepaidMinor == request.creditMinor) {
                        "Cette demande de paiement a déjà été utilisée."
                    }
                    return@atomic raced.id
                }
                val portal = evses.portal(workspace, evseId)
                if (!portal.available)
                    throw ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Ce point de charge n’est pas disponible pour une nouvelle session.",
                    )
                val order =
                    ChargingPaymentEntity(
                        tenantId = tenant.id,
                        evseId = evseId,
                        stationId = evse.stationId,
                        connectorId = evse.connectorId,
                        requestId = request.requestId,
                        accessTokenHash = sha256(request.accessToken),
                        accessTokenEncrypted = vault.encrypt(request.accessToken),
                        portalUrl = evses.portalUrl(tenant.id, evseId),
                        description = "Crédit de recharge — ${evse.name}",
                        stripeAccountId = accountId,
                        prepaidMinor = request.creditMinor,
                        currency = portal.tariff.currency,
                        energyPrice = portal.tariff.energyPrice,
                        timePrice = portal.tariff.timePrice,
                        flatPrice = portal.tariff.flatPrice,
                        tokenUid = "Q" + UUID.randomUUID().toString().replace("-", "").take(19),
                    )
                payments.saveAndFlush(order)
                tokens.save(
                    ChargingTokenEntity(
                        ChargingTokenKey(tenant.id, order.tokenUid),
                        enabled = false,
                        expiresAt = order.expiresAt,
                    )
                )
                order.id
            }
        return initialize(orderId)
    }

    fun initialize(orderId: UUID): CheckoutResponse {
        val order = payments.findById(orderId).orElseThrow()
        order.checkoutUrl?.let {
            return CheckoutResponse(order.id, it)
        }
        if (
            order.state != PaymentState.PENDING_PAYMENT || order.expiresAt.isBefore(Instant.now())
        ) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Cette demande de paiement a expiré.",
            )
        }
        val accessToken = vault.decrypt(order.accessTokenEncrypted)
        val details =
            CheckoutDetails(
                order.id,
                order.stripeAccountId,
                order.prepaidMinor,
                order.currency,
                order.description,
                "${order.portalUrl}/sessions/${order.id}#cle=$accessToken",
                order.portalUrl,
                order.expiresAt,
            )
        val checkout = gateway.createCheckout(details)
        return db.atomic {
            val saved = requireNotNull(payments.findForUpdate(orderId))
            saved.stripeCheckoutId = checkout.id
            saved.checkoutUrl = checkout.url
            saved.updatedAt = Instant.now()
            payments.save(saved)
            CheckoutResponse(saved.id, checkout.url)
        }
    }
}
