package fr.codetics.ev_server.controllers.tokens

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.models.tokens.TokenAssignment
import fr.codetics.ev_server.models.tokens.TokenInput
import fr.codetics.ev_server.services.tokens.ChargingTokenService
import java.util.UUID
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/tokens")
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
class ChargingTokenController(private val tokens: ChargingTokenService) {
    @GetMapping
    fun list(
        @AuthenticationPrincipal user: AppPrincipal,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "") search: String,
        @RequestParam(required = false) userId: UUID?,
    ) = tokens.list(user, offset, limit, search, userId)

    @GetMapping("/export")
    fun export(@AuthenticationPrincipal user: AppPrincipal) = tokens.export(user)

    @PostMapping("/import", consumes = ["text/csv", "text/plain"])
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    fun import(@RequestBody content: String, @AuthenticationPrincipal user: AppPrincipal) =
        tokens.import(user, content)

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    fun save(@RequestBody input: TokenInput, @AuthenticationPrincipal user: AppPrincipal) =
        tokens.save(input, user)

    @DeleteMapping("/{uid}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    fun delete(@PathVariable uid: String, @AuthenticationPrincipal user: AppPrincipal) =
        tokens.delete(user, uid)

    @PostMapping("/{uid}/assign")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    fun assign(
        @PathVariable uid: String,
        @RequestBody input: TokenAssignment,
        @AuthenticationPrincipal user: AppPrincipal,
    ) = tokens.assign(user, uid, input.userId)

    @PostMapping("/{uid}/unassign")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    fun unassign(@PathVariable uid: String, @AuthenticationPrincipal user: AppPrincipal) =
        tokens.unassign(user, uid)
}
