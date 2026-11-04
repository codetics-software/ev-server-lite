// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.ev_server.models.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID
import org.springframework.data.mongodb.core.mapping.Document

@Entity
@Table(name = "station_photo_contents")
@Document("station_photo_contents")
class StationPhotoContentEntity(
    @Id var photoId: UUID = UUID.randomUUID(),
    @Column(columnDefinition = "bytea") var content: ByteArray = byteArrayOf(),
)
