package fr.codetics.ev_server.services.billing

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.models.entities.WorkspacePaymentSettings
import fr.codetics.ev_server.models.workspaces.WorkspacePaymentInput
import fr.codetics.ev_server.models.workspaces.WorkspacePaymentResponse
import fr.codetics.ev_server.repositories.TenantRepository
import fr.codetics.ev_server.repositories.WorkspacePaymentSettingsRepository
import fr.codetics.ev_server.stripe.PaymentGateway
import java.time.Instant
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class StripeConnectService(
    private val settings: WorkspacePaymentSettingsRepository,
    private val tenants: TenantRepository,
    private val gateway: PaymentGateway,
    private val db: Database,
    @Value("\${app.public-url}") private val publicUrl: String,
) {
    fun status(user: AppPrincipal): WorkspacePaymentResponse {
        val current = settings.findById(user.tenantId).orElse(null)
        val ready =
            gateway.configured && current?.stripeAccountId?.let(gateway::accountReady) == true
        return WorkspacePaymentResponse(
            gateway.configured,
            current?.enabled == true,
            current?.stripeAccountId,
            ready,
            current?.country ?: "FR",
            current?.currency ?: "EUR",
        )
    }

    fun update(user: AppPrincipal, input: WorkspacePaymentInput): WorkspacePaymentResponse {
        require(input.country.matches(Regex("[A-Z]{2}"))) {
            "Le code du pays doit contenir deux lettres majuscules."
        }
        db.atomic {
            val current =
                settings.findForUpdate(user.tenantId)
                    ?: WorkspacePaymentSettings(tenantId = user.tenantId)
            require(current.stripeAccountId == null || current.country == input.country) {
                "Le pays ne peut plus être modifié après la création du compte Stripe."
            }
            current.enabled = input.enabled
            current.country = input.country
            current.updatedAt = Instant.now()
            settings.saveAndFlush(current)
            db.audit(
                user.tenantId,
                user.id.toString(),
                "PAYMENT_SETTINGS_UPDATE",
                user.tenantId.toString(),
            )
            true
        }
        return status(user)
    }

    fun onboarding(user: AppPrincipal): String {
        val accountId =
            db.atomic {
                if (!settings.existsById(user.tenantId))
                    settings.saveAndFlush(WorkspacePaymentSettings(tenantId = user.tenantId))
                val current = requireNotNull(settings.findForUpdate(user.tenantId))
                if (current.stripeAccountId == null) {
                    val tenant = tenants.findById(user.tenantId).orElseThrow()
                    current.stripeAccountId =
                        gateway.createAccount(
                            user.tenantId,
                            tenant.name,
                            user.email,
                            current.country,
                        )
                    settings.save(current)
                    db.audit(
                        user.tenantId,
                        user.id.toString(),
                        "STRIPE_ACCOUNT_CREATE",
                        user.tenantId.toString(),
                    )
                }
                requireNotNull(current.stripeAccountId)
            }
        return gateway.onboardingUrl(accountId, "${publicUrl.trimEnd('/')}/payments")
    }
}
