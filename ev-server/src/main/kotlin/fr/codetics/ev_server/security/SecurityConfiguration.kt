package fr.codetics.ev_server.security

import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfiguration {
    @Bean
    @Order(1)
    fun protocols(http: HttpSecurity): SecurityFilterChain {
        // Les protocoles vérifient leurs propres identifiants de borne ou de partenaire.
        // Les cookies du navigateur ne donnent aucun accès aux protocoles.
        http
            .securityMatcher("/ocpp/**", "/ocpi/**")
            .authorizeHttpRequests { authorization -> authorization.anyRequest().permitAll() }
            .csrf { csrf -> csrf.disable() }
            .requestCache { cache -> cache.disable() }
            .sessionManagement { sessions ->
                sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }

        return http.build()
    }

    @Bean
    @Order(2)
    fun application(
        http: HttpSecurity,
        accounts: AccountService,
        providers: ProviderRegistry,
        successHandler: OAuthSignInHandler,
        @Value("\${app.cookie-secure}") secure: Boolean,
        @Value("\${app.public-url}") publicUrl: String,
    ): SecurityFilterChain {
        val csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse()
        csrfRepository.setCookieCustomizer { cookie ->
            cookie.secure(secure).sameSite("Lax").path("/")
        }

        http.csrf { csrf ->
            csrf
                .csrfTokenRepository(csrfRepository)
                .csrfTokenRequestHandler(CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers("/login/oauth2/code/apple", "/api/v1/stripe/webhook")
        }
        http.addFilterBefore(
            AccountAuthenticationFilter(accounts),
            UsernamePasswordAuthenticationFilter::class.java,
        )
        http.authorizeHttpRequests { authorization ->
            authorization
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/auth/csrf",
                    "/api/v1/auth/providers",
                    "/api/v1/auth/eula",
                    "/actuator/health",
                    "/api/v1/public/**",
                )
                .permitAll()
            authorization
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/stripe/webhook",
                    "/api/v1/workspaces",
                    "/api/v1/public/evses/*/*/checkout",
                    "/api/v1/public/sessions/*/stop",
                    "/api/v1/auth/signin",
                    "/api/v1/auth/signon",
                    "/api/v1/auth/password/reset/request",
                    "/api/v1/auth/password/reset",
                    "/api/v1/auth/mail/resend",
                    "/api/v1/auth/mail/check",
                )
                .permitAll()
            authorization.requestMatchers("/oauth2/**", "/login/oauth2/**", "/error").permitAll()
            authorization
                .requestMatchers("/api/v1/openapi/**", "/api/v1/docs/**", "/swagger-ui/**")
                .hasRole("ADMIN")
            authorization.requestMatchers("/api/v1/**").authenticated()
            authorization.anyRequest().denyAll()
        }
        http.exceptionHandling { exceptions ->
            exceptions.authenticationEntryPoint { _, response, _ ->
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
            }
            exceptions.accessDeniedHandler { _, response, _ ->
                response.sendError(HttpServletResponse.SC_FORBIDDEN)
            }
        }
        http.requestCache { cache -> cache.disable() }
        http.logout { logout -> logout.disable() }
        http.headers { headers ->
            headers.contentSecurityPolicy { policy ->
                policy.policyDirectives("default-src 'none'; frame-ancestors 'none'")
            }
            headers.frameOptions { frames -> frames.deny() }
        }

        if (providers.registrations.isNotEmpty()) {
            configureOAuth(http, providers, successHandler, publicUrl)
        }

        return http.build()
    }

    private fun configureOAuth(
        http: HttpSecurity,
        providers: ProviderRegistry,
        successHandler: OAuthSignInHandler,
        publicUrl: String,
    ) {
        val resolver = DefaultOAuth2AuthorizationRequestResolver(providers, "/oauth2/authorization")
        resolver.setAuthorizationRequestCustomizer { builder ->
            if (builder.build().authorizationUri.startsWith("https://appleid.apple.com/")) {
                builder.additionalParameters { parameters ->
                    parameters["response_mode"] = "form_post"
                }
            } else {
                OAuth2AuthorizationRequestCustomizers.withPkce().accept(builder)
            }
        }

        http.oauth2Login { oauth ->
            oauth.authorizationEndpoint { authorization ->
                authorization.authorizationRequestResolver(resolver)
            }
            oauth.successHandler(successHandler)
            oauth.failureHandler { request, response, _ ->
                request.getSession(false)?.invalidate()
                response.sendRedirect("${publicUrl.trimEnd('/')}/auth/login?error=provider")
            }
        }
    }
}
