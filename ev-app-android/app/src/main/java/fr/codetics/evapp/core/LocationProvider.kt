// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp.core

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

class LocationProvider(private val context: Context) {
    var location by mutableStateOf<Location?>(null)
        private set

    private var started = false
    private val manager = context.getSystemService(LocationManager::class.java)
    private val listener = LocationListener { location = it }

    val hasPermission: Boolean
        get() =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start() {
        if (started || !hasPermission) return
        started = true
        for (provider in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)) {
            if (!manager.allProviders.contains(provider)) continue
            manager.getLastKnownLocation(provider)?.let { if (location == null) location = it }
            manager.requestLocationUpdates(provider, UPDATE_MILLIS, UPDATE_METERS, listener, Looper.getMainLooper())
        }
    }

    fun stop() {
        if (!started) return
        started = false
        manager.removeUpdates(listener)
    }

    private companion object {
        const val UPDATE_MILLIS = 15_000L
        const val UPDATE_METERS = 50f
    }
}
