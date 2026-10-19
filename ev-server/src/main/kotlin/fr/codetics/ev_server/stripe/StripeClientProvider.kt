package fr.codetics.ev_server.stripe

import com.stripe.StripeClient
import com.stripe.net.RequestOptions
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

@Component
class StripeClientProvider(
    @Value("\${app.stripe.api-key:}") private val apiKey: String,
    @Value("\${app.stripe.webhook-secret:}") val webhookSecret: String,
) {
    val configured
        get() = apiKey.isNotBlank() && webhookSecret.isNotBlank()

    val client: StripeClient by lazy {
        if (!configured)
            throw ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Le paiement n’est pas configuré.",
            )
        StripeClient.builder()
            .setApiKey(apiKey)
            .setConnectTimeout(5000)
            .setReadTimeout(20000)
            .setMaxNetworkRetries(2)
            .build()
    }

    fun options(accountId: String? = null, idempotencyKey: String? = null): RequestOptions =
        RequestOptions.builder()
            .apply {
                accountId?.let(::setStripeAccount)
                idempotencyKey?.let(::setIdempotencyKey)
            }
            .build()
}
