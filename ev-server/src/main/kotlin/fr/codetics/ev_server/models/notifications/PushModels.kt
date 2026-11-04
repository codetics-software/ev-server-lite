// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.ev_server.models.notifications

data class PushDeviceInput(
    val token: String,
    val platform: String = "IOS",
    val environment: String = "production",
    val locale: String? = null,
)
