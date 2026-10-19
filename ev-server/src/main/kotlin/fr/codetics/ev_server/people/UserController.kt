package fr.codetics.ev_server.people

import fr.codetics.ev_server.core.AppPrincipal
import java.util.UUID
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
class UserController(private val users: UserService) {
    @GetMapping
    fun list(@AuthenticationPrincipal user: AppPrincipal) = users.list(user)

    @PostMapping
    fun create(@RequestBody input: UserInput, @AuthenticationPrincipal user: AppPrincipal) = users.create(input, user)

    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody input: UserInput, @AuthenticationPrincipal user: AppPrincipal) =
        users.update(id, input, user)
}
