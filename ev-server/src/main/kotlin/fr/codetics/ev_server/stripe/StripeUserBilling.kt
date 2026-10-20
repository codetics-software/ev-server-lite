package fr.codetics.ev_server.stripe

import com.stripe.exception.CardException
import com.stripe.exception.InvalidRequestException
import com.stripe.model.Invoice
import com.stripe.param.CustomerCreateParams
import com.stripe.param.CustomerPaymentMethodListParams
import com.stripe.param.CustomerUpdateParams
import com.stripe.param.InvoiceCreateParams
import com.stripe.param.InvoiceItemCreateParams
import com.stripe.param.SetupIntentCreateParams
import com.stripe.param.TaxRateListParams
import fr.codetics.ev_server.core.NotFound
import java.time.Instant
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class StripeUserBilling(
    private val stripe: StripeClientProvider,
    @Value("\${app.stripe.publishable-key:}") override val publishableKey: String,
) : UserBillingGateway {
    override val configured
        get() = stripe.configured

    override fun customer(accountId: String, userId: UUID, email: String, name: String, existing: String?): String {
        if (existing != null) {
            stripe.client.v1().customers().update(
                existing,
                CustomerUpdateParams.builder().setEmail(email).setName(name).build(),
                stripe.options(accountId),
            )
            return existing
        }
        return stripe.client.v1().customers().create(
            CustomerCreateParams.builder()
                .setEmail(email)
                .setName(name)
                .addPreferredLocale("fr")
                .putMetadata("user_id", userId.toString())
                .build(),
            stripe.options(accountId, "user:$userId:customer"),
        ).id
    }

    override fun setupIntent(accountId: String, customerId: String): String =
        requireNotNull(
            stripe.client.v1().setupIntents().create(
                SetupIntentCreateParams.builder()
                    .setCustomer(customerId)
                    .setUsage(SetupIntentCreateParams.Usage.OFF_SESSION)
                    .setAutomaticPaymentMethods(
                        SetupIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build()
                    )
                    .build(),
                stripe.options(accountId),
            ).clientSecret
        )

    override fun paymentMethods(accountId: String, customerId: String): List<PaymentMethodInfo> {
        val customer = stripe.client.v1().customers().retrieve(customerId, stripe.options(accountId))
        val default = customer.invoiceSettings?.defaultPaymentMethod
        return stripe.client.v1().customers().paymentMethods()
            .list(customerId, CustomerPaymentMethodListParams.builder().setLimit(50).build(), stripe.options(accountId))
            .data
            .map { method ->
                PaymentMethodInfo(
                    method.id,
                    method.card?.brand ?: method.type,
                    method.card?.last4 ?: method.sepaDebit?.last4,
                    method.card?.expMonth,
                    method.card?.expYear,
                    method.id == default,
                    Instant.ofEpochSecond(method.created),
                )
            }
    }

    override fun setDefaultPaymentMethod(accountId: String, customerId: String, paymentMethodId: String) {
        owned(accountId, customerId, paymentMethodId)
        stripe.client.v1().customers().update(
            customerId,
            CustomerUpdateParams.builder()
                .setInvoiceSettings(CustomerUpdateParams.InvoiceSettings.builder().setDefaultPaymentMethod(paymentMethodId).build())
                .build(),
            stripe.options(accountId),
        )
    }

    override fun detachPaymentMethod(accountId: String, customerId: String, paymentMethodId: String) {
        owned(accountId, customerId, paymentMethodId)
        stripe.client.v1().paymentMethods().detach(paymentMethodId, stripe.options(accountId))
    }

    private fun owned(accountId: String, customerId: String, paymentMethodId: String) {
        val method = stripe.client.v1().paymentMethods().retrieve(paymentMethodId, stripe.options(accountId))
        if (method.customer != customerId) throw NotFound("Moyen de paiement introuvable.")
    }

    override fun taxRates(accountId: String): List<TaxRateInfo> =
        stripe.client.v1().taxRates()
            .list(TaxRateListParams.builder().setActive(true).setLimit(100).build(), stripe.options(accountId))
            .data
            .map { TaxRateInfo(it.id, it.displayName, it.percentage, it.inclusive == true) }

    override fun invoice(
        accountId: String,
        customerId: String,
        invoiceId: UUID,
        currency: String,
        lines: List<InvoiceLine>,
        taxRateId: String?,
    ): GatewayInvoice {
        val draft =
            stripe.client.v1().invoices().create(
                InvoiceCreateParams.builder()
                    .setCustomer(customerId)
                    .setCurrency(currency.lowercase())
                    .setCollectionMethod(InvoiceCreateParams.CollectionMethod.CHARGE_AUTOMATICALLY)
                    .setPendingInvoiceItemsBehavior(InvoiceCreateParams.PendingInvoiceItemsBehavior.EXCLUDE)
                    .setAutoAdvance(false)
                    .putMetadata("invoice_id", invoiceId.toString())
                    .apply { taxRateId?.let(::addDefaultTaxRate) }
                    .build(),
                stripe.options(accountId, "invoice:$invoiceId:create"),
            )
        lines.forEach { line ->
            stripe.client.v1().invoiceItems().create(
                InvoiceItemCreateParams.builder()
                    .setCustomer(customerId)
                    .setInvoice(draft.id)
                    .setCurrency(currency.lowercase())
                    .setAmount(line.amountMinor)
                    .setDescription(line.description.take(500))
                    .putMetadata("reference", line.reference)
                    .build(),
                stripe.options(accountId, "invoice:$invoiceId:line:${line.reference}"),
            )
        }
        val finalized =
            stripe.client.v1().invoices().finalizeInvoice(draft.id, stripe.options(accountId, "invoice:$invoiceId:finalize"))
        return pay(accountId, requireNotNull(finalized.id))
    }

    override fun pay(accountId: String, stripeInvoiceId: String): GatewayInvoice =
        try {
            map(stripe.client.v1().invoices().pay(stripeInvoiceId, stripe.options(accountId)))
        } catch (_: CardException) {
            retrieve(accountId, stripeInvoiceId)
        } catch (error: InvalidRequestException) {
            val current = retrieve(accountId, stripeInvoiceId)
            if (current.status in setOf("paid", "open")) current else throw error
        }

    override fun retrieve(accountId: String, stripeInvoiceId: String): GatewayInvoice =
        map(stripe.client.v1().invoices().retrieve(stripeInvoiceId, stripe.options(accountId)))

    private fun map(invoice: Invoice) =
        GatewayInvoice(
            requireNotNull(invoice.id),
            invoice.number,
            invoice.status ?: "draft",
            invoice.total ?: invoice.amountDue ?: 0,
            invoice.hostedInvoiceUrl,
            invoice.invoicePdf,
        )
}
