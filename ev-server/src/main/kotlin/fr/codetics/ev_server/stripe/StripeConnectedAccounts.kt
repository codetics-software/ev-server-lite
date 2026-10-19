package fr.codetics.ev_server.stripe

import com.stripe.param.v2.core.AccountCreateParams
import com.stripe.param.v2.core.AccountLinkCreateParams
import com.stripe.param.v2.core.AccountRetrieveParams
import java.util.UUID
import org.springframework.stereotype.Component

@Component
class StripeConnectedAccounts(private val stripe: StripeClientProvider) {
    fun create(tenantId: UUID, name: String, email: String, country: String): String {
        val capabilities =
            AccountCreateParams.Configuration.Merchant.Capabilities.builder()
                .setCardPayments(
                    AccountCreateParams.Configuration.Merchant.Capabilities.CardPayments.builder()
                        .setRequested(true)
                        .build()
                )
                .build()
        val responsibilities =
            AccountCreateParams.Defaults.Responsibilities.builder()
                .setFeesCollector(
                    AccountCreateParams.Defaults.Responsibilities.FeesCollector.STRIPE
                )
                .setLossesCollector(
                    AccountCreateParams.Defaults.Responsibilities.LossesCollector.STRIPE
                )
                .build()
        val parameters =
            AccountCreateParams.builder()
                .setDisplayName(name)
                .setContactEmail(email)
                .setDashboard(AccountCreateParams.Dashboard.FULL)
                .setIdentity(
                    AccountCreateParams.Identity.builder().setCountry(country.lowercase()).build()
                )
                .setConfiguration(
                    AccountCreateParams.Configuration.builder()
                        .setMerchant(
                            AccountCreateParams.Configuration.Merchant.builder()
                                .setCapabilities(capabilities)
                                .build()
                        )
                        .build()
                )
                .setDefaults(
                    AccountCreateParams.Defaults.builder()
                        .setCurrency("eur")
                        .setResponsibilities(responsibilities)
                        .addLocale(AccountCreateParams.Defaults.Locale.FR_FR)
                        .build()
                )
                .putMetadata("tenant_id", tenantId.toString())
                .build()
        return stripe.client
            .v2()
            .core()
            .accounts()
            .create(parameters, stripe.options(idempotencyKey = "workspace:$tenantId:account"))
            .id
    }

    fun onboardingUrl(accountId: String, returnUrl: String): String {
        val onboarding =
            AccountLinkCreateParams.UseCase.AccountOnboarding.builder()
                .setReturnUrl(returnUrl)
                .setRefreshUrl("$returnUrl?renouveler=1")
                .build()
        val parameters =
            AccountLinkCreateParams.builder()
                .setAccount(accountId)
                .setUseCase(
                    AccountLinkCreateParams.UseCase.builder()
                        .setType(AccountLinkCreateParams.UseCase.Type.ACCOUNT_ONBOARDING)
                        .setAccountOnboarding(onboarding)
                        .build()
                )
                .build()
        return stripe.client.v2().core().accountLinks().create(parameters).url
    }

    fun ready(accountId: String): Boolean {
        val account =
            stripe.client
                .v2()
                .core()
                .accounts()
                .retrieve(
                    accountId,
                    AccountRetrieveParams.builder()
                        .addInclude(AccountRetrieveParams.Include.CONFIGURATION__MERCHANT)
                        .build(),
                )
        return account.configuration?.merchant?.capabilities?.cardPayments?.status == "active"
    }
}
