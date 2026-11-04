// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.PushDeviceEntity
import java.util.UUID

interface PushDeviceRepository : StoreRepository<PushDeviceEntity, String> {
    fun findAllByTenantIdAndUserId(tenantId: UUID, userId: UUID): List<PushDeviceEntity>

    fun existsByTenantIdAndUserId(tenantId: UUID, userId: UUID): Boolean

    fun deleteAllByUserId(userId: UUID)
}
