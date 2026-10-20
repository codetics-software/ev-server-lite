package fr.codetics.ev_server.stripe

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class PaymentMethodInfo(
    val id: String,
    val brand: String?,
    val last4: String?,
    val expMonth: Long?,
    val expYear: Long?,
    val default: Boolean,
    val createdAt: Instant,
)

data class TaxRateInfo(val id: String, val displayName: String, val percentage: BigDecimal, val inclusive: Boolean)

data class InvoiceLine(val reference: String, val description: String, val amountMinor: Long)

data class GatewayInvoice(
    val id: String,
    val number: String?,
    val status: String,
    val amountMinor: Long,
    val hostedUrl: String?,
    val pdfUrl: String?,
)

interface UserBillingGateway {
    val configured: Boolean

    val publishableKey: String

    fun customer(accountId: String, userId: UUID, email: String, name: String, existing: String?): String

    fun setupIntent(accountId: String, customerId: String): String

    fun paymentMethods(accountId: String, customerId: String): List<PaymentMethodInfo>

    fun setDefaultPaymentMethod(accountId: String, customerId: String, paymentMethodId: String)

    fun detachPaymentMethod(accountId: String, customerId: String, paymentMethodId: String)

    fun taxRates(accountId: String): List<TaxRateInfo>

    fun invoice(
        accountId: String,
        customerId: String,
        invoiceId: UUID,
        currency: String,
        lines: List<InvoiceLine>,
        taxRateId: String?,
    ): GatewayInvoice

    fun pay(accountId: String, stripeInvoiceId: String): GatewayInvoice

    fun retrieve(accountId: String, stripeInvoiceId: String): GatewayInvoice
}
