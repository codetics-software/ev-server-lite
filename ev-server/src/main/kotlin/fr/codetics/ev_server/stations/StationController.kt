package fr.codetics.ev_server.stations

import fr.codetics.ev_server.commands.CommandInput
import fr.codetics.ev_server.commands.CommandReceipt
import fr.codetics.ev_server.commands.CommandService
import fr.codetics.ev_server.core.AppPrincipal
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/stations")
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
class StationController(private val stations: StationService, private val commands: CommandService) {
    @GetMapping
    fun list(
        @AuthenticationPrincipal user: AppPrincipal,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "50") limit: Int
    ) = stations.list(user.tenantId, offset, limit)

    @GetMapping("/{id}")
    fun find(@PathVariable id: String, @AuthenticationPrincipal user: AppPrincipal) = stations.find(user.tenantId, id)

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun provision(@RequestBody input: StationInput, @AuthenticationPrincipal user: AppPrincipal) = stations.provision(user, input)

    @PostMapping("/{id}/commands")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    fun command(
        @PathVariable id: String,
        @RequestBody input: CommandInput,
        @AuthenticationPrincipal user: AppPrincipal
    ): CommandReceipt = commands.queue(user, id, input)
}
