package fr.codetics.ev_server.controllers.people

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.models.notifications.NotificationPreferencesInput
import fr.codetics.ev_server.models.people.SiteAssignmentInput
import fr.codetics.ev_server.models.people.UserInput
import fr.codetics.ev_server.models.people.UserRole
import fr.codetics.ev_server.services.people.UserFilter
import fr.codetics.ev_server.services.people.UserService
import java.util.UUID
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
class UserController(private val users: UserService) {
    @GetMapping
    fun list(
        @AuthenticationPrincipal user: AppPrincipal,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "") search: String,
        @RequestParam(required = false) role: UserRole?,
        @RequestParam(required = false) state: String?,
    ) = users.list(user, offset, limit, UserFilter(search, role, state))

    @GetMapping("/in-error")
    fun inError(@AuthenticationPrincipal user: AppPrincipal) = users.inError(user)

    @GetMapping("/export")
    fun export(@AuthenticationPrincipal user: AppPrincipal) = users.export(user)

    @PostMapping("/import", consumes = ["text/csv", "text/plain"])
    fun import(@RequestBody content: String, @AuthenticationPrincipal user: AppPrincipal) =
        users.import(user, content)

    @GetMapping("/{id}")
    fun find(@PathVariable id: UUID, @AuthenticationPrincipal user: AppPrincipal) = users.find(user, id)

    @PostMapping
    fun create(@RequestBody input: UserInput, @AuthenticationPrincipal user: AppPrincipal) =
        users.create(input, user)

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @RequestBody input: UserInput,
        @AuthenticationPrincipal user: AppPrincipal,
    ) = users.update(id, input, user)

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID, @AuthenticationPrincipal user: AppPrincipal) =
        users.delete(id, user)

    @GetMapping("/{id}/sites")
    fun sites(@PathVariable id: UUID, @AuthenticationPrincipal user: AppPrincipal) =
        users.sites(user, id)

    @PutMapping("/{id}/sites")
    fun assignSites(
        @PathVariable id: UUID,
        @RequestBody input: List<SiteAssignmentInput>,
        @AuthenticationPrincipal user: AppPrincipal,
    ) = users.assignSites(user, id, input)

    @GetMapping("/{id}/notifications")
    fun preferences(@PathVariable id: UUID, @AuthenticationPrincipal user: AppPrincipal) =
        users.preferences(user.tenantId, id)

    @PutMapping("/{id}/notifications")
    fun updatePreferences(
        @PathVariable id: UUID,
        @RequestBody input: NotificationPreferencesInput,
        @AuthenticationPrincipal user: AppPrincipal,
    ) = users.updatePreferences(user.tenantId, id, input)
}
