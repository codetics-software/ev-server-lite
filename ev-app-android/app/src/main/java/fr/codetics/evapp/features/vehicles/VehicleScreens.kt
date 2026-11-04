// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp.features.vehicles

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Surface
import fr.codetics.evapp.R
import fr.codetics.evapp.app.Route
import fr.codetics.evapp.core.Formatters
import fr.codetics.evapp.features.LocalModel
import fr.codetics.evapp.models.AutoChargeCandidate
import fr.codetics.evapp.models.CarCatalogData
import fr.codetics.evapp.models.CarData
import fr.codetics.evapp.models.RecordInput
import fr.codetics.evapp.models.RecordResponse
import fr.codetics.evapp.models.VehicleIdResponse
import fr.codetics.evapp.ui.ActionRow
import fr.codetics.evapp.ui.ConfirmDialog
import fr.codetics.evapp.ui.EmptyState
import fr.codetics.evapp.ui.ErrorBanner
import fr.codetics.evapp.ui.EvList
import fr.codetics.evapp.ui.EvScaffold
import fr.codetics.evapp.ui.LabeledRow
import fr.codetics.evapp.ui.PrimaryButton
import fr.codetics.evapp.ui.RowDivider
import fr.codetics.evapp.ui.Sym
import fr.codetics.evapp.ui.TextRow
import fr.codetics.evapp.ui.item
import fr.codetics.evapp.ui.section
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VehiclesScreen() {
    val model = LocalModel.current
    val scope = rememberCoroutineScope()
    var cars by remember { mutableStateOf<List<RecordResponse<CarData>>>(emptyList()) }
    var defaultCarId by remember { mutableStateOf<UUID?>(null) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<RecordResponse<CarData>?>(null) }
    suspend fun load() {
        val api = model.api ?: return
        try {
            coroutineScope {
                val owned = async { api.cars() }
                val profile = async { api.profile() }
                cars = owned.await()
                defaultCarId = profile.await().defaultCarId
            }
            errorMessage = null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            errorMessage = model.message(error)
        } finally {
            loading = false
        }
    }
    LaunchedEffect(model.stacks.getValue(model.selectedTab).size) { load() }
    EvScaffold(
        stringResource(R.string.my_vehicles),
        onBack = { model.back() },
        floatingActionButton = {
            FloatingActionButton(onClick = { model.open(Route.VehicleEditor(null)) }, modifier = Modifier.testTag("vehicles.add")) {
                Sym(R.drawable.sym_add, tint = MaterialTheme.colorScheme.onPrimaryContainer, description = stringResource(R.string.add_a_vehicle))
            }
        },
    ) { padding ->
        EvList(padding, onRefresh = { load() }) {
            errorMessage?.let { message -> item { ErrorBanner(message) } }
            if (cars.isEmpty() && !loading && errorMessage == null) {
                item {
                    EmptyState(R.drawable.sym_directions_car, stringResource(R.string.no_vehicle), stringResource(R.string.add_your_vehicle_to_get_better_charging_estimate)) {
                        Button(onClick = { model.open(Route.VehicleEditor(null)) }) { Text(stringResource(R.string.add_a_vehicle)) }
                    }
                }
            } else if (cars.isNotEmpty()) {
                section {
                    cars.forEachIndexed { index, car ->
                        if (index > 0) RowDivider()
                        val defaultLabel = stringResource(R.string.default_label)
                        ActionRow(
                            title = car.name,
                            detail = listOfNotNull(
                                car.data.licensePlate,
                                car.data.batteryKwh?.let(Formatters::energy),
                                defaultLabel.takeIf { car.id == defaultCarId },
                            ).joinToString(" · ").ifEmpty { null },
                            icon = R.drawable.sym_electric_car_fill,
                            modifier = Modifier.testTag("vehicle.row"),
                            trailing = {
                                IconButton(onClick = { deleting = car }) {
                                    Sym(R.drawable.sym_delete, description = stringResource(R.string.delete))
                                }
                            },
                        ) { model.open(Route.VehicleDetail(car)) }
                    }
                }
            }
        }
    }
    deleting?.let { car ->
        ConfirmDialog(stringResource(R.string.delete_this_vehicle), car.name, stringResource(R.string.delete), onDismiss = { deleting = null }) {
            scope.launch {
                try {
                    model.api?.deleteCar(car.id)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    errorMessage = model.message(error)
                }
                load()
            }
        }
    }
}

