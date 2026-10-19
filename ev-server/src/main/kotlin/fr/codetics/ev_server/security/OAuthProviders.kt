package fr.codetics.ev_server.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod

class ProviderRegistry(
    val registrations: List<ClientRegistration>
) : ClientRegistrationRepository, Iterable<ClientRegistration> {
    override fun findByRegistrationId(registrationId: String): ClientRegistration? {
        return registrations.find { registration ->
            registration.registrationId == registrationId
        }
    }

    override fun iterator(): Iterator<ClientRegistration> {
        return registrations.iterator()
    }
}

private data class ProviderEndpoints(
    val issuer: String,
    val authorization: String,
    val token: String,
    val keys: String
)

@Configuration(proxyBeanMethods = false)
class OAuthProviders {
    @Bean
    fun providers(environment: Environment): ProviderRegistry {
        val publicUrl = requireNotNull(environment.getProperty("app.public-url")).trimEnd('/')
        val registrations = listOfNotNull(
            buildProvider(
                environment,
                "google",
                publicUrl,
                listOf("openid", "email", "profile"),
                ProviderEndpoints(
                    issuer = "https://accounts.google.com",
                    authorization = "https://accounts.google.com/o/oauth2/v2/auth",
                    token = "https://oauth2.googleapis.com/token",
                    keys = "https://www.googleapis.com/oauth2/v3/certs"
                )
            ),
            microsoft(environment, publicUrl),
            buildProvider(
                environment,
                "apple",
                publicUrl,
                listOf("openid", "email", "name"),
                ProviderEndpoints(
                    issuer = "https://appleid.apple.com",
                    authorization = "https://appleid.apple.com/auth/authorize",
                    token = "https://appleid.apple.com/auth/token",
                    keys = "https://appleid.apple.com/auth/keys"
                )
            )
        )

        return ProviderRegistry(registrations)
    }

    private fun microsoft(environment: Environment, publicUrl: String): ClientRegistration? {
        val client = environment.getProperty("MICROSOFT_CLIENT_ID")

        if (client.isNullOrBlank()) {
            return null
        }

        val tenant = requireNotNull(environment.getProperty("MICROSOFT_TENANT_ID")) {
            "Set MICROSOFT_TENANT_ID to your Entra directory UUID"
        }
        require(tenant.matches(Regex("[0-9a-fA-F-]{36}"))) {
            "A specific Microsoft tenant is required"
        }

        return buildProvider(
            environment,
            "microsoft",
            publicUrl,
            listOf("openid", "email", "profile"),
            ProviderEndpoints(
                issuer = "https://login.microsoftonline.com/$tenant/v2.0",
                authorization = "https://login.microsoftonline.com/$tenant/oauth2/v2.0/authorize",
                token = "https://login.microsoftonline.com/$tenant/oauth2/v2.0/token",
                keys = "https://login.microsoftonline.com/$tenant/discovery/v2.0/keys"
            )
        )
    }

    private fun buildProvider(
        environment: Environment,
        id: String,
        publicUrl: String,
        scopes: List<String>,
        endpoints: ProviderEndpoints
    ): ClientRegistration? {
        val clientId = environment.getProperty("${id.uppercase()}_CLIENT_ID")

        if (clientId.isNullOrBlank()) {
            return null
        }

        val secret = requireNotNull(environment.getProperty("${id.uppercase()}_CLIENT_SECRET")) {
            "Missing $id client secret"
        }
        require(secret.isNotBlank())
        val authentication = if (id == "apple") {
            ClientAuthenticationMethod.CLIENT_SECRET_POST
        } else {
            ClientAuthenticationMethod.CLIENT_SECRET_BASIC
        }

        return ClientRegistration.withRegistrationId(id)
            .clientId(clientId)
            .clientSecret(secret)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .clientAuthenticationMethod(authentication)
            .redirectUri("$publicUrl/login/oauth2/code/$id")
            .scope(scopes)
            .authorizationUri(endpoints.authorization)
            .tokenUri(endpoints.token)
            .jwkSetUri(endpoints.keys)
            .issuerUri(endpoints.issuer)
            .userNameAttributeName("sub")
            .clientName(id.replaceFirstChar(Char::uppercase))
            .build()
    }
}
