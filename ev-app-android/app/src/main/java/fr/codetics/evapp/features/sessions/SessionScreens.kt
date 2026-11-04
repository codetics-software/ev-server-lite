// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp.features.sessions

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import fr.codetics.evapp.R
import fr.codetics.evapp.app.Route
import fr.codetics.evapp.core.ApiError
import fr.codetics.evapp.core.Formatters
import fr.codetics.evapp.features.LocalModel
import fr.codetics.evapp.features.charging.ConsumptionChart
import fr.codetics.evapp.models.ChargingSessionResponse
import fr.codetics.evapp.models.ConsumptionPoint
import fr.codetics.evapp.models.SignedMeterData
import fr.codetics.evapp.models.StatisticsResponse
import fr.codetics.evapp.ui.ActionRow
import fr.codetics.evapp.ui.EmptyState
import fr.codetics.evapp.ui.ErrorBanner
import fr.codetics.evapp.ui.EvList
import fr.codetics.evapp.ui.EvScaffold
import fr.codetics.evapp.ui.LabeledRow
import fr.codetics.evapp.ui.MetricTile
import fr.codetics.evapp.ui.RowDivider
import fr.codetics.evapp.ui.item
import fr.codetics.evapp.ui.section
import java.math.BigDecimal
import java.text.DateFormatSymbols
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun SessionsScreen() {
    val model = LocalModel.current
    var statistics by rememberSaveable { mutableStateOf(false) }
    EvScaffold(stringResource(R.string.sessions)) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (model.components?.statistics != false) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("sessions.mode")) {
                    SegmentedButton(
                        selected = !statistics, onClick = { statistics = false },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        modifier = Modifier.testTag("sessions.history"),
                    ) { Text(stringResource(R.string.history)) }
                    SegmentedButton(
                        selected = statistics, onClick = { statistics = true },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        modifier = Modifier.testTag("sessions.statistics"),
                    ) { Text(stringResource(R.string.statistics)) }
                }
            }
            if (statistics) StatisticsView() else SessionHistoryView()
        }
    }
}

@Composable
private fun SessionHistoryView() {
    val model = LocalModel.current
    val scope = rememberCoroutineScope()
    val sessions = remember { mutableStateListOf<ChargingSessionResponse>() }
    var total by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var loadedOnce by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    suspend fun load(reset: Boolean) {
        val api = model.api ?: return
        if (loading) return
        loading = true
        try {
            val page = api.sessions(offset = if (reset) 0 else sessions.size, limit = 30)
            if (reset) sessions.clear()
            sessions.addAll(page.items)
            total = page.total
            errorMessage = null
        } catch (error: CancellationException) {
            throw error
        } catch (_: ApiError.Unauthorized) {
            model.sessionExpired()
        } catch (error: Exception) {
            errorMessage = model.message(error)
        } finally {
            loading = false
            loadedOnce = true
        }
    }
    LaunchedEffect(Unit) { load(true) }
    fr.codetics.evapp.ui.EvLazyList(PaddingValues(), onRefresh = { load(true) }) {
        errorMessage?.let { message -> item { ErrorBanner(message) } }
        if (sessions.isEmpty() && loadedOnce && errorMessage == null) {
            item { EmptyState(R.drawable.sym_electric_car, stringResource(R.string.no_sessions_yet), stringResource(R.string.your_charging_sessions_will_appear_here)) }
        }
        items(sessions.size) { index ->
            val session = sessions[index]
            if (index == sessions.lastIndex && sessions.size < total) {
                LaunchedEffect(sessions.size) { load(false) }
            }
            SessionRow(session, model.stations.station(session.stationId)?.name) { model.open(Route.SessionDetail(session)) }
        }
    }
}

