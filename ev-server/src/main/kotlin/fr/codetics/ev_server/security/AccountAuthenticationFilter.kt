package fr.codetics.ev_server.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

fun requestToken(request: HttpServletRequest): String? {
    val authorization = request.getHeader("Authorization")

    if (authorization != null && authorization.startsWith("Bearer ")) {
        return authorization.removePrefix("Bearer ")
    }

    return request.cookies?.find { cookie -> cookie.name == "EV_SESSION" }?.value
}

internal class AccountAuthenticationFilter(private val accounts: AccountService) :
    OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val user = requestToken(request)?.let(accounts::authenticateToken)

        if (user != null) {
            val authorities = listOf(SimpleGrantedAuthority("ROLE_${user.role.name}"))
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(user, null, authorities)
        }

        chain.doFilter(request, response)
    }
}
