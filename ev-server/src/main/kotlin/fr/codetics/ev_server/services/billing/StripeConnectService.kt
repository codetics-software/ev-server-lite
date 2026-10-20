package fr.codetics.ev_server.services.billing

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.AppProperties
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.core.lock
import fr.codetics.ev_server.models.entities.WorkspacePaymentSettings
import fr.codetics.ev_server.models.workspaces.WorkspacePaymentInput
import fr.codetics.ev_server.models.workspaces.UserBillingSettings
import fr.codetics.ev_server.models.workspaces.WorkspacePaymentResponse
import fr.codetics.ev_server.repositories.TenantRepository
import fr.codetics.ev_server.repositories.WorkspacePaymentSettingsRepository
import fr.codetics.ev_server.stripe.PaymentGateway
import java.time.Instant
import org.springframework.stereotype.Service

@Service
class StripeConnectService(
    private val settings: WorkspacePaymentSettingsRepository,
    private val tenants: TenantRepository,
    private val gateway: PaymentGateway,
    private val db: Database,
    private val properties: AppProperties,
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
            UserBillingSettings(
                current?.userBillingEnabled == true,
                current?.userBillingMode ?: "PER_SESSION",
                current?.taxRateId,
            ),
        )
    }

    fun update(user: AppPrincipal, input: WorkspacePaymentInput): WorkspacePaymentResponse {
        ensure(input.country.matches(Regex("[A-Z]{2}"))) {
            "Le code du pays doit contenir deux lettres majuscules."
        }
        db.atomic {
            val current =
                db.lock<WorkspacePaymentSettings>(user.tenantId)
                    ?: WorkspacePaymentSettings(tenantId = user.tenantId)
            ensure(current.stripeAccountId == null || current.country == input.country) {
                "Le pays ne peut plus être modifié après la création du compte Stripe."
            }
            current.enabled = input.enabled
            current.country = input.country
            current.updatedAt = Instant.now()
            settings.save(current).also { db.flush() }
            db.audit(
                user.tenantId,
                user.id.toString(),
                "PAYMENT_SETTINGS_UPDATE",
                user.tenantId.toString(),
            )
        }
        return status(user)
    }

    fun onboarding(user: AppPrincipal): String {
        val accountId =
            db.atomic {
                if (!settings.existsById(user.tenantId))
                    settings.save(WorkspacePaymentSettings(tenantId = user.tenantId)).also {
                        db.flush()
                    }
                val current = requireNotNull(db.lock<WorkspacePaymentSettings>(user.tenantId))
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
        return gateway.onboardingUrl(accountId, "${properties.publicUrl.trimEnd('/')}/payments")
    }
}
