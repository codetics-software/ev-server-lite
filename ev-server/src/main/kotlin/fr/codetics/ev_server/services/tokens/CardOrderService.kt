// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.ev_server.services.tokens

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.AppProperties
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.NotFound
import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.core.lock
import fr.codetics.ev_server.core.pageRequest
import fr.codetics.ev_server.models.api.PageResponse
import fr.codetics.ev_server.models.billing.CheckoutDetails
import fr.codetics.ev_server.models.entities.CardOrderEntity
import fr.codetics.ev_server.models.entities.ChargingTokenEntity
import fr.codetics.ev_server.models.entities.ChargingTokenKey
import fr.codetics.ev_server.models.entities.StripeEventEntity
import fr.codetics.ev_server.models.entities.WorkspacePaymentSettings
import fr.codetics.ev_server.models.notifications.NotificationType
import fr.codetics.ev_server.models.tokens.CardActivationInput
import fr.codetics.ev_server.models.tokens.CardAssignmentInput
import fr.codetics.ev_server.models.tokens.CardCheckoutResponse
import fr.codetics.ev_server.models.tokens.CardOfferResponse
import fr.codetics.ev_server.models.tokens.CardOrderInput
import fr.codetics.ev_server.models.tokens.CardOrderResponse
import fr.codetics.ev_server.models.tokens.CardOrderState
import fr.codetics.ev_server.models.tokens.CardSaleInput
import fr.codetics.ev_server.models.tokens.ChargingTokenResponse
import fr.codetics.ev_server.ocpp.VehicleIds
import fr.codetics.ev_server.repositories.CardOrderRepository
import fr.codetics.ev_server.repositories.ChargingTokenRepository
import fr.codetics.ev_server.repositories.StripeEventRepository
import fr.codetics.ev_server.repositories.UserRepository
import fr.codetics.ev_server.repositories.WorkspacePaymentSettingsRepository
import fr.codetics.ev_server.security.RateLimiter
import fr.codetics.ev_server.services.notifications.NotificationService
import fr.codetics.ev_server.stripe.PaymentGateway
import fr.codetics.ev_server.stripe.SignedPaymentEvent
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class CardOrderService(
    private val db: Database,
    private val orders: CardOrderRepository,
    private val tokens: ChargingTokenRepository,
    private val settings: WorkspacePaymentSettingsRepository,
    private val users: UserRepository,
    private val events: StripeEventRepository,
    private val gateway: PaymentGateway,
    private val notifications: NotificationService,
    private val limiter: RateLimiter,
    private val properties: AppProperties,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private fun settings(tenant: UUID): WorkspacePaymentSettings? = settings.findById(tenant).orElse(null)

    fun offer(user: AppPrincipal): CardOfferResponse {
        val current = settings(user.tenantId)
        val available =
            gateway.configured &&
                current?.cardSaleEnabled == true &&
                current.cardPriceMinor != null &&
                current.stripeAccountId != null
        return CardOfferResponse(
            available,
            current?.cardPriceMinor,
            current?.currency ?: "EUR",
            current?.cardDescription,
            current?.cardShipping ?: true,
        )
    }

    fun updateSale(user: AppPrincipal, input: CardSaleInput): CardOfferResponse {
        ensure(input.priceMinor in 100..100000) { "Le prix de la carte doit être compris entre 1 € et 1 000 €." }
        ensure(input.description == null || input.description.length <= 500) {
            "La description contient 500 caractères au plus."
        }
        db.atomic {
            val current = db.lock<WorkspacePaymentSettings>(user.tenantId) ?: WorkspacePaymentSettings(tenantId = user.tenantId)
            ensure(!input.enabled || current.stripeAccountId != null) { "Connectez d’abord le compte Stripe de l’espace." }
            current.cardSaleEnabled = input.enabled
            current.cardPriceMinor = input.priceMinor
            current.cardDescription = input.description?.trim()?.ifBlank { null }
            current.cardShipping = input.shipping
            current.updatedAt = Instant.now(clock)
            settings.save(current)
            db.audit(user.tenantId, user.id.toString(), "CARD_SALE_SETTINGS", input.enabled.toString())
        }
        return offer(user)
    }

    fun create(user: AppPrincipal, input: CardOrderInput): CardCheckoutResponse {
        limiter.check("card-order:${user.id}", 10)
        val current = settings(user.tenantId)
        val price = current?.cardPriceMinor
        val account = current?.stripeAccountId
        if (!gateway.configured || current?.cardSaleEnabled != true || price == null || account == null || !gateway.accountReady(account)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "La vente de cartes n’est pas disponible dans cet espace.")
        }
        if (current.cardShipping) {
            ensure(!input.recipientName.isNullOrBlank() && !input.address.isNullOrBlank() && !input.postalCode.isNullOrBlank() && !input.city.isNullOrBlank()) {
                "Renseignez l’adresse de livraison complète."
            }
        }
        listOf(input.recipientName, input.address, input.city).forEach { value ->
            ensure(value == null || value.length <= 200) { "L’adresse de livraison est trop longue." }
        }
        ensure(input.postalCode == null || input.postalCode.length <= 20) { "Le code postal est trop long." }
        val orderId =
            db.atomic {
                val existing = orders.findByTenantIdAndRequestId(user.tenantId, input.requestId)
                if (existing != null) {
                    ensure(existing.userId == user.id) { "Cette demande de commande a déjà été utilisée." }
                    return@atomic existing.id
                }
                val now = Instant.now(clock)
                val order =
                    CardOrderEntity(
                        tenantId = user.tenantId,
                        userId = user.id,
                        requestId = input.requestId,
                        amountMinor = price,
                        currency = current.currency,
                        recipientName = input.recipientName?.trim().takeIf { current.cardShipping },
                        address = input.address?.trim().takeIf { current.cardShipping },
                        postalCode = input.postalCode?.trim().takeIf { current.cardShipping },
                        city = input.city?.trim().takeIf { current.cardShipping },
                        stripeAccountId = account,
                        expiresAt = now.plus(CHECKOUT_WINDOW),
                        createdAt = now,
                        updatedAt = now,
                    )
                orders.save(order).also { db.flush() }
                db.audit(user.tenantId, user.id.toString(), "CARD_ORDER_CREATE", order.id.toString())
                order.id
            }
        return initialize(orderId)
    }

    private fun initialize(id: UUID): CardCheckoutResponse {
        val order = orders.findById(id).orElseThrow()
        order.checkoutUrl?.let {
            return CardCheckoutResponse(order.id, it)
        }
        if (order.state != CardOrderState.PENDING_PAYMENT || order.expiresAt.isBefore(Instant.now(clock))) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Cette commande a expiré.")
        }
        val returnUrl = "${properties.publicUrl.trimEnd('/')}/card-orders"
        val checkout =
            gateway.createCardCheckout(
                CheckoutDetails(
                    order.id,
                    order.stripeAccountId,
                    order.amountMinor,
                    order.currency,
                    "Carte de recharge",
                    returnUrl,
                    returnUrl,
                    order.expiresAt,
                )
            )
        return db.atomic {
            val saved = requireNotNull(db.lock<CardOrderEntity>(id))
            saved.stripeCheckoutId = checkout.id
            saved.checkoutUrl = checkout.url
            saved.updatedAt = Instant.now(clock)
            orders.save(saved)
            CardCheckoutResponse(saved.id, checkout.url)
        }
    }

    fun own(user: AppPrincipal): List<CardOrderResponse> =
        orders.findAllByTenantIdAndUserIdOrderByCreatedAtDesc(user.tenantId, user.id).map(CardOrderResponse::forDriver)

    fun list(user: AppPrincipal, state: String?, offset: Int, limit: Int): PageResponse<CardOrderResponse> {
        val request = pageRequest(offset, limit, Sort.by(Sort.Direction.DESC, "createdAt"))
        val page =
            if (state.isNullOrBlank()) orders.findAllByTenantId(user.tenantId, request)
            else orders.findAllByTenantIdAndState(user.tenantId, state, request)
        val people = users.findAllByTenantIdAndIdIn(user.tenantId, page.content.map { it.userId }.toSet()).associateBy { it.id }
        val cards =
            page.content.mapNotNull { order -> order.tokenUid?.let { uid -> tokens.findById(ChargingTokenKey(user.tenantId, uid)).orElse(null) } }
                .associateBy { it.key.uid }
        return PageResponse(
            page.content.map { order ->
                CardOrderResponse.forDriver(order).copy(
                    userName = people[order.userId]?.displayName,
                    userEmail = people[order.userId]?.email,
                    tokenUid = order.tokenUid,
                    visualId = order.tokenUid?.let { cards[it]?.visualId },
                )
            },
            page.totalElements,
        )
    }

    fun assign(user: AppPrincipal, id: UUID, input: CardAssignmentInput): CardOrderResponse {
        val uid = input.uid.trim()
        ensure(uid.matches(Regex("[A-Za-z0-9_-]{1,20}"))) { "L’identifiant de la carte doit contenir au plus 20 caractères." }
        ensure(!VehicleIds.isVid(uid)) { "Un identifiant de véhicule ne peut pas être associé à une commande." }
        val visual = input.visualId?.trim()?.ifBlank { null }
        ensure(visual == null || visual.length <= 100) { "L’identifiant visible contient 100 caractères au plus." }
        val owner =
            db.atomic {
                val order = db.lock<CardOrderEntity>(id)?.takeIf { it.tenantId == user.tenantId } ?: throw NotFound("Commande introuvable.")
                ensure(order.state == CardOrderState.PAID || order.state == CardOrderState.SHIPPED) {
                    "Seule une commande payée peut recevoir une carte."
                }
                val key = ChargingTokenKey(user.tenantId, uid)
                val token = tokens.findById(key).orElse(null)
                if (token != null && token.userId != null) {
                    throw ResponseStatusException(HttpStatus.CONFLICT, "Cette carte est déjà attribuée à un utilisateur.")
                }
                val card = token ?: ChargingTokenEntity(key)
                card.enabled = false
                card.defaultToken = false
                card.visualId = visual ?: card.visualId
                card.description = card.description ?: "Carte commandée"
                tokens.save(card)
                order.tokenUid = uid
                order.state = CardOrderState.SHIPPED
                order.shippedAt = order.shippedAt ?: Instant.now(clock)
                order.updatedAt = Instant.now(clock)
                orders.save(order)
                db.audit(user.tenantId, user.id.toString(), "CARD_ORDER_ASSIGN", "$id/$uid")
                users.findByTenantIdAndId(user.tenantId, order.userId)
            }
        owner?.let {
            safely {
                notifications.notify(
                    it,
                    NotificationType.CARD_READY,
                    "Votre carte de recharge est prête",
                    "Dès réception, activez-la dans l’application en la scannant ou en saisissant son numéro.",
                    dedupeKey = "card-ready:$id",
                )
            }
        }
        return list(user, null, 0, 500).items.first { it.id == id }
    }

    fun activate(user: AppPrincipal, input: CardActivationInput): ChargingTokenResponse {
        val number = normalize(input.number)
        ensure(number.length in 1..100) { "Renseignez le numéro de la carte." }
        limiter.check("card-activation:${user.id}", 10)
        return db.atomic {
            val candidates = orders.findAllByTenantIdAndUserIdOrderByCreatedAtDesc(user.tenantId, user.id)
            val shipped = candidates.filter { it.state == CardOrderState.SHIPPED }
            val match =
                shipped.firstNotNullOfOrNull { order ->
                    val token = order.tokenUid?.let { tokens.findById(ChargingTokenKey(user.tenantId, it)).orElse(null) }
                    token?.takeIf { matches(it, number) }?.let { order to it }
                }
            if (match == null) {
                val message =
                    if (candidates.any { it.state == CardOrderState.PAID })
                        "Votre carte n’est pas encore prête. Vous serez prévenu dès que l’exploitant l’aura préparée."
                    else "Aucune carte commandée sur votre compte ne correspond à ce numéro."
                throw NotFound(message)
            }
            val (found, token) = match
            val order = requireNotNull(db.lock<CardOrderEntity>(found.id))
            ensure(order.state == CardOrderState.SHIPPED) { "Cette carte est déjà activée." }
            if (token.userId != null && token.userId != user.id) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Cette carte est déjà attribuée à un autre utilisateur.")
            }
            val hasDefault = tokens.findAllByKeyTenantIdAndUserId(user.tenantId, user.id).any { it.defaultToken }
            token.userId = user.id
            token.enabled = true
            token.defaultToken = !hasDefault
            tokens.save(token)
            order.state = CardOrderState.ACTIVATED
            order.activatedAt = Instant.now(clock)
            order.updatedAt = Instant.now(clock)
            orders.save(order)
            db.audit(user.tenantId, user.id.toString(), "CARD_ACTIVATE", token.key.uid)
            ChargingTokenResponse.from(token, user.name)
        }
    }

    fun cancel(user: AppPrincipal, id: UUID): CardOrderResponse {
        val order = orders.findByTenantIdAndId(user.tenantId, id) ?: throw NotFound("Commande introuvable.")
        ensure(order.state in CANCELLABLE) { "Une carte activée ne peut plus être remboursée ici. Désactivez le badge si nécessaire." }
        val refund =
            order.stripePaymentIntentId?.let { intent ->
                gateway.refund(order.stripeAccountId, intent, order.amountMinor, order.id)
            }
        db.atomic {
            val saved = requireNotNull(db.lock<CardOrderEntity>(id))
            ensure(saved.state in CANCELLABLE) { "Cette commande a changé d’état." }
            saved.state = CardOrderState.CANCELLED
            saved.refundId = refund?.id
            saved.updatedAt = Instant.now(clock)
            orders.save(saved)
            db.audit(user.tenantId, user.id.toString(), "CARD_ORDER_CANCEL", id.toString())
        }
        return list(user, null, 0, 500).items.first { it.id == id }
    }

    fun forget(tenant: UUID, user: UUID) {
        orders.findAllByTenantIdAndUserIdOrderByCreatedAtDesc(tenant, user).forEach { order ->
            order.recipientName = null
            order.address = null
            order.postalCode = null
            order.city = null
            orders.save(order)
        }
    }

    fun handle(event: SignedPaymentEvent): Boolean {
        val raw = event.data.path("metadata").path("card_order_id").asText()
        if (raw.isBlank()) return false
        val id = runCatching { UUID.fromString(raw) }.getOrNull() ?: return true
        if (event.type !in CHECKOUT_EVENTS) return true
        var paid: CardOrderEntity? = null
        db.atomic {
            val order = db.lock<CardOrderEntity>(id) ?: return@atomic
            if (order.stripeAccountId != event.accountId) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Le compte Stripe ne correspond pas à la commande.")
            }
            if (order.stripeCheckoutId == null) {
                throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "La commande est en cours d’enregistrement. Réessayez.")
            }
            ensure(order.stripeCheckoutId == event.data.path("id").asText()) { "La session Stripe ne correspond pas à la commande." }
            if (events.existsById(event.id)) return@atomic
            if (event.type in FAILED_EVENTS) {
                if (order.state == CardOrderState.PENDING_PAYMENT) order.state = CardOrderState.CANCELLED
            } else if (event.data.path("payment_status").asText() == "paid") {
                ensure(
                    event.data.path("amount_total").longValue() == order.amountMinor &&
                        event.data.path("currency").asText().equals(order.currency, true)
                ) {
                    "Le montant Stripe ne correspond pas à la commande."
                }
                val intent = event.data.path("payment_intent").asText()
                ensure(intent.startsWith("pi_")) { "Le paiement Stripe est incomplet." }
                if (order.state == CardOrderState.PENDING_PAYMENT) {
                    order.stripePaymentIntentId = intent
                    order.state = CardOrderState.PAID
                    order.paidAt = Instant.now(clock)
                    paid = order
                }
            }
            order.updatedAt = Instant.now(clock)
            orders.save(order)
            events.save(StripeEventEntity(event.id, event.accountId, event.type))
        }
        paid?.let { order ->
            safely {
                notifications.notifyAdmins(
                    order.tenantId,
                    NotificationType.CARD_ORDER_PAID,
                    "Nouvelle commande de carte de recharge",
                    "Une carte a été commandée et payée. Associez-en une à la commande dans le tableau de bord.",
                    dedupeKey = "card-paid:${order.id}",
                )
            }
        }
        return true
    }

    private fun safely(block: () -> Unit) {
        try {
            db.isolated(block)
        } catch (error: RuntimeException) {
            log.warn("Notification de commande de carte non créée", error)
        }
    }

    private fun matches(token: ChargingTokenEntity, number: String): Boolean {
        val uid = normalize(token.key.uid)
        return uid == number || reversed(uid) == number || token.visualId?.let(::normalize) == number
    }

    private fun normalize(value: String) = value.uppercase().filter(Char::isLetterOrDigit)

    private fun reversed(uid: String) =
        if (uid.length % 2 == 0) uid.chunked(2).reversed().joinToString("") else uid

    private companion object {
        val CHECKOUT_WINDOW: Duration = Duration.ofHours(1)
        val CANCELLABLE = setOf(CardOrderState.PENDING_PAYMENT, CardOrderState.PAID, CardOrderState.SHIPPED)
        val FAILED_EVENTS = setOf("checkout.session.expired", "checkout.session.async_payment_failed")
        val CHECKOUT_EVENTS = FAILED_EVENTS + setOf("checkout.session.completed", "checkout.session.async_payment_succeeded")
    }
}
