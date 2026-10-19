package fr.codetics.ev_server.security

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.sha256
import fr.codetics.ev_server.persistence.UserRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class LoginRequest(
    val tenant: String,
    val email: String,
    val password: String
)

data class SignupRequest(
    val email: String,
    val name: String,
    val password: String
)

data class ChallengeRequest(
    val tenant: String,
    val email: String
)

data class CompleteRequest(
    val token: String,
    val password: String? = null
)

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val accounts: AccountService,
    private val providers: ProviderRegistry,
    private val db: Database,
    private val users: UserRepository,
    @param:Value("\${app.cookie-secure}") private val secure: Boolean
) {
    @GetMapping("/csrf")
    fun csrf(token: CsrfToken): Map<String, String> {
        return mapOf(
            "token" to token.token,
            "headerName" to token.headerName
        )
    }

    @GetMapping("/providers")
    fun providers(): List<Map<String, String>> {
        return providers.registrations.map { provider ->
            mapOf(
                "id" to provider.registrationId,
                "name" to provider.clientName,
                "url" to "/oauth2/authorization/${provider.registrationId}"
            )
        }
    }

    @PostMapping("/signin")
    fun login(
        @RequestBody body: LoginRequest,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): AppPrincipal {
        val user = accounts.login(body.tenant, body.email, body.password, request.remoteAddr)
        requestToken(request)?.let(accounts::revoke)
        request.getSession(false)?.invalidate()
        response.addHeader("Set-Cookie", sessionCookie(accounts.issue(user), secure).toString())
        return user
    }

    @PostMapping("/signon")
    fun signup(@RequestBody body: SignupRequest, request: HttpServletRequest): Map<String, String> {
        accounts.limit("signup:${request.remoteAddr}", 5)
        accounts.register(body.email, body.name, body.password)
        return mapOf(
            "message" to "If registration is available for this email, a verification link will be sent"
        )
    }

    @PostMapping("/signout")
    fun logout(request: HttpServletRequest, response: HttpServletResponse): Map<String, Boolean> {
        requestToken(request)?.let(accounts::revoke)
        request.getSession(false)?.invalidate()
        response.addHeader("Set-Cookie", sessionCookie("", secure, true).toString())
        return mapOf("success" to true)
    }

    @PostMapping("/signout/all")
    fun logoutAll(
        @AuthenticationPrincipal user: AppPrincipal,
        response: HttpServletResponse
    ): Map<String, Boolean> {
        accounts.revokeAll(user.id)
        response.addHeader("Set-Cookie", sessionCookie("", secure, true).toString())
        return mapOf("success" to true)
    }

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal user: AppPrincipal): AppPrincipal {
        return user
    }

    @PostMapping("/password/reset/request")
    fun resetRequest(@RequestBody body: ChallengeRequest, request: HttpServletRequest): Map<String, Boolean> {
        accounts.limit("reset:${request.remoteAddr}", 20)
        accounts.requestChallenge(body.tenant, body.email, "RESET")
        return mapOf("accepted" to true)
    }

    @PostMapping("/password/reset")
    fun reset(@RequestBody body: CompleteRequest): Map<String, Boolean> {
        val success = accounts.completeChallenge(body.token, "RESET", body.password)
        return mapOf("success" to success)
    }

    @PostMapping("/mail/resend")
    fun resend(@RequestBody body: ChallengeRequest, request: HttpServletRequest): Map<String, Boolean> {
        accounts.limit("verify:${request.remoteAddr}", 20)
        accounts.requestChallenge(body.tenant, body.email, "VERIFY")
        return mapOf("accepted" to true)
    }

    @PostMapping("/mail/check")
    fun verify(@RequestBody body: CompleteRequest): Map<String, Boolean> {
        return mapOf("success" to accounts.completeChallenge(body.token, "VERIFY"))
    }

    @PostMapping("/link/{provider}")
    fun link(
        @PathVariable provider: String,
        @AuthenticationPrincipal user: AppPrincipal,
        request: HttpServletRequest
    ): Map<String, String> {
        require(providers.findByRegistrationId(provider) != null) {
            "Provider is not configured"
        }

        if (request.getSession(false) != null) {
            request.changeSessionId()
        }

        val session = request.getSession(true)
        session.setAttribute("LINK_USER", user.id)
        session.setAttribute("LINK_TOKEN", sha256(requireNotNull(requestToken(request))))
        return mapOf("url" to "/oauth2/authorization/$provider")
    }

    @GetMapping("/eula")
    fun eula(): Map<String, String> {
        return mapOf(
            "version" to "1.0",
            "attribution" to "EV Supervision Lite — Kevin Vavelin, Codetics",
            "url" to "https://codetics.fr"
        )
    }

    @PostMapping("/eula/accept")
    fun accept(@AuthenticationPrincipal user: AppPrincipal): Map<String, Boolean> {
        return db.atomic {
            val account = users.findById(user.id).orElseThrow()
            account.eulaVersion = "1.0"
            users.save(account)
            db.audit(user.tenantId, user.id.toString(), "EULA_ACCEPT", "1.0")
            mapOf("success" to true)
        }
    }
}
