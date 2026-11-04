// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp.push

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import fr.codetics.evapp.EvApp
import fr.codetics.evapp.MainActivity
import fr.codetics.evapp.R
import kotlinx.coroutines.launch

class EvMessagingService : FirebaseMessagingService() {
    private val model
        get() = (application as EvApp).model

    override fun onNewToken(token: String) {
        model.scope.launch { model.push.deviceRegistered(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        model.scope.launch { model.charging.refresh() }
        val notification = message.notification ?: return
        val transaction = message.data["transaction_id"]
        val intent =
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .apply { transaction?.let { putExtra("transaction_id", it) } }
        val pending =
            PendingIntent.getActivity(
                this,
                transaction?.toIntOrNull() ?: 0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val built =
            NotificationCompat.Builder(this, EvApp.CHANNEL_CHARGING)
                .setSmallIcon(R.drawable.sym_ev_station_fill)
                .setContentTitle(notification.title)
                .setContentText(notification.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
                .setAutoCancel(true)
                .setContentIntent(pending)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
        val manager = NotificationManagerCompat.from(this)
        if (manager.areNotificationsEnabled()) {
            runCatching { manager.notify(notification.tag ?: message.data["type"], transaction?.toIntOrNull() ?: 0, built) }
        }
    }
}
