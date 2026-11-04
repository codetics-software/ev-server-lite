// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.ev_server.controllers.stations

import fr.codetics.ev_server.services.stations.CustomerStationService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/public/workspaces/{slug}/stations")
class PublicStationController(private val stations: CustomerStationService) {
    @GetMapping
    fun list(@PathVariable slug: String, @RequestParam(defaultValue = "") search: String) =
        stations.publicList(slug, search)

    @GetMapping("/{id}")
    fun find(@PathVariable slug: String, @PathVariable id: String) = stations.publicFind(slug, id)
}
