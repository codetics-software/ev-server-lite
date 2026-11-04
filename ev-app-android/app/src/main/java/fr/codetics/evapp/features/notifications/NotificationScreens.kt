// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp.features.notifications

import androidx.compose.ui.res.stringResource
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import fr.codetics.evapp.R
import fr.codetics.evapp.app.Route
import fr.codetics.evapp.core.Formatters
import fr.codetics.evapp.features.LocalModel
import fr.codetics.evapp.models.NotificationPreferences
import fr.codetics.evapp.models.NotificationResponse
import fr.codetics.evapp.models.NotificationTypeInfo
import fr.codetics.evapp.ui.ActionRow
import fr.codetics.evapp.ui.ErrorBanner
import fr.codetics.evapp.ui.EvList
import fr.codetics.evapp.ui.EvScaffold
import fr.codetics.evapp.ui.LoadingRow
import fr.codetics.evapp.ui.RowDivider
import fr.codetics.evapp.ui.TextRow
import fr.codetics.evapp.ui.item
import fr.codetics.evapp.ui.section
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun NotificationsScreen() {
    val model = LocalModel.current
    var items by remember { mutableStateOf<List<NotificationResponse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    suspend fun load() {
        try {
            items = model.api?.notifications()?.items.orEmpty()
            errorMessage = null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            errorMessage = model.message(error)
        } finally {
            loading = false
        }
    }
    LaunchedEffect(Unit) { load() }
    EvScaffold(stringResource(R.string.notifications), onBack = { model.back() }) { padding ->
        EvList(padding, onRefresh = { load() }) {
            section {
                ActionRow(stringResource(R.string.preferences), icon = R.drawable.sym_tune, chevron = true, modifier = Modifier.testTag("notifications.preferences")) {
                    model.open(Route.NotificationPreferences)
                }
            }
            errorMessage?.let { message -> item { ErrorBanner(message) } }
            section(stringResource(R.string.history)) {
                if (items.isEmpty() && !loading) TextRow(stringResource(R.string.no_notifications_yet))
                items.forEachIndexed { index, item ->
                    if (index > 0) RowDivider()
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(item.subject, style = MaterialTheme.typography.titleSmall)
                        Text(Formatters.dateTime(item.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@Composable
fun NotificationPreferencesScreen() {
    val model = LocalModel.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preferences by remember { mutableStateOf<NotificationPreferences?>(null) }
    var types by remember { mutableStateOf<List<NotificationTypeInfo>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            preferences = model.api?.notificationPreferences()
            types = model.api?.notificationTypes().orEmpty()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            errorMessage = model.message(error)
        }
    }
    fun update(next: NotificationPreferences) {
        preferences = next
        scope.launch {
            try {
                model.api?.updateNotificationPreferences(next)
                errorMessage = null
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage = model.message(error)
            }
        }
    }
    val denied = !model.push.notificationsAllowed
    EvScaffold(stringResource(R.string.preferences), onBack = { model.back() }) { padding ->
        EvList(padding) {
            val current = preferences
            if (current != null) {
                section(
                    footer =
                        when {
                            model.server?.androidPushEnabled != true -> stringResource(R.string.this_server_does_not_send_push_notifications_yet)
                            denied -> stringResource(R.string.notifications_are_turned_off_for_this_app_in_and)
                            else -> null
                        },
                ) {
                    ToggleRow(stringResource(R.string.push_notifications), current.push) { update(current.copy(push = it)) }
                    RowDivider()
                    ToggleRow(stringResource(R.string.emails), current.email) { update(current.copy(email = it)) }
                    if (denied) {
                        RowDivider()
                        ActionRow(stringResource(R.string.open_android_settings), icon = R.drawable.sym_open_in_new) {
                            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                        }
                    }
                }
                section(stringResource(R.string.notify_me_about)) {
                    types.filter { it.audience == "DRIVER" }.forEachIndexed { index, type ->
                        if (index > 0) RowDivider()
                        ToggleRow(type.label, current.types[type.type] ?: true) { enabled ->
                            update(current.copy(types = current.types + (type.type to enabled)))
                        }
                    }
                }
            } else if (errorMessage == null) {
                item { LoadingRow() }
            }
            errorMessage?.let { message -> item { ErrorBanner(message) } }
        }
    }
}
