// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.ev_server.services.stations

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.core.Database
import fr.codetics.ev_server.core.NotFound
import fr.codetics.ev_server.core.ensure
import fr.codetics.ev_server.models.api.MutationResponse
import fr.codetics.ev_server.models.entities.StationPhotoContentEntity
import fr.codetics.ev_server.models.entities.StationPhotoEntity
import fr.codetics.ev_server.models.people.UserRole
import fr.codetics.ev_server.models.stations.StationPhotoResponse
import fr.codetics.ev_server.repositories.StationPhotoContentRepository
import fr.codetics.ev_server.repositories.StationPhotoRepository
import fr.codetics.ev_server.security.RateLimiter
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

data class StationPhotoContent(val contentType: String, val content: ByteArray)

@Service
class StationPhotoService(
    private val db: Database,
    private val photos: StationPhotoRepository,
    private val contents: StationPhotoContentRepository,
    private val stations: CustomerStationService,
    private val limiter: RateLimiter,
) {
    fun list(user: AppPrincipal, stationId: String): List<StationPhotoResponse> {
        stations.requireVisible(user, stationId)
        return photos.findAllByTenantIdAndStationIdOrderByCreatedAtDesc(user.tenantId, stationId).map { response(user, it) }
    }

    fun upload(user: AppPrincipal, stationId: String, contentType: String, content: ByteArray): StationPhotoResponse {
        stations.requireVisible(user, stationId)
        val type = contentType.substringBefore(';').trim().lowercase()
        ensure(type in TYPES) { "La photo doit être une image JPEG, PNG ou WebP." }
        ensure(content.size in 1..SIZE_LIMIT) { "La photo doit peser au plus 5 Mo." }
        ensure(matches(type, content)) { "Le contenu du fichier ne correspond pas à son format." }
        limiter.check("station-photo:${user.id}", UPLOADS_PER_WINDOW)
        return db.atomic {
            ensure(photos.countByTenantIdAndStationId(user.tenantId, stationId) < MAXIMUM_PER_STATION) {
                "Cette borne a atteint le nombre maximal de photos."
            }
            val photo =
                photos.save(
                    StationPhotoEntity(
                        tenantId = user.tenantId,
                        stationId = stationId,
                        userId = user.id,
                        contentType = type,
                    )
                )
            contents.save(StationPhotoContentEntity(photo.id, content))
            db.audit(user.tenantId, user.id.toString(), "STATION_PHOTO_UPLOAD", "$stationId/${photo.id}")
            response(user, photo)
        }
    }

    fun content(user: AppPrincipal, stationId: String, photoId: UUID): StationPhotoContent {
        stations.requireVisible(user, stationId)
        val photo = photo(user, stationId, photoId)
        val content = contents.findById(photo.id).orElse(null) ?: throw NotFound("Photo introuvable.")
        return StationPhotoContent(photo.contentType, content.content)
    }

    fun delete(user: AppPrincipal, stationId: String, photoId: UUID): MutationResponse =
        db.atomic {
            val photo = photo(user, stationId, photoId)
            if (photo.userId != user.id && user.role !in setOf(UserRole.ADMIN, UserRole.OPERATOR)) {
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "Vos droits ne permettent pas cette action.")
            }
            remove(photo)
            db.audit(user.tenantId, user.id.toString(), "STATION_PHOTO_DELETE", "$stationId/$photoId")
            MutationResponse()
        }

    fun deleteAllOf(tenant: UUID, user: UUID) {
        photos.findAllByTenantIdAndUserId(tenant, user).forEach(::remove)
    }

    private fun remove(photo: StationPhotoEntity) {
        contents.deleteById(photo.id)
        photos.delete(photo)
    }

    private fun photo(user: AppPrincipal, stationId: String, photoId: UUID) =
        photos.findById(photoId).orElse(null)?.takeIf { it.tenantId == user.tenantId && it.stationId == stationId }
            ?: throw NotFound("Photo introuvable.")

    private fun response(user: AppPrincipal, photo: StationPhotoEntity) =
        StationPhotoResponse(
            photo.id,
            photo.stationId,
            photo.contentType,
            photo.createdAt,
            photo.userId == user.id,
            "/api/v1/account/stations/${photo.stationId}/photos/${photo.id}",
        )

    private fun matches(type: String, content: ByteArray): Boolean {
        fun starts(vararg bytes: Int) =
            content.size >= bytes.size && bytes.indices.all { content[it] == bytes[it].toByte() }
        return when (type) {
            "image/png" -> starts(0x89, 0x50, 0x4E, 0x47)
            "image/jpeg" -> starts(0xFF, 0xD8, 0xFF)
            "image/webp" ->
                starts(0x52, 0x49, 0x46, 0x46) &&
                    content.size > 12 &&
                    String(content, 8, 4, Charsets.US_ASCII) == "WEBP"
            else -> false
        }
    }

    private companion object {
        val TYPES = setOf("image/jpeg", "image/png", "image/webp")
        const val SIZE_LIMIT = 5 * 1024 * 1024
        const val MAXIMUM_PER_STATION = 30L
        const val UPLOADS_PER_WINDOW = 20
    }
}
