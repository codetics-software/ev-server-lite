// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.ev_server.repositories

import fr.codetics.ev_server.models.entities.StationPhotoContentEntity
import java.util.UUID

interface StationPhotoContentRepository : StoreRepository<StationPhotoContentEntity, UUID>
