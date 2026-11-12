// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp.features

import androidx.compose.ui.res.stringResource
import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import fr.codetics.evapp.R
import fr.codetics.evapp.app.AppModel
import fr.codetics.evapp.app.AppTab
import fr.codetics.evapp.app.Launch
import fr.codetics.evapp.app.Route
import fr.codetics.evapp.core.Formatters
import fr.codetics.evapp.features.account.AboutScreen
import fr.codetics.evapp.features.account.AccountScreen
import fr.codetics.evapp.features.account.MySitesScreen
import fr.codetics.evapp.features.account.ProfileScreen
import fr.codetics.evapp.features.auth.EulaScreen
import fr.codetics.evapp.features.auth.PasswordResetScreen
import fr.codetics.evapp.features.auth.SignInEntryScreen
import fr.codetics.evapp.features.auth.SignUpScreen
import fr.codetics.evapp.features.badges.ActivateCardScreen
import fr.codetics.evapp.features.badges.BadgeDetailScreen
import fr.codetics.evapp.features.badges.BadgesScreen
import fr.codetics.evapp.features.billing.InvoicesScreen
import fr.codetics.evapp.features.billing.PaymentMethodsScreen
import fr.codetics.evapp.features.charging.ChargeScreen
import fr.codetics.evapp.features.charging.ChargingPointIdScreen
import fr.codetics.evapp.features.charging.ChargingPointScreen
import fr.codetics.evapp.features.charging.GuestCheckoutScreen
import fr.codetics.evapp.features.charging.GuestSessionScreen
import fr.codetics.evapp.features.charging.LiveSessionScreen
import fr.codetics.evapp.features.charging.QrScannerScreen
import fr.codetics.evapp.features.charging.StartChargeScreen
import fr.codetics.evapp.features.launch.ConfigurationIssuesView
import fr.codetics.evapp.features.launch.LaunchView
import fr.codetics.evapp.features.launch.ServerUnavailableView
import fr.codetics.evapp.features.map.StationMapScreen
import fr.codetics.evapp.features.notifications.NotificationPreferencesScreen
import fr.codetics.evapp.features.notifications.NotificationsScreen
import fr.codetics.evapp.features.sessions.SessionDetailScreen
import fr.codetics.evapp.features.sessions.SessionsScreen
import fr.codetics.evapp.features.stations.ReportProblemScreen
import fr.codetics.evapp.features.stations.StationDetailScreen
import fr.codetics.evapp.features.vehicles.VehicleDetailScreen
import fr.codetics.evapp.features.vehicles.VehicleEditorScreen
import fr.codetics.evapp.features.vehicles.VehiclesScreen
import fr.codetics.evapp.ui.Sym

val LocalModel = staticCompositionLocalOf<AppModel> { error("AppModel is not provided") }

@Composable
fun RootView(model: AppModel, modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalModel provides model) {
        Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (val state = model.launch) {
                Launch.Checking -> LaunchView()
                is Launch.Misconfigured -> ConfigurationIssuesView(state.issues)
                is Launch.Unreachable -> ServerUnavailableView(state.message)
                Launch.Incompatible -> ServerUnavailableView(stringResource(R.string.this_server_is_not_compatible_with_this_version))
                Launch.Ready ->
                    if (model.principal?.eulaAccepted == false) EulaScreen() else MainTabs(model)
            }
            model.alertMessage?.let { message ->
                AlertDialog(
                    onDismissRequest = { model.alertMessage = null },
                    title = { Text(stringResource(R.string.something_went_wrong)) },
                    text = { Text(message) },
                    confirmButton = { TextButton(onClick = { model.alertMessage = null }) { Text(stringResource(R.string.ok)) } },
                )
            }
        }
    }
}

private data class TabItem(val tab: AppTab, val label: String, @DrawableRes val icon: Int)

