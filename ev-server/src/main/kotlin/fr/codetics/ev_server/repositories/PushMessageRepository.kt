// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.PushMessageEntity
import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.Pageable

interface PushMessageRepository : StoreRepository<PushMessageEntity, UUID> {
    fun findAllBySentAtIsNullAndAttemptsLessThanOrderByCreatedAt(attempts: Int, pageable: Pageable): List<PushMessageEntity>

    fun deleteAllByCreatedAtBefore(before: Instant)

    fun deleteAllByUserId(userId: UUID)
}
