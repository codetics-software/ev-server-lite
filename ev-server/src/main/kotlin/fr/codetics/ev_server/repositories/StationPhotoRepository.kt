// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.StationPhotoEntity
import java.util.UUID

interface StationPhotoRepository : StoreRepository<StationPhotoEntity, UUID> {
    fun findAllByTenantIdAndStationIdOrderByCreatedAtDesc(tenantId: UUID, stationId: String): List<StationPhotoEntity>

    fun countByTenantIdAndStationId(tenantId: UUID, stationId: String): Long

    fun findAllByTenantIdAndUserId(tenantId: UUID, userId: UUID): List<StationPhotoEntity>
}
