package fr.codetics.ev_server.services.billing

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.NotFound
import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.core.lock
import fr.codetics.ev_server.core.pageRequest
import fr.codetics.ev_server.models.api.MutationResponse
import fr.codetics.ev_server.models.api.PageResponse
import fr.codetics.ev_server.models.entities.InvoiceEntity
import fr.codetics.ev_server.models.entities.StationKey
import fr.codetics.ev_server.models.entities.TransactionEntity
import fr.codetics.ev_server.models.entities.UserEntity
import fr.codetics.ev_server.models.entities.WorkspacePaymentSettings
import fr.codetics.ev_server.models.notifications.NotificationType
import fr.codetics.ev_server.models.people.UserRole
import fr.codetics.ev_server.models.sessions.ChargingSessionResponse
import fr.codetics.ev_server.models.workspaces.UserBillingInput
import fr.codetics.ev_server.ocpp.AuthorizationStatus
import fr.codetics.ev_server.ocpp.TransactionStopped
import fr.codetics.ev_server.persistence.Queries
import fr.codetics.ev_server.persistence.eq
import fr.codetics.ev_server.persistence.list
import fr.codetics.ev_server.repositories.ChargingPaymentRepository
import fr.codetics.ev_server.repositories.InvoiceRepository
import fr.codetics.ev_server.repositories.StationRepository
import fr.codetics.ev_server.repositories.TransactionRepository
import fr.codetics.ev_server.repositories.UserRepository
import fr.codetics.ev_server.repositories.WorkspacePaymentSettingsRepository
import fr.codetics.ev_server.services.notifications.NotificationService
import fr.codetics.ev_server.stripe.GatewayInvoice
import fr.codetics.ev_server.stripe.InvoiceLine
import fr.codetics.ev_server.stripe.PaymentGateway
import fr.codetics.ev_server.stripe.PaymentMethodInfo
import fr.codetics.ev_server.stripe.SignedPaymentEvent
import fr.codetics.ev_server.stripe.TaxRateInfo
import fr.codetics.ev_server.stripe.UserBillingGateway
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class PaymentMethodResponse(
    val id: String,
    val brand: String?,
    val last4: String?,
    val expMonth: Long?,
    val expYear: Long?,
    val default: Boolean,
    val createdAt: Instant,
) {
    companion object {
        fun from(info: PaymentMethodInfo) =
            PaymentMethodResponse(info.id, info.brand, info.last4, info.expMonth, info.expYear, info.default, info.createdAt)
    }
}

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class SetupResponse(val clientSecret: String, val accountId: String, val publishableKey: String)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class TaxRateResponse(val id: String, val displayName: String, val percentage: BigDecimal, val inclusive: Boolean) {
    companion object {
        fun from(info: TaxRateInfo) = TaxRateResponse(info.id, info.displayName, info.percentage, info.inclusive)
    }
}

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class InvoiceResponse(
    val id: UUID,
    val number: String?,
    val userId: UUID,
    val userName: String?,
    val status: String,
    val amountMinor: Long,
    val currency: String,
    val createdAt: Instant,
    val sessionIds: List<Int>,
    val hostedUrl: String?,
    val lastError: String?,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class BillingCheck(val ready: Boolean, val message: String)

@Service
class UserBillingService(
    private val db: Database,
    private val settings: WorkspacePaymentSettingsRepository,
    private val users: UserRepository,
    private val transactions: TransactionRepository,
    private val invoices: InvoiceRepository,
    private val stations: StationRepository,
    private val payments: ChargingPaymentRepository,
    private val queries: Queries,
    private val gateway: UserBillingGateway,
    private val connect: PaymentGateway,
    private val notifications: NotificationService,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val zone = ZoneId.of("Europe/Paris")
    private val months = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH)

    private fun settings(tenant: UUID): WorkspacePaymentSettings? = settings.findById(tenant).orElse(null)

    private fun account(tenant: UUID): String {
        ensure(gateway.configured) { "Stripe n’est pas configuré sur ce serveur." }
        return settings(tenant)?.stripeAccountId
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Connectez d’abord le compte Stripe de l’espace.")
    }

    fun updateSettings(user: AppPrincipal, input: UserBillingInput) {
        ensure(input.mode in setOf("PER_SESSION", "MONTHLY")) { "Mode de facturation inconnu." }
        ensure(input.taxRateId == null || input.taxRateId.matches(Regex("txr_[A-Za-z0-9]{1,100}"))) { "Taux de taxe invalide." }
        if (input.enabled) account(user.tenantId)
        db.atomic {
            val current = db.lock<WorkspacePaymentSettings>(user.tenantId) ?: WorkspacePaymentSettings(tenantId = user.tenantId)
            current.userBillingEnabled = input.enabled
            current.userBillingMode = input.mode
            current.taxRateId = input.taxRateId?.ifBlank { null }
            current.updatedAt = Instant.now(clock)
            settings.save(current)
            db.audit(user.tenantId, user.id.toString(), "USER_BILLING_SETTINGS", input.mode)
        }
    }

    fun check(user: AppPrincipal): BillingCheck {
        if (!gateway.configured) return BillingCheck(false, "Stripe n’est pas configuré sur ce serveur.")
        val accountId = settings(user.tenantId)?.stripeAccountId ?: return BillingCheck(false, "Aucun compte Stripe n’est connecté.")
        return if (connect.accountReady(accountId)) BillingCheck(true, "Le compte Stripe est prêt.")
        else BillingCheck(false, "Terminez la vérification du compte chez Stripe.")
    }

    fun taxes(user: AppPrincipal): List<TaxRateResponse> = gateway.taxRates(account(user.tenantId)).map(TaxRateResponse::from)

    private fun user(tenant: UUID, id: UUID): UserEntity =
        users.findByTenantIdAndId(tenant, id)?.takeIf { it.deletedAt == null } ?: throw NotFound("Utilisateur introuvable.")

    fun synchronize(user: AppPrincipal, id: UUID): MutationResponse {
        val accountId = account(user.tenantId)
        val account = user(user.tenantId, id)
        val customer = gateway.customer(accountId, account.id, account.email, account.displayName, account.stripeCustomerId)
        db.atomic {
            val stored = user(user.tenantId, id)
            stored.stripeCustomerId = customer
            users.save(stored)
            db.audit(user.tenantId, user.id.toString(), "BILLING_USER_SYNCHRONIZE", id.toString())
        }
        return MutationResponse()
    }

    private fun customer(tenant: UUID, id: UUID): Pair<String, String> {
        val accountId = account(tenant)
        val account = user(tenant, id)
        if (account.stripeCustomerId != null) return accountId to requireNotNull(account.stripeCustomerId)
        val customer = gateway.customer(accountId, account.id, account.email, account.displayName, null)
        db.atomic {
            val stored = user(tenant, id)
            stored.stripeCustomerId = customer
            users.save(stored)
        }
        return accountId to customer
    }

    private fun target(user: AppPrincipal, id: UUID?): UUID {
        val target = id ?: user.id
        if (target != user.id && user.role != UserRole.ADMIN) throw ResponseStatusException(HttpStatus.FORBIDDEN)
        return target
    }

    fun paymentMethods(user: AppPrincipal, id: UUID? = null): List<PaymentMethodResponse> {
        val account = user(user.tenantId, target(user, id))
        val accountId = settings(user.tenantId)?.stripeAccountId ?: return emptyList()
        val customer = account.stripeCustomerId ?: return emptyList()
        if (!gateway.configured) return emptyList()
        return gateway.paymentMethods(accountId, customer).map(PaymentMethodResponse::from)
    }

    fun setup(user: AppPrincipal): SetupResponse {
        ensure(gateway.publishableKey.isNotBlank()) { "Configurez STRIPE_PUBLISHABLE_KEY pour enregistrer un moyen de paiement." }
        val (accountId, customer) = customer(user.tenantId, user.id)
        return SetupResponse(gateway.setupIntent(accountId, customer), accountId, gateway.publishableKey)
    }

    fun setDefault(user: AppPrincipal, paymentMethod: String): MutationResponse {
        ensure(paymentMethod.matches(Regex("pm_[A-Za-z0-9]{1,100}"))) { "Moyen de paiement invalide." }
        val (accountId, customer) = customer(user.tenantId, user.id)
        gateway.setDefaultPaymentMethod(accountId, customer, paymentMethod)
        db.audit(user.tenantId, user.id.toString(), "PAYMENT_METHOD_DEFAULT", paymentMethod)
        return MutationResponse()
    }

    fun detach(user: AppPrincipal, paymentMethod: String): MutationResponse {
        ensure(paymentMethod.matches(Regex("pm_[A-Za-z0-9]{1,100}"))) { "Moyen de paiement invalide." }
        val (accountId, customer) = customer(user.tenantId, user.id)
        gateway.detachPaymentMethod(accountId, customer, paymentMethod)
        db.audit(user.tenantId, user.id.toString(), "PAYMENT_METHOD_DELETE", paymentMethod)
        return MutationResponse()
    }

    fun invoices(user: AppPrincipal, userId: UUID?, status: String?, offset: Int, limit: Int): PageResponse<InvoiceResponse> {
        val target = if (user.role == UserRole.DRIVER) user.id else userId
        val request = pageRequest(offset, limit, Sort.by(Sort.Direction.DESC, "createdAt"))
        val page =
            when {
                target != null && !status.isNullOrBlank() -> invoices.findAllByTenantIdAndUserIdAndStatus(user.tenantId, target, status, request)
                target != null -> invoices.findAllByTenantIdAndUserId(user.tenantId, target, request)
                !status.isNullOrBlank() -> invoices.findAllByTenantIdAndStatus(user.tenantId, status, request)
                else -> invoices.findAllByTenantId(user.tenantId, request)
            }
        val names = users.findAllByTenantIdAndIdIn(user.tenantId, page.content.map { it.userId }.toSet()).associate { it.id to it.displayName }
        return PageResponse(page.content.map { response(it, names[it.userId]) }, page.totalElements)
    }

    private fun invoice(user: AppPrincipal, id: UUID): InvoiceEntity {
        val invoice = invoices.findByTenantIdAndId(user.tenantId, id) ?: throw NotFound("Facture introuvable.")
        if (user.role == UserRole.DRIVER && invoice.userId != user.id) throw NotFound("Facture introuvable.")
        return invoice
    }

    fun download(user: AppPrincipal, id: UUID): String {
        val invoice = invoice(user, id)
        val stripeId = invoice.stripeInvoiceId ?: throw ResponseStatusException(HttpStatus.CONFLICT, "La facture n’est pas encore émise.")
        val current = gateway.retrieve(invoice.stripeAccountId, stripeId)
        return current.pdfUrl ?: current.hostedUrl
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Le document de facture n’est pas disponible.")
    }

    fun pay(user: AppPrincipal, id: UUID): InvoiceResponse {
        val invoice = invoice(user, id)
        val stripeId = invoice.stripeInvoiceId ?: throw ResponseStatusException(HttpStatus.CONFLICT, "La facture n’est pas encore émise.")
        val result = gateway.pay(invoice.stripeAccountId, stripeId)
        apply(invoice.id, result)
        db.audit(user.tenantId, user.id.toString(), "INVOICE_PAY", id.toString())
        return response(invoices.findById(id).orElseThrow(), null)
    }

    @TransactionalEventListener(fallbackExecution = true)
    fun stopped(event: TransactionStopped) {
        try {
            db.isolated {
                val transaction = transactions.findById(requireNotNull(event.transaction.id)).orElse(null) ?: return@isolated
                if (!billable(transaction)) return@isolated
                transaction.billingStatus = "PENDING"
                transactions.save(transaction)
            }
        } catch (error: RuntimeException) {
            log.warn("Préparation de la facturation impossible pour la session {}", event.transaction.id, error)
        }
    }

    private fun billable(transaction: TransactionEntity): Boolean {
        val current = settings(transaction.tenantId) ?: return false
        return current.userBillingEnabled &&
            current.stripeAccountId != null &&
            transaction.billingStatus == "NONE" &&
            transaction.userId != null &&
            transaction.roamingPartnerId == null &&
            transaction.authorizationStatus == AuthorizationStatus.Accepted &&
            (transaction.price?.signum() ?: 0) > 0 &&
            payments.findByTenantIdAndTransactionId(transaction.tenantId, requireNotNull(transaction.id)) == null
    }

    @Scheduled(initialDelay = 60000, fixedDelay = 60000)
    fun worker() {
        if (!gateway.configured) return
        val pending =
            queries.list<TransactionEntity>(listOf(eq("billingStatus", "PENDING")), Sort.by("tenantId", "userId", "startedAt"), 500)
        pending.groupBy { it.tenantId }.forEach { (tenant, sessions) ->
            val current = settings(tenant) ?: return@forEach
            if (!current.userBillingEnabled || current.stripeAccountId == null) return@forEach
            val thisMonth = YearMonth.now(clock.withZone(zone))
            sessions.groupBy { requireNotNull(it.userId) }.forEach { (userId, owned) ->
                if (current.userBillingMode == "MONTHLY") {
                    owned.groupBy { YearMonth.from(requireNotNull(it.stoppedAt).atZone(zone)) }
                        .filterKeys { it < thisMonth }
                        .forEach { (month, rows) -> safely { bill(tenant, userId, rows, current, month.toString()) } }
                } else {
                    owned.forEach { row -> safely { bill(tenant, userId, listOf(row), current, null) } }
                }
            }
        }
        invoices.findAllByStatus("OPEN", PageRequest.of(0, 200)).forEach { invoice ->
            safely {
                invoice.stripeInvoiceId?.let { apply(invoice.id, gateway.retrieve(invoice.stripeAccountId, it)) }
            }
        }
    }

    private fun safely(block: () -> Unit) {
        try {
            block()
        } catch (error: RuntimeException) {
            log.warn("Facturation interrompue", error)
        }
    }

    private fun bill(tenant: UUID, userId: UUID, rows: List<TransactionEntity>, current: WorkspacePaymentSettings, period: String?) {
        val accountId = requireNotNull(current.stripeAccountId)
        val currency = rows.mapNotNull { it.currency }.distinct().singleOrNull() ?: "EUR"
        val draft =
            db.atomic {
                val locked = rows.mapNotNull { db.lock<TransactionEntity>(requireNotNull(it.id)) }.filter { it.billingStatus == "PENDING" }
                if (locked.isEmpty()) return@atomic null
                val entity =
                    invoices.save(
                        InvoiceEntity(
                            tenantId = tenant,
                            userId = userId,
                            stripeAccountId = accountId,
                            currency = currency,
                            sessionIds = db.encode(locked.map { it.id }),
                            periodKey = period,
                            amountMinor = locked.sumOf { minor(it) },
                        )
                    )
                locked.forEach {
                    it.billingStatus = "INVOICED"
                    it.invoiceId = entity.id
                    transactions.save(it)
                }
                entity to locked
            } ?: return
        val (entity, sessions) = draft
        try {
            val (_, customer) = customer(tenant, userId)
            val names =
                sessions.map { it.stationId }.toSet().associateWith { id ->
                    stations.findById(StationKey(tenant, id)).map { it.name }.orElse(id)
                }
            val lines =
                sessions.map {
                    InvoiceLine(
                        "session-${it.id}",
                        "Recharge du ${it.startedAt.atZone(zone).toLocalDate()} — ${names[it.stationId]} — ${ChargingSessionResponse.energyKwh(it)} kWh",
                        minor(it),
                    )
                }
            apply(entity.id, gateway.invoice(accountId, customer, entity.id, currency, lines, current.taxRateId))
            val owner = users.findByTenantIdAndId(tenant, userId)
            if (owner != null) {
                db.isolated {
                    notifications.notify(
                        owner,
                        NotificationType.INVOICE_AVAILABLE,
                        "Votre facture est disponible",
                        "Une facture de ${BigDecimal(entity.amountMinor).movePointLeft(2)} $currency" +
                            (period?.let { " pour ${YearMonth.parse(it).format(months)}" } ?: "") +
                            " est disponible dans votre espace.",
                        dedupeKey = "invoice:${entity.id}",
                    )
                }
            }
        } catch (error: RuntimeException) {
            db.atomic {
                val failed = invoices.findById(entity.id).orElseThrow()
                failed.status = "FAILED"
                failed.lastError = (error.message ?: "Facturation impossible.").take(500)
                failed.updatedAt = Instant.now(clock)
                invoices.save(failed)
                sessions.forEach { session ->
                    transactions.findById(requireNotNull(session.id)).ifPresent {
                        it.billingStatus = "FAILED"
                        transactions.save(it)
                    }
                }
            }
            users.findByTenantIdAndId(tenant, userId)?.let { owner ->
                db.isolated {
                    notifications.notify(
                        owner,
                        NotificationType.BILLING_FAILED,
                        "Le paiement de votre recharge a échoué",
                        "Nous n’avons pas pu facturer votre recharge. Vérifiez votre moyen de paiement dans votre compte.",
                        dedupeKey = "billing-failed:${entity.id}",
                    )
                }
            }
            throw error
        }
    }

    private fun minor(transaction: TransactionEntity) =
        (transaction.price ?: BigDecimal.ZERO).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()

    private fun apply(id: UUID, result: GatewayInvoice) {
        db.atomic {
            val invoice = db.lock<InvoiceEntity>(id) ?: return@atomic
            invoice.stripeInvoiceId = result.id
            invoice.number = result.number
            invoice.status =
                when (result.status) {
                    "paid" -> "PAID"
                    "open" -> "OPEN"
                    "void" -> "VOID"
                    "uncollectible" -> "UNCOLLECTIBLE"
                    else -> "DRAFT"
                }
            invoice.amountMinor = result.amountMinor
            invoice.hostedUrl = result.hostedUrl
            invoice.pdfUrl = result.pdfUrl
            invoice.lastError = null
            invoice.updatedAt = Instant.now(clock)
            invoices.save(invoice)
            if (invoice.status == "PAID") {
                db.decode(invoice.sessionIds).forEach { node ->
                    transactions.findById(node.intValue()).ifPresent {
                        it.billingStatus = "PAID"
                        transactions.save(it)
                    }
                }
            }
        }
    }

    fun handle(event: SignedPaymentEvent): Boolean {
        if (!event.type.startsWith("invoice.")) return false
        val id = runCatching { UUID.fromString(event.data.path("metadata").path("invoice_id").asText()) }.getOrNull() ?: return true
        val invoice = invoices.findById(id).orElse(null) ?: return true
        if (invoice.stripeAccountId != event.accountId) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Le compte Stripe ne correspond pas à la facture.")
        }
        val stripeId = event.data.path("id").asText()
        if (invoice.stripeInvoiceId != null && invoice.stripeInvoiceId != stripeId) return true
        apply(id, gateway.retrieve(invoice.stripeAccountId, stripeId))
        return true
    }

    private fun response(invoice: InvoiceEntity, name: String?) =
        InvoiceResponse(
            invoice.id,
            invoice.number,
            invoice.userId,
            name,
            invoice.status,
            invoice.amountMinor,
            invoice.currency,
            invoice.createdAt,
            db.decode(invoice.sessionIds).toList().map { it.intValue() },
            invoice.hostedUrl,
            invoice.lastError,
        )

}
