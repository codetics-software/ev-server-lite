package fr.codetics.ev_server.controllers.people

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.models.api.RecordInput
import fr.codetics.ev_server.models.notifications.NotificationPreferencesInput
import fr.codetics.ev_server.models.people.ProfileInput
import fr.codetics.ev_server.services.people.AccountCars
import fr.codetics.ev_server.services.people.UserService
import fr.codetics.ev_server.services.tokens.ChargingTokenService
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
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/account")
@PreAuthorize("isAuthenticated()")
class AccountController(
    private val users: UserService,
    private val tokens: ChargingTokenService,
    private val cars: AccountCars,
) {
    @GetMapping fun profile(@AuthenticationPrincipal user: AppPrincipal) = users.find(user, user.id)

    @PutMapping
    fun update(@RequestBody input: ProfileInput, @AuthenticationPrincipal user: AppPrincipal) =
        users.updateProfile(user, input)

    @GetMapping("/notifications")
    fun preferences(@AuthenticationPrincipal user: AppPrincipal) =
        users.preferences(user.tenantId, user.id)

    @PutMapping("/notifications")
    fun updatePreferences(
        @RequestBody input: NotificationPreferencesInput,
        @AuthenticationPrincipal user: AppPrincipal,
    ) = users.updatePreferences(user.tenantId, user.id, input)

    @GetMapping("/tokens") fun tokens(@AuthenticationPrincipal user: AppPrincipal) = tokens.owned(user)

    @GetMapping("/sites")
    fun sites(@AuthenticationPrincipal user: AppPrincipal) = users.sites(user, user.id)

    @GetMapping("/cars") fun cars(@AuthenticationPrincipal user: AppPrincipal) = cars.list(user)

    @PostMapping("/cars")
    fun createCar(@RequestBody input: RecordInput, @AuthenticationPrincipal user: AppPrincipal) =
        cars.create(user, input)

    @PutMapping("/cars/{id}")
    fun updateCar(
        @PathVariable id: UUID,
        @RequestBody input: RecordInput,
        @AuthenticationPrincipal user: AppPrincipal,
    ) = cars.update(user, id, input)

    @DeleteMapping("/cars/{id}")
    fun deleteCar(@PathVariable id: UUID, @AuthenticationPrincipal user: AppPrincipal) =
        cars.delete(user, id)
}
