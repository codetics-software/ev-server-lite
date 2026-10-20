package fr.codetics.ev_server.controllers.stations

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.models.commands.CommandInput
import fr.codetics.ev_server.models.stations.AutoChargeSetting
import fr.codetics.ev_server.models.commands.CommandReceipt
import fr.codetics.ev_server.models.stations.ParameterInput
import fr.codetics.ev_server.models.stations.StationInput
import fr.codetics.ev_server.models.stations.StationUpdate
import fr.codetics.ev_server.services.commands.CommandService
import fr.codetics.ev_server.services.stations.StationFilter
import fr.codetics.ev_server.services.stations.StationService
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
@RequestMapping("/api/v1/stations")
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
class StationController(
    private val stations: StationService,
    private val commands: CommandService,
) {
    @GetMapping
    fun list(
        @AuthenticationPrincipal user: AppPrincipal,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "") search: String,
        @RequestParam(required = false) siteId: UUID?,
        @RequestParam(required = false) siteAreaId: UUID?,
        @RequestParam(required = false) state: String?,
    ) = stations.list(user.tenantId, offset, limit, StationFilter(search, siteId, siteAreaId, state))

    @GetMapping("/in-error")
    fun inError(@AuthenticationPrincipal user: AppPrincipal) = stations.inError(user.tenantId)

    @GetMapping("/export")
    fun export(@AuthenticationPrincipal user: AppPrincipal) = stations.exportStations(user.tenantId)

    @GetMapping("/parameters/export")
    fun exportParameters(
        @AuthenticationPrincipal user: AppPrincipal,
        @RequestParam(required = false) stationId: String?,
    ) = stations.exportParameters(user.tenantId, stationId)

    @GetMapping("/{id}")
    fun find(@PathVariable id: String, @AuthenticationPrincipal user: AppPrincipal) =
        stations.find(user.tenantId, id)

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    fun provision(@RequestBody input: StationInput, @AuthenticationPrincipal user: AppPrincipal) =
        stations.provision(user, input)

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    fun update(
        @PathVariable id: String,
        @RequestBody input: StationUpdate,
        @AuthenticationPrincipal user: AppPrincipal,
    ) = stations.update(user, id, input)

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    fun delete(@PathVariable id: String, @AuthenticationPrincipal user: AppPrincipal) =
        stations.delete(user, id)

    @PostMapping("/{id}/secret")
    @PreAuthorize("hasRole('ADMIN')")
    fun rotateSecret(@PathVariable id: String, @AuthenticationPrincipal user: AppPrincipal) =
        stations.rotateSecret(user, id)

    @PostMapping("/{id}/commands")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    fun command(
        @PathVariable id: String,
        @RequestBody input: CommandInput,
        @AuthenticationPrincipal user: AppPrincipal,
    ): CommandReceipt = commands.queue(user, id, input)

    @GetMapping("/{id}/events")
    fun events(
        @PathVariable id: String,
        @AuthenticationPrincipal user: AppPrincipal,
        @RequestParam(required = false) type: String?,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "50") limit: Int,
    ) = stations.events(user.tenantId, id, type, offset, limit)

    @GetMapping("/{id}/parameters")
    fun parameters(@PathVariable id: String, @AuthenticationPrincipal user: AppPrincipal) =
        stations.parameters(user.tenantId, id)

    @PostMapping("/{id}/parameters/refresh")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    fun refreshParameters(@PathVariable id: String, @AuthenticationPrincipal user: AppPrincipal) =
        stations.refreshParameters(user, id)

    @PutMapping("/{id}/parameters")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    fun changeParameter(
        @PathVariable id: String,
        @RequestBody input: ParameterInput,
        @AuthenticationPrincipal user: AppPrincipal,
    ) = stations.changeParameter(user, id, input)

    @PutMapping("/{id}/autocharge")
    @PreAuthorize("hasRole('ADMIN')")
    fun autoCharge(
        @PathVariable id: String,
        @RequestBody input: AutoChargeSetting,
        @AuthenticationPrincipal user: AppPrincipal,
    ) = stations.autoCharge(user, id, input)

    @PostMapping("/{id}/template/apply")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    fun applyTemplate(@PathVariable id: String, @AuthenticationPrincipal user: AppPrincipal) =
        stations.applyTemplate(user, id)
}
