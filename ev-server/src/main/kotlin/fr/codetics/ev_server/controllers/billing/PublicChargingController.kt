package fr.codetics.ev_server.controllers.billing

import fr.codetics.ev_server.models.billing.CheckoutRequest
import fr.codetics.ev_server.security.RateLimiter
import fr.codetics.ev_server.services.billing.ChargingCheckoutService
import fr.codetics.ev_server.services.billing.PortalSessionService
import fr.codetics.ev_server.services.evses.EvseService
import jakarta.servlet.http.HttpServletRequest
import java.util.UUID
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/public")
class PublicChargingController(
    private val evses: EvseService,
    private val checkout: ChargingCheckoutService,
    private val sessions: PortalSessionService,
    private val limiter: RateLimiter,
) {
    @GetMapping("/evses/{workspace}/{id}")
    fun evse(@PathVariable workspace: String, @PathVariable id: UUID) = evses.portal(workspace, id)

    @PostMapping("/evses/{workspace}/{id}/checkout")
    fun checkout(
        @PathVariable workspace: String,
        @PathVariable id: UUID,
        @RequestBody input: CheckoutRequest,
        request: HttpServletRequest,
    ): Any {
        limiter.check("public-checkout:${request.remoteAddr}", 10)
        return checkout.create(workspace, id, input)
    }

    @GetMapping("/sessions/{id}")
    fun session(@PathVariable id: UUID, @RequestHeader("X-Charge-Token") token: String) =
        sessions.read(id, token)

    @PostMapping("/sessions/{id}/stop")
    fun stop(@PathVariable id: UUID, @RequestHeader("X-Charge-Token") token: String) =
        sessions.stop(id, token)
}
