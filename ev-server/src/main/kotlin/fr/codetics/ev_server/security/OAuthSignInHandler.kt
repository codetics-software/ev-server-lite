package fr.codetics.ev_server.security

import fr.codetics.ev_server.core.AppProperties
import fr.codetics.ev_server.core.ensure
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.UUID
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class OAuthSignInHandler(
    private val accounts: AccountService,
    private val properties: AppProperties,
) : AuthenticationSuccessHandler {
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        try {
            val identity = authentication.principal as OidcUser
            val linkedUser = linkingAccount(request)
            val user =
                accounts.oauth(
                    issuer = identity.issuer.toString(),
                    subject = requireNotNull(identity.subject),
                    email = identity.email,
                    name = identity.fullName,
                    verified = identity.claims["email_verified"]?.toString() == "true",
                    linkUser = linkedUser,
                )

            request.getSession(false)?.invalidate()
            SecurityContextHolder.clearContext()
            val cookie = sessionCookie(accounts.issue(user), properties.cookieSecure)
        response.addHeader("Set-Cookie", cookie.toString())
            response.sendRedirect("${properties.publicUrl.trimEnd('/')}/")
        } catch (_: Exception) {
            request.getSession(false)?.invalidate()
            SecurityContextHolder.clearContext()
            response.sendRedirect("${properties.publicUrl.trimEnd('/')}/auth/login?error=provider")
        }
    }

    private fun linkingAccount(request: HttpServletRequest): UUID? {
        val session = request.getSession(false) ?: return null
        val user = session.getAttribute("LINK_USER") as? UUID ?: return null
        val tokenHash = session.getAttribute("LINK_TOKEN") as? String

        ensure(tokenHash != null && accounts.authenticateTokenHash(tokenHash)?.id == user) {
            "La session d’origine du compte n’est plus valide."
        }

        return user
    }
}
