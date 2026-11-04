// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.ev_server.controllers.stations

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.services.stations.CustomerStationService
import fr.codetics.ev_server.services.stations.StationPhotoService
import java.time.Duration
import java.util.UUID
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/account/stations")
@PreAuthorize("isAuthenticated()")
class CustomerStationController(
    private val stations: CustomerStationService,
    private val photos: StationPhotoService,
) {
    @GetMapping
    fun list(@AuthenticationPrincipal user: AppPrincipal, @RequestParam(defaultValue = "") search: String) =
        stations.list(user, search)

    @GetMapping("/{id}")
    fun find(@PathVariable id: String, @AuthenticationPrincipal user: AppPrincipal) = stations.find(user, id)

    @GetMapping("/{id}/photos")
    fun photos(@PathVariable id: String, @AuthenticationPrincipal user: AppPrincipal) = photos.list(user, id)

    @PostMapping("/{id}/photos", consumes = ["image/jpeg", "image/png", "image/webp"])
    fun upload(
        @PathVariable id: String,
        @RequestHeader("Content-Type") contentType: String,
        @RequestBody content: ByteArray,
        @AuthenticationPrincipal user: AppPrincipal,
    ) = photos.upload(user, id, contentType, content)

    @GetMapping("/{id}/photos/{photoId}")
    fun photo(
        @PathVariable id: String,
        @PathVariable photoId: UUID,
        @AuthenticationPrincipal user: AppPrincipal,
    ): ResponseEntity<ByteArray> {
        val photo = photos.content(user, id, photoId)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(photo.contentType))
            .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
            .header("X-Content-Type-Options", "nosniff")
            .body(photo.content)
    }

    @DeleteMapping("/{id}/photos/{photoId}")
    fun delete(
        @PathVariable id: String,
        @PathVariable photoId: UUID,
        @AuthenticationPrincipal user: AppPrincipal,
    ) = photos.delete(user, id, photoId)
}
