package fr.codetics.ev_server.controllers.workspaces

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.models.workspaces.WorkspaceCreation
import fr.codetics.ev_server.models.workspaces.WorkspaceUpdate
import fr.codetics.ev_server.security.AccountService
import fr.codetics.ev_server.services.workspaces.WorkspaceService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class WorkspaceController(
    private val workspaces: WorkspaceService,
    private val accounts: AccountService,
) {
    @GetMapping("/public/workspaces/options")
    fun options() = mapOf("registrationAvailable" to workspaces.registrationAvailable())

    @GetMapping("/workspace")
    @PreAuthorize("isAuthenticated()")
    fun current(@AuthenticationPrincipal user: AppPrincipal) = workspaces.current(user.tenantId)

    @PutMapping("/workspace")
    @PreAuthorize("hasRole('ADMIN')")
    fun update(@RequestBody input: WorkspaceUpdate, @AuthenticationPrincipal user: AppPrincipal) =
        workspaces.rename(user, input.name)

    @PostMapping("/workspaces")
    fun create(@RequestBody input: WorkspaceCreation, request: HttpServletRequest) =
        accounts.limit("workspace-signup:${request.remoteAddr}", 5).let { workspaces.create(input) }
}