@Composable
private fun MainTabs(model: AppModel) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, model.isSignedIn) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { model.charging.monitor() }
    }
    if (Build.VERSION.SDK_INT >= 33) {
        val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            model.push.permissionRequest = false
        }
        LaunchedEffect(model.push.permissionRequest) {
            if (model.push.permissionRequest) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val tabs = buildList {
        add(TabItem(AppTab.Map, stringResource(R.string.map), R.drawable.sym_map))
        add(TabItem(AppTab.Charge, stringResource(R.string.charge), R.drawable.sym_ev_station))
        if (model.isSignedIn) {
            add(TabItem(AppTab.Sessions, stringResource(R.string.sessions), R.drawable.sym_history))
            add(TabItem(AppTab.Badges, stringResource(R.string.badges), R.drawable.sym_contactless))
        }
        add(TabItem(AppTab.Account, stringResource(R.string.account), R.drawable.sym_account_circle))
    }
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            tabs.forEach { item ->
                item(
                    selected = model.selectedTab == item.tab,
                    onClick = {
                        if (model.selectedTab == item.tab) {
                            model.stacks.getValue(item.tab).apply { while (size > 1) removeAt(lastIndex) }
                        }
                        model.selectedTab = item.tab
                    },
                    icon = { Sym(item.icon, tint = androidx.compose.material3.LocalContentColor.current) },
                    label = { Text(item.label) },
                    modifier = Modifier.testTag("tab.${item.tab.name.lowercase()}"),
                )
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            val tab = if (tabs.any { it.tab == model.selectedTab }) model.selectedTab else AppTab.Map
            val stack = model.stacks.getValue(tab)
            BackHandler(enabled = stack.size == 1 && tab != AppTab.Map) { model.selectedTab = AppTab.Map }
            Box(Modifier.weight(1f)) {
                NavDisplay(
                    backStack = stack,
                    onBack = { stack.removeAt(stack.lastIndex) },
                    entryProvider = { route -> NavEntry(route) { Destination(route) } },
                )
            }
            ActiveSessionBar(model)
        }
    }
}

@Composable
private fun ActiveSessionBar(model: AppModel) {
    val session = model.charging.current ?: return
    val onScreen = model.stacks.getValue(AppTab.Charge).lastOrNull() == Route.LiveSession(session.id) && model.selectedTab == AppTab.Charge
    if (onScreen) return
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().testTag("session.accessory").clickable { model.openNotification(session.id) },
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Sym(R.drawable.sym_electric_car_fill, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Column(Modifier.weight(1f)) {
                Text(
                    model.stations.station(session.stationId)?.name ?: session.stationId,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "${Formatters.power(session.currentPowerKw ?: 0.0)} · ${Formatters.energy(session.energyKwh)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Sym(R.drawable.sym_chevron_right, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun Destination(route: Route) {
    when (route) {
        is Route.Root ->
            when (route.tab) {
                AppTab.Map -> StationMapScreen()
                AppTab.Charge -> ChargeScreen()
                AppTab.Sessions -> SessionsScreen()
                AppTab.Badges -> BadgesScreen()
                AppTab.Account -> if (LocalModel.current.isSignedIn) AccountScreen() else SignInEntryScreen()
            }
        is Route.StationDetail -> StationDetailScreen(route.stationId)
        is Route.StartCharge -> StartChargeScreen(route.request)
        is Route.Report -> ReportProblemScreen(route.stationId, route.connectorId, route.subject)
        is Route.LiveSession -> LiveSessionScreen(route.id)
        is Route.Portal -> ChargingPointScreen(route.link)
        is Route.Checkout -> GuestCheckoutScreen(route.portal)
        is Route.GuestSession -> GuestSessionScreen(route.order)
        Route.Scanner -> QrScannerScreen()
        is Route.ChargingPointLookup -> ChargingPointIdScreen(route.prefill, route.operatorUrl)
        is Route.BadgeDetail -> BadgeDetailScreen(route.token)
        Route.ActivateCard -> ActivateCardScreen()
        is Route.SessionDetail -> SessionDetailScreen(route.session)
        Route.Vehicles -> VehiclesScreen()
        is Route.VehicleDetail -> VehicleDetailScreen(route.car)
        is Route.VehicleEditor -> VehicleEditorScreen(route.car)
        Route.PaymentMethods -> PaymentMethodsScreen()
        Route.Invoices -> InvoicesScreen()
        Route.Notifications -> NotificationsScreen()
        Route.NotificationPreferences -> NotificationPreferencesScreen()
        Route.Sites -> MySitesScreen()
        Route.Profile -> ProfileScreen()
        Route.About -> AboutScreen()
        Route.SignUp -> SignUpScreen()
        is Route.PasswordReset -> PasswordResetScreen(route.workspace, route.email)
    }
}
