package fr.codetics.ev_server.controllers.cars

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.services.cars.CarCatalogService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/car-catalog")
@PreAuthorize("hasRole('ADMIN')")
class CarCatalogController(private val catalog: CarCatalogService) {
    @PostMapping("/synchronize")
    fun synchronize(@AuthenticationPrincipal user: AppPrincipal) = catalog.synchronize(user)
}
