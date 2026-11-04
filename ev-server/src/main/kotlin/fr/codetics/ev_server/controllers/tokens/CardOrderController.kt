// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.ev_server.controllers.tokens

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.models.tokens.CardActivationInput
import fr.codetics.ev_server.models.tokens.CardAssignmentInput
import fr.codetics.ev_server.models.tokens.CardOrderInput
import fr.codetics.ev_server.models.tokens.CardSaleInput
import fr.codetics.ev_server.services.tokens.CardOrderService
import java.util.UUID
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@PreAuthorize("isAuthenticated()")
class CardOrderController(private val cards: CardOrderService) {
    @GetMapping("/api/v1/account/card-offer")
    fun offer(@AuthenticationPrincipal user: AppPrincipal) = cards.offer(user)

    @GetMapping("/api/v1/account/card-orders")
    fun own(@AuthenticationPrincipal user: AppPrincipal) = cards.own(user)

    @PostMapping("/api/v1/account/card-orders")
    fun create(@RequestBody input: CardOrderInput, @AuthenticationPrincipal user: AppPrincipal) =
        cards.create(user, input)

    @PostMapping("/api/v1/account/card-orders/activate")
    fun activate(@RequestBody input: CardActivationInput, @AuthenticationPrincipal user: AppPrincipal) =
        cards.activate(user, input)

    @GetMapping("/api/v1/card-orders")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    fun list(
        @AuthenticationPrincipal user: AppPrincipal,
        @RequestParam(required = false) state: String?,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "50") limit: Int,
    ) = cards.list(user, state, offset, limit)

    @PutMapping("/api/v1/card-orders/settings")
    @PreAuthorize("hasRole('ADMIN')")
    fun settings(@RequestBody input: CardSaleInput, @AuthenticationPrincipal user: AppPrincipal) =
        cards.updateSale(user, input)

    @PostMapping("/api/v1/card-orders/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    fun assign(
        @PathVariable id: UUID,
        @RequestBody input: CardAssignmentInput,
        @AuthenticationPrincipal user: AppPrincipal,
    ) = cards.assign(user, id, input)

    @PostMapping("/api/v1/card-orders/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    fun cancel(@PathVariable id: UUID, @AuthenticationPrincipal user: AppPrincipal) = cards.cancel(user, id)
}