@Composable
fun VehicleDetailScreen(car: RecordResponse<CarData>) {
    val model = LocalModel.current
    val scope = rememberCoroutineScope()
    var vehicleIds by remember { mutableStateOf<List<VehicleIdResponse>>(emptyList()) }
    var candidates by remember { mutableStateOf<List<AutoChargeCandidate>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    suspend fun load() {
        try {
            vehicleIds = model.api?.vehicleIds(car.id).orEmpty()
            candidates = model.api?.autoChargeCandidates().orEmpty()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            errorMessage = model.message(error)
        }
    }
    fun perform(action: suspend () -> Unit) {
        scope.launch {
            try {
                action()
                load()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage = model.message(error)
            }
        }
    }
    LaunchedEffect(car.id) { load() }
    EvScaffold(
        car.name,
        onBack = { model.back() },
        actions = {
            TextButton(onClick = {
                model.back()
                model.open(Route.VehicleEditor(car))
            }) { Text(stringResource(R.string.edit)) }
        },
    ) { padding ->
        EvList(padding) {
            section(stringResource(R.string.vehicle)) {
                val rows = buildList<Pair<String, String>> {
                    car.data.make?.let { add("make" to it) }
                    car.data.model?.let { add("model" to it) }
                    car.data.licensePlate?.let { add("plate" to it) }
                    car.data.vin?.let { add("vin" to it) }
                    car.data.batteryKwh?.let { add("battery" to Formatters.energy(it)) }
                    car.data.maxAcPowerKw?.let { add("ac" to Formatters.power(it)) }
                    car.data.maxDcPowerKw?.let { add("dc" to Formatters.power(it)) }
                }
                rows.forEachIndexed { index, (key, value) ->
                    if (index > 0) RowDivider()
                    LabeledRow(
                        when (key) {
                            "make" -> stringResource(R.string.make)
                            "model" -> stringResource(R.string.model)
                            "plate" -> stringResource(R.string.licence_plate)
                            "vin" -> stringResource(R.string.vin)
                            "battery" -> stringResource(R.string.battery)
                            "ac" -> stringResource(R.string.ac_power)
                            else -> stringResource(R.string.dc_power)
                        },
                        value,
                    )
                }
            }
            section(stringResource(R.string.automatic_charging), stringResource(R.string.on_compatible_stations_charging_starts_when_you)) {
                vehicleIds.forEach { id ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Sym(R.drawable.sym_auto_mode, tint = MaterialTheme.colorScheme.primary)
                        Text(id.uid, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                        TextButton(onClick = { perform { model.api?.revokeAutoCharge(car.id, id.uid) } }) {
                            Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                candidates.forEach { candidate ->
                    ActionRow(stringResource(R.string.enable_with_the_session_on_x, candidate.stationId), icon = R.drawable.sym_add_circle) {
                        perform { model.api?.enrollAutoCharge(car.id, candidate.transactionId) }
                    }
                }
                if (vehicleIds.isEmpty() && candidates.isEmpty()) {
                    TextRow(stringResource(R.string.plug_this_vehicle_into_a_compatible_station_and))
                }
            }
            errorMessage?.let { message -> item { ErrorBanner(message) } }
        }
    }
}

private fun parseNumber(value: String) = value.replace(',', '.').toDoubleOrNull()

private fun displayNumber(value: Double?) = value?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }.orEmpty()

@Composable
fun VehicleEditorScreen(car: RecordResponse<CarData>?) {
    val model = LocalModel.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(car?.name.orEmpty()) }
    var make by rememberSaveable { mutableStateOf(car?.data?.make.orEmpty()) }
    var carModel by rememberSaveable { mutableStateOf(car?.data?.model.orEmpty()) }
    var plate by rememberSaveable { mutableStateOf(car?.data?.licensePlate.orEmpty()) }
    var vin by rememberSaveable { mutableStateOf(car?.data?.vin.orEmpty()) }
    var battery by rememberSaveable { mutableStateOf(displayNumber(car?.data?.batteryKwh)) }
    var ac by rememberSaveable { mutableStateOf(displayNumber(car?.data?.maxAcPowerKw)) }
    var dc by rememberSaveable { mutableStateOf(displayNumber(car?.data?.maxDcPowerKw)) }
    var isDefault by rememberSaveable { mutableStateOf(car?.data?.default ?: false) }
    var catalogId by remember { mutableStateOf(car?.data?.carCatalogId) }
    var showsCatalog by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val fallbackName = stringResource(R.string.vehicle)
    EvScaffold(if (car == null) stringResource(R.string.new_vehicle) else stringResource(R.string.edit_vehicle), onBack = { model.back() }) { padding ->
        EvList(padding) {
            section {
                ActionRow(stringResource(R.string.choose_from_the_catalog), icon = R.drawable.sym_search, modifier = Modifier.testTag("vehicle.catalog")) { showsCatalog = true }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
                    Text(stringResource(R.string.vehicle), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Field(stringResource(R.string.name_optional), name) { name = it }
                    Field(stringResource(R.string.make), make, "vehicle.make") { make = it }
                    Field(stringResource(R.string.model), carModel, "vehicle.model") { carModel = it }
                    Field(stringResource(R.string.licence_plate), plate, characters = true) { plate = it }
                    Field(stringResource(R.string.vin_17_characters), vin, characters = true) { vin = it }
                    Text(stringResource(R.string.specifications), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                    Field(stringResource(R.string.battery_kwh), battery, number = true) { battery = it }
                    Field(stringResource(R.string.ac_power_kw), ac, number = true) { ac = it }
                    Field(stringResource(R.string.dc_power_kw), dc, number = true) { dc = it }
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.use_as_my_default_vehicle), modifier = Modifier.weight(1f))
                        Switch(isDefault, { isDefault = it })
                    }
                    errorMessage?.let { ErrorBanner(it) }
                    PrimaryButton(stringResource(R.string.save), enabled = !saving, modifier = Modifier.testTag("vehicle.save")) {
                        scope.launch {
                            val api = model.api ?: return@launch
                            saving = true
                            val payload =
                                CarData(
                                    userId = car?.data?.userId,
                                    carCatalogId = catalogId,
                                    vin = vin.trim().uppercase().ifEmpty { null },
                                    licensePlate = plate.trim().uppercase().ifEmpty { null },
                                    make = make.trim().ifEmpty { null },
                                    model = carModel.trim().ifEmpty { null },
                                    batteryKwh = parseNumber(battery),
                                    maxAcPowerKw = parseNumber(ac),
                                    maxDcPowerKw = parseNumber(dc),
                                    default = isDefault,
                                )
                            val label =
                                name.trim().ifEmpty { null }
                                    ?: listOfNotNull(payload.make, payload.model).joinToString(" ").ifEmpty { null }
                                    ?: payload.licensePlate
                                    ?: fallbackName
                            try {
                                if (car == null) {
                                    api.createCar(RecordInput(label, payload, null))
                                } else {
                                    api.updateCar(car.id, RecordInput(label, payload, car.revision))
                                }
                                model.back()
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                errorMessage = model.message(error)
                            } finally {
                                saving = false
                            }
                        }
                    }
                }
            }
        }
    }
    if (showsCatalog) {
        CarCatalogDialog(onDismiss = { showsCatalog = false }) { entry ->
            catalogId = entry.id
            make = entry.data.make
            carModel = listOfNotNull(entry.data.model, entry.data.version).joinToString(" ")
            entry.data.batteryKwh?.let { battery = displayNumber(it) }
            entry.data.maxAcPowerKw?.let { ac = displayNumber(it) }
            entry.data.maxDcPowerKw?.let { dc = displayNumber(it) }
            showsCatalog = false
        }
    }
}

