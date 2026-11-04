// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp.demo

import android.graphics.Bitmap
import fr.codetics.evapp.core.AppConfiguration
import okhttp3.Interceptor

object DemoSupport {
    const val available = false
    const val EMAIL = ""
    const val PASSWORD = ""
    const val OWN_CARD = ""
    const val ORDERED_CARD = ""
    const val PORTAL_LINK = ""

    fun configuration(): AppConfiguration? = null

    fun interceptor(): Interceptor? = null

    fun stationPhoto(): Bitmap? = null
}
