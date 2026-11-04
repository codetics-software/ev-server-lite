// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.ev_server.services.notifications

import fr.codetics.ev_server.models.entities.PushDeviceEntity
import fr.codetics.ev_server.models.entities.PushMessageEntity

object PushPlatform {
    const val IOS = "IOS"
    const val ANDROID = "ANDROID"
}

enum class PushResult {
    DELIVERED,
    INVALID_DEVICE,
    RETRY,
}

interface PushGateway {
    val platform: String
        get() = PushPlatform.IOS

    val configured: Boolean

    fun send(device: PushDeviceEntity, message: PushMessageEntity): PushResult
}
