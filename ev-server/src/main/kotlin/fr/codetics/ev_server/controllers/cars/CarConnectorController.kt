package fr.codetics.ev_server.controllers.cars

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.services.cars.CarConnectorSecretInput
import fr.codetics.ev_server.services.cars.CarConnectorService
import java.util.UUID
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class CarConnectorController(private val connectors: CarConnectorService) {
    @PutMapping("/car-connectors/{id}/secret")
    @PreAuthorize("hasRole('ADMIN')")
    fun secret(
        @PathVariable id: UUID,
        @RequestBody input: CarConnectorSecretInput,
        @AuthenticationPrincipal user: AppPrincipal,
    ) = connectors.updateSecret(user, id, input)

    @PostMapping("/car-connectors/{id}/check")
    @PreAuthorize("hasRole('ADMIN')")
    fun check(@PathVariable id: UUID, @AuthenticationPrincipal user: AppPrincipal) = connectors.check(user, id)

    @PostMapping("/cars/{id}/soc")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    fun soc(@PathVariable id: UUID, @AuthenticationPrincipal user: AppPrincipal) = connectors.soc(user, id)
}
