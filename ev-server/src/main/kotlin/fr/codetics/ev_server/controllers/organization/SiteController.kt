package fr.codetics.ev_server.controllers.organization

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.models.people.SiteAssignmentInput
import fr.codetics.ev_server.services.people.UserService
import java.util.UUID
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/sites")
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
class SiteController(private val users: UserService) {
    @GetMapping("/{id}/users")
    fun users(@PathVariable id: UUID, @AuthenticationPrincipal user: AppPrincipal) =
        users.siteUsers(user, id)

    @PutMapping("/{id}/users")
    @PreAuthorize("hasRole('ADMIN')")
    fun assign(
        @PathVariable id: UUID,
        @RequestBody input: List<SiteAssignmentInput>,
        @AuthenticationPrincipal user: AppPrincipal,
    ) = users.assignUsers(user, id, input)
}
