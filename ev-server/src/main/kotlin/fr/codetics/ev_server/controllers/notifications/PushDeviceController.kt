// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.ev_server.controllers.notifications

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.models.notifications.PushDeviceInput
import fr.codetics.ev_server.services.notifications.PushService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/account/devices")
@PreAuthorize("isAuthenticated()")
class PushDeviceController(private val push: PushService) {
    @PostMapping
    fun register(@RequestBody input: PushDeviceInput, @AuthenticationPrincipal user: AppPrincipal) =
        push.register(user, input)

    @DeleteMapping("/{token}")
    fun unregister(@PathVariable token: String, @AuthenticationPrincipal user: AppPrincipal) =
        push.unregister(user, token)
}
