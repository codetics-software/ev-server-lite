// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp.features.charging

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.codetics.evapp.R
import fr.codetics.evapp.app.AppTab
import fr.codetics.evapp.app.ChargingPointId
import fr.codetics.evapp.app.ConnectorStatus
import fr.codetics.evapp.app.PortalLink
import fr.codetics.evapp.app.Route
import fr.codetics.evapp.app.StartRequest
import fr.codetics.evapp.features.LocalModel
import fr.codetics.evapp.ui.ActionRow
import fr.codetics.evapp.ui.EvList
import fr.codetics.evapp.ui.EvScaffold
import fr.codetics.evapp.ui.NoticeRow
import fr.codetics.evapp.ui.PrimaryButton
import fr.codetics.evapp.ui.RowDivider
import fr.codetics.evapp.ui.item
import fr.codetics.evapp.ui.openInBrowser
import fr.codetics.evapp.ui.section
import fr.codetics.evapp.ui.theme.StatusOrange
import kotlinx.coroutines.launch

@Composable
fun ChargingPointIdScreen(prefill: String?, operatorUrl: String?) {
    val model = LocalModel.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by rememberSaveable { mutableStateOf(prefill.orEmpty()) }
    var searching by remember { mutableStateOf(false) }
    var notFound by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    fun search() {
        if (ChargingPointId.normalize(code).isEmpty() || searching) return
        scope.launch {
            searching = true
            try {
                if (model.stations.stations.isEmpty()) model.stations.reload()
                val match =
                    ChargingPointId.match(code, model.stations.stations)
                        ?: run {
                            model.stations.reload()
                            ChargingPointId.match(code, model.stations.stations)
                        }
                if (match == null) {
                    notFound = true
                    return@launch
                }
                val link =
                    if (model.isSignedIn) null
                    else match.station.publicEvses.firstOrNull { match.connectorId == null || it.connectorId == match.connectorId }
                if (link != null) {
                    model.open(Route.Portal(PortalLink(link.workspaceSlug, link.id)), AppTab.Charge)
                    return@launch
                }
                model.open(Route.StationDetail(match.station.id), AppTab.Charge)
                val connector = match.station.connectors.firstOrNull { it.connectorId == match.connectorId }
                if (
                    model.isSignedIn && connector != null && match.station.connected &&
                        connector.currentTransactionId == null && ConnectorStatus.of(connector.status).isStartable
                ) {
                    model.open(Route.StartCharge(StartRequest(match.station.id, match.station.name, connector.connectorId)), AppTab.Charge)
                }
            } finally {
                searching = false
            }
        }
    }

    LaunchedEffect(Unit) { if (code.isEmpty()) runCatching { focus.requestFocus() } }

    EvScaffold(stringResource(R.string.charging_point_id), onBack = { model.back() }) { padding ->
        EvList(padding) {
            section(stringResource(R.string.charging_point_id), footer = stringResource(R.string.type_the_id_printed_on_the_charging_point)) {
                OutlinedTextField(
                    code,
                    {
                        code = it
                        notFound = false
                    },
                    placeholder = { Text("FR*ABC*E12345*1", fontFamily = FontFamily.Monospace) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Search,
                        ),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(focus).testTag("chargingPoint.code"),
                )
            }
            item {
                Box(Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    PrimaryButton(
                        stringResource(R.string.find_this_charging_point),
                        modifier = Modifier.testTag("chargingPoint.find"),
                        enabled = ChargingPointId.normalize(code).isNotEmpty() && !searching,
                    ) { search() }
                    if (searching) CircularProgressIndicator(Modifier.size(24.dp))
                }
            }
            if (notFound) {
                section {
                    NoticeRow(stringResource(R.string.this_charging_point_is_not_on_x, model.brandName), R.drawable.sym_help, StatusOrange)
                    operatorUrl?.let { url ->
                        RowDivider()
                        ActionRow(
                            stringResource(R.string.continue_on_x, Uri.parse(url).host.orEmpty()),
                            icon = R.drawable.sym_open_in_new,
                        ) { context.openInBrowser(url) }
                    }
                }
            }
        }
    }
}