@Composable
private fun SessionRow(session: ChargingSessionResponse, stationName: String?, onClick: () -> Unit) {
    androidx.compose.material3.Card(
        onClick = onClick,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("session.row.${session.id}"),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stationName ?: session.stationId, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                if (session.isActive) {
                    Text(stringResource(R.string.in_progress), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                } else {
                    Text(Formatters.price(session.price, session.currency), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(Formatters.dateTime(session.startedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${Formatters.energy(session.energyKwh)} · ${Formatters.duration(session.durationSeconds)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun billingLabel(status: String) =
    when (status) {
        "NONE" -> stringResource(R.string.not_billed)
        "PENDING" -> stringResource(R.string.pending)
        "INVOICED" -> stringResource(R.string.invoiced)
        "PAID" -> stringResource(R.string.paid)
        "FAILED" -> stringResource(R.string.payment_failed)
        else -> stringResource(R.string.unknown)
    }

@Composable
fun SessionDetailScreen(session: ChargingSessionResponse) {
    val model = LocalModel.current
    var detail by remember { mutableStateOf<ChargingSessionResponse?>(null) }
    var points by remember { mutableStateOf<List<ConsumptionPoint>>(emptyList()) }
    var signed by remember { mutableStateOf<SignedMeterData?>(null) }
    val current = detail ?: session
    LaunchedEffect(session.id) {
        val api = model.api ?: return@LaunchedEffect
        detail = runCatching { api.session(session.id) }.getOrNull()
        points = runCatching { api.consumptions(session.id) }.getOrDefault(emptyList())
        if ((detail ?: session).signedMeterData) signed = runCatching { api.signedData(session.id) }.getOrNull()
    }
    val reportSubject = stringResource(R.string.session_x, current.id.toString())
    EvScaffold(stringResource(R.string.session_x, current.id.toString()), onBack = { model.back() }) { padding ->
        EvList(padding) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricTile(stringResource(R.string.energy), Formatters.energy(current.energyKwh), R.drawable.sym_bolt_fill, Modifier.weight(1f))
                        MetricTile(stringResource(R.string.duration), Formatters.duration(current.durationSeconds), R.drawable.sym_timer, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricTile(stringResource(R.string.price), Formatters.price(current.price, current.currency), R.drawable.sym_euro_symbol, Modifier.weight(1f))
                        MetricTile(stringResource(R.string.idle_time), Formatters.duration(current.inactivitySeconds), R.drawable.sym_pause_circle, Modifier.weight(1f))
                    }
                }
            }
            if (points.size > 1) section { ConsumptionChart(points, Modifier.padding(16.dp)) }
            section(stringResource(R.string.details)) {
                LabeledRow(stringResource(R.string.station), model.stations.station(current.stationId)?.name ?: current.stationId)
                RowDivider()
                LabeledRow(stringResource(R.string.connector), current.connectorId.toString())
                RowDivider()
                LabeledRow(stringResource(R.string.started), Formatters.dateTime(current.startedAt))
                current.stoppedAt?.let {
                    RowDivider()
                    LabeledRow(stringResource(R.string.ended), Formatters.dateTime(it))
                }
                val start = current.stateOfChargeStart
                val end = current.stateOfCharge
                if (start != null && end != null) {
                    RowDivider()
                    LabeledRow(stringResource(R.string.battery), "$start% → $end%")
                }
                current.tokenUid?.let {
                    RowDivider()
                    LabeledRow(stringResource(R.string.badge), it)
                }
                current.roamingPartner?.let {
                    RowDivider()
                    LabeledRow(stringResource(R.string.roaming_partner), it)
                }
                RowDivider()
                LabeledRow(stringResource(R.string.billing), billingLabel(current.billingStatus))
            }
            signed?.takeIf { it.start != null || it.stop != null }?.let { data ->
                section(stringResource(R.string.signed_meter_data), stringResource(R.string.these_signed_readings_let_you_check_the_metered)) {
                    data.format?.let { LabeledRow(stringResource(R.string.format), it) }
                    data.start?.let { SignedReading(stringResource(R.string.start_reading), it) }
                    data.stop?.let { SignedReading(stringResource(R.string.end_reading), it) }
                }
            }
            section {
                if (current.isActive) {
                    ActionRow(stringResource(R.string.follow_live), icon = R.drawable.sym_electric_bolt, chevron = true) { model.open(Route.LiveSession(current.id)) }
                    RowDivider()
                }
                ActionRow(stringResource(R.string.report_a_problem), icon = R.drawable.sym_feedback) {
                    model.open(Route.Report(current.stationId, current.connectorId, reportSubject))
                }
            }
        }
    }
}

@Composable
private fun SignedReading(title: String, value: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        if (expanded) {
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

private enum class StatisticsMetric(val key: String) {
    Consumption("consumption"),
    Usage("usage"),
    Inactivity("inactivity"),
    Sessions("sessions"),
    Pricing("pricing"),
}

@Composable
private fun metricLabel(metric: StatisticsMetric) =
    when (metric) {
        StatisticsMetric.Consumption -> stringResource(R.string.energy)
        StatisticsMetric.Usage -> stringResource(R.string.charging_time)
        StatisticsMetric.Inactivity -> stringResource(R.string.idle_time)
        StatisticsMetric.Sessions -> stringResource(R.string.sessions)
        StatisticsMetric.Pricing -> stringResource(R.string.spending)
    }

private fun metricFormat(metric: StatisticsMetric, value: Double, currency: String?): String =
    when (metric) {
        StatisticsMetric.Consumption -> Formatters.energy(value)
        StatisticsMetric.Usage, StatisticsMetric.Inactivity -> Formatters.duration((value * 3600).toInt())
        StatisticsMetric.Sessions -> value.toInt().toString()
        StatisticsMetric.Pricing -> Formatters.price(BigDecimal.valueOf(value), currency)
    }

@Composable
private fun <T> Picker(label: String, value: String, options: List<T>, text: @Composable (T) -> String, tag: String, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.testTag(tag)) { Text(value) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(text(option)) }, onClick = {
                        expanded = false
                        onSelect(option)
                    })
                }
            }
        }
    }
}

@Composable
private fun StatisticsView() {
    val model = LocalModel.current
    var metric by rememberSaveable { mutableStateOf(StatisticsMetric.Consumption) }
    var years by remember { mutableStateOf<List<Int>>(emptyList()) }
    var year by rememberSaveable { mutableIntStateOf(LocalDate.now().year) }
    var result by remember { mutableStateOf<StatisticsResponse?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    suspend fun load() {
        try {
            result = model.api?.statistics(metric.key, year)
            errorMessage = null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            errorMessage = model.message(error)
        }
    }
    LaunchedEffect(Unit) { years = runCatching { model.api?.statisticsYears() }.getOrNull().orEmpty() }
    LaunchedEffect(metric, year) { load() }
    EvList(PaddingValues(), onRefresh = { load() }) {
        section {
            Picker(stringResource(R.string.indicator), metricLabel(metric), StatisticsMetric.entries, { metricLabel(it) }, "stats.metric") { metric = it }
            if (years.isNotEmpty()) {
                RowDivider()
                Picker(stringResource(R.string.year), year.toString(), years, { it.toString() }, "stats.year") { year = it }
            }
        }
        result?.let { current ->
            section {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(metricFormat(metric, current.total, current.currency), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    MonthlyBars(current.months)
                }
            }
            if (current.groups.size > 1) {
                section(stringResource(R.string.by_station)) {
                    current.groups.forEachIndexed { index, group ->
                        if (index > 0) RowDivider()
                        LabeledRow(group.label, metricFormat(metric, group.total, current.currency))
                    }
                }
            }
        }
        errorMessage?.let { message -> item { ErrorBanner(message) } }
    }
}

@Composable
private fun MonthlyBars(months: List<Double>) {
    val accent = MaterialTheme.colorScheme.primary
    val top = months.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val symbols = remember { DateFormatSymbols.getInstance(Locale.getDefault()).shortMonths.filter(String::isNotEmpty) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(190.dp).testTag("stats.chart")) {
            val slot = size.width / months.size.coerceAtLeast(1)
            months.forEachIndexed { index, value ->
                val height = size.height * (value / top).toFloat()
                drawRoundRect(
                    accent,
                    Offset(index * slot + slot * 0.18f, size.height - height),
                    Size(slot * 0.64f, height),
                    CornerRadius(6f, 6f),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            months.indices.forEach { index ->
                Text(
                    symbols.getOrNull(index)?.take(1)?.uppercase() ?: "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
