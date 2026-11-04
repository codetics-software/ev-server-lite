// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.ev_server.models.entities

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.springframework.data.mongodb.core.mapping.Document

@Entity
@Table(name = "push_devices")
@Document("push_devices")
class PushDeviceEntity(
    @Id var token: String = "",
    var tenantId: UUID = UUID.randomUUID(),
    var userId: UUID = UUID.randomUUID(),
    var platform: String = "IOS",
    var environment: String = "production",
    var locale: String? = null,
    var createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now(),
)
