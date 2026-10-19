package fr.codetics.ev_server.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class OAuthSignInHandler(
    private val accounts: AccountService,
    @param:Value("\${app.cookie-secure}") private val secure: Boolean,
    @param:Value("\${app.public-url}") private val publicUrl: String
) : AuthenticationSuccessHandler {
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        try {
            val identity = authentication.principal as OidcUser
            val linkedUser = linkingAccount(request)
            val user = accounts.oauth(
                issuer = identity.issuer.toString(),
                subject = requireNotNull(identity.subject),
                email = identity.email,
                name = identity.fullName,
                verified = identity.claims["email_verified"]?.toString() == "true",
                linkUser = linkedUser
            )

            request.getSession(false)?.invalidate()
            SecurityContextHolder.clearContext()
            response.addHeader("Set-Cookie", sessionCookie(accounts.issue(user), secure).toString())
            response.sendRedirect("${publicUrl.trimEnd('/')}/")
        } catch (_: Exception) {
            request.getSession(false)?.invalidate()
            SecurityContextHolder.clearContext()
            response.sendRedirect("${publicUrl.trimEnd('/')}/auth/login?error=provider")
        }
    }

    private fun linkingAccount(request: HttpServletRequest): UUID? {
        val session = request.getSession(false) ?: return null
        val user = session.getAttribute("LINK_USER") as? UUID ?: return null
        val tokenHash = session.getAttribute("LINK_TOKEN") as? String

        require(tokenHash != null && accounts.authenticateTokenHash(tokenHash)?.id == user) {
            "The original account session is no longer valid"
        }

        return user
    }
}