@Composable
private fun Field(label: String, value: String, tag: String? = null, number: Boolean = false, characters: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text,
            capitalization = if (characters) KeyboardCapitalization.Characters else KeyboardCapitalization.None,
        ),
        modifier = Modifier.fillMaxWidth().then(if (tag != null) Modifier.testTag(tag) else Modifier),
    )
}

@Composable
private fun CarCatalogDialog(onDismiss: () -> Unit, onSelect: (RecordResponse<CarCatalogData>) -> Unit) {
    val model = LocalModel.current
    var search by rememberSaveable { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<RecordResponse<CarCatalogData>>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(search) {
        delay(250)
        try {
            entries = model.api?.carCatalog(search)?.items.orEmpty()
            errorMessage = null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            errorMessage = model.message(error)
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            EvScaffold(stringResource(R.string.vehicle_catalog), onBack = onDismiss) { padding ->
                Column(Modifier.padding(padding)) {
                    OutlinedTextField(
                        search, { search = it },
                        placeholder = { Text(stringResource(R.string.make_or_model)) },
                        leadingIcon = { Sym(R.drawable.sym_search) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("catalog.search"),
                    )
                    errorMessage?.let { Column(Modifier.padding(horizontal = 16.dp)) { ErrorBanner(it) } }
                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(entries, key = { it.id }) { entry ->
                            Column(
                                Modifier.fillMaxWidth().clickable { onSelect(entry) }.padding(horizontal = 16.dp, vertical = 12.dp).testTag("catalog.row"),
                            ) {
                                Text("${entry.data.make} ${entry.data.model}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                entry.data.version?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            RowDivider()
                        }
                    }
                }
            }
        }
    }
}
