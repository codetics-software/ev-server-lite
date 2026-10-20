package fr.codetics.ev_server.controllers.workspaces

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.models.workspaces.PlatformWorkspaceInput
import fr.codetics.ev_server.models.workspaces.WorkspaceComponentsInput
import fr.codetics.ev_server.models.workspaces.WorkspaceCreation
import fr.codetics.ev_server.models.workspaces.WorkspaceUpdate
import fr.codetics.ev_server.security.AccountService
import fr.codetics.ev_server.services.workspaces.PlatformService
import fr.codetics.ev_server.services.workspaces.WorkspaceService
import jakarta.servlet.http.HttpServletRequest
import java.time.Duration
import java.util.UUID
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class WorkspaceController(
    private val workspaces: WorkspaceService,
    private val platform: PlatformService,
    private val accounts: AccountService,
) {
    @GetMapping("/public/workspaces/options")
    fun options() = mapOf("registrationAvailable" to workspaces.registrationAvailable())

    @GetMapping("/public/workspaces/{slug}/logo")
    fun logo(@PathVariable slug: String): ResponseEntity<ByteArray> {
        val logo = workspaces.logo(slug) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(logo.contentType))
            .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
            .header("X-Content-Type-Options", "nosniff")
            .body(logo.content)
    }

    @GetMapping("/workspace")
    @PreAuthorize("isAuthenticated()")
    fun current(@AuthenticationPrincipal user: AppPrincipal) = workspaces.current(user.tenantId)

    @PutMapping("/workspace")
    @PreAuthorize("hasRole('ADMIN')")
    fun update(@RequestBody input: WorkspaceUpdate, @AuthenticationPrincipal user: AppPrincipal) =
        workspaces.rename(user, input.name)

    @PutMapping("/workspace/components")
    @PreAuthorize("hasRole('ADMIN')")
    fun components(
        @RequestBody input: WorkspaceComponentsInput,
        @AuthenticationPrincipal user: AppPrincipal,
    ) = workspaces.updateComponents(user, input)

    @PutMapping("/workspace/logo", consumes = ["image/png", "image/jpeg", "image/webp"])
    @PreAuthorize("hasRole('ADMIN')")
    fun uploadLogo(
        @RequestHeader("Content-Type") contentType: String,
        @RequestBody content: ByteArray,
        @AuthenticationPrincipal user: AppPrincipal,
    ) = workspaces.uploadLogo(user, contentType, content)

    @DeleteMapping("/workspace/logo")
    @PreAuthorize("hasRole('ADMIN')")
    fun deleteLogo(@AuthenticationPrincipal user: AppPrincipal) = workspaces.deleteLogo(user)

    @PostMapping("/workspaces")
    fun create(@RequestBody input: WorkspaceCreation, request: HttpServletRequest) =
        accounts.limit("workspace-signup:${request.remoteAddr}", 5).let { workspaces.create(input) }

    @GetMapping("/platform/workspaces")
    @PreAuthorize("hasRole('ADMIN')")
    fun platformWorkspaces(
        @AuthenticationPrincipal user: AppPrincipal,
        @RequestParam(defaultValue = "") search: String,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "50") limit: Int,
    ) = platform.list(user, search, offset, limit)

    @PutMapping("/platform/workspaces/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun updatePlatformWorkspace(
        @PathVariable id: UUID,
        @RequestBody input: PlatformWorkspaceInput,
        @AuthenticationPrincipal user: AppPrincipal,
    ) = platform.update(user, id, input)
}
