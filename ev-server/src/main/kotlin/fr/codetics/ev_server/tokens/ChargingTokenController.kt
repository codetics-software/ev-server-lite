package fr.codetics.ev_server.tokens

import fr.codetics.ev_server.core.AppPrincipal
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/tokens")
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
class ChargingTokenController(private val tokens: ChargingTokenService) {
    @GetMapping
    fun list(@AuthenticationPrincipal user: AppPrincipal) = tokens.list(user)

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    fun save(@RequestBody input: TokenInput, @AuthenticationPrincipal user: AppPrincipal) = tokens.save(input, user)
}
