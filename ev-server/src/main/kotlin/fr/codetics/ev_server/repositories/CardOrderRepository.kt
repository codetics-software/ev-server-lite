// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.CardOrderEntity
import java.util.UUID
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface CardOrderRepository : StoreRepository<CardOrderEntity, UUID> {
    fun findByTenantIdAndRequestId(tenantId: UUID, requestId: UUID): CardOrderEntity?

    fun findByTenantIdAndId(tenantId: UUID, id: UUID): CardOrderEntity?

    fun findAllByTenantIdAndUserIdOrderByCreatedAtDesc(tenantId: UUID, userId: UUID): List<CardOrderEntity>

    fun findAllByTenantId(tenantId: UUID, pageable: Pageable): Page<CardOrderEntity>

    fun findAllByTenantIdAndState(tenantId: UUID, state: String, pageable: Pageable): Page<CardOrderEntity>
}
