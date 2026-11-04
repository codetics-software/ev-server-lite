// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.annotation.StringRes
import fr.codetics.evapp.app.AppModel

class EvApp : Application() {
    lateinit var model: AppModel
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        model = AppModel(this)
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(
                    CHANNEL_CHARGING,
                    getString(R.string.charging),
                    NotificationManager.IMPORTANCE_HIGH,
                )
            )
    }

    companion object {
        const val CHANNEL_CHARGING = "charging"
        lateinit var instance: EvApp
            private set

        fun string(@StringRes id: Int, vararg args: Any): String = instance.getString(id, *args)
    }
}
