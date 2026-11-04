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
@Table(name = "push_outbox")
@Document("push_outbox")
class PushMessageEntity(
    @Id var id: UUID = UUID.randomUUID(),
    var tenantId: UUID = UUID.randomUUID(),
    var userId: UUID = UUID.randomUUID(),
    var type: String = "",
    var title: String = "",
    var body: String = "",
    var transactionId: Int? = null,
    var attempts: Int = 0,
    var createdAt: Instant = Instant.now(),
    var sentAt: Instant? = null,
)
