package fr.codetics.ev_server.controllers.billing

import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.services.billing.StripeWebhookService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
class StripeWebhookController(private val webhooks: StripeWebhookService) {
    @PostMapping("/api/v1/stripe/webhook")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun receive(
        @RequestBody payload: String,
        @RequestHeader("Stripe-Signature") signature: String,
    ) {
        ensure(payload.length <= 1_048_576) { "La notification est trop volumineuse." }
        webhooks.receive(payload, signature)
    }
}
