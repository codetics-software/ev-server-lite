// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp.features.stations

import androidx.compose.ui.res.stringResource
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import fr.codetics.evapp.R
import fr.codetics.evapp.app.AppTab
import fr.codetics.evapp.app.ConnectorStatus
import fr.codetics.evapp.app.Route
import fr.codetics.evapp.app.StartRequest
import fr.codetics.evapp.app.StationAvailability
import fr.codetics.evapp.app.addressLine
import fr.codetics.evapp.app.coordinate
import fr.codetics.evapp.app.distance
import fr.codetics.evapp.core.ApiError
import fr.codetics.evapp.core.Formatters
import fr.codetics.evapp.demo.DemoSupport
import fr.codetics.evapp.features.LocalModel
import fr.codetics.evapp.features.map.availabilityLabel
import fr.codetics.evapp.features.map.connectorLabel
import fr.codetics.evapp.models.ConnectorResponse
import fr.codetics.evapp.models.CustomerStationResponse
import fr.codetics.evapp.models.CustomerTariff
import fr.codetics.evapp.models.ErrorReportInput
import fr.codetics.evapp.models.StationPhotoResponse
import fr.codetics.evapp.ui.ActionRow
import fr.codetics.evapp.ui.ErrorBanner
import fr.codetics.evapp.ui.EvList
import fr.codetics.evapp.ui.EvScaffold
import fr.codetics.evapp.ui.LabeledRow
import fr.codetics.evapp.ui.LoadingRow
import fr.codetics.evapp.ui.NoticeRow
import fr.codetics.evapp.ui.PrimaryButton
import fr.codetics.evapp.ui.RowDivider
import fr.codetics.evapp.ui.StatusDot
import fr.codetics.evapp.ui.Sym
import fr.codetics.evapp.ui.TextRow
import fr.codetics.evapp.ui.item
import fr.codetics.evapp.ui.section
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigDecimal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StationDetailScreen(stationId: String) {
    val model = LocalModel.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preview = model.stations.station(stationId)
    var loaded by remember { mutableStateOf<CustomerStationResponse?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    suspend fun load() {
        val api = model.api ?: return
        try {
            loaded =
                if (model.isSignedIn) {
                    api.station(stationId)
                } else {
                    api.publicStation(model.configuration?.workspaceSlug.orEmpty(), stationId)
                }
            errorMessage = null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            errorMessage = model.message(error)
        }
    }
    LaunchedEffect(stationId) { load() }
    val station = loaded ?: preview
    EvScaffold(title = station?.name ?: stringResource(R.string.station), onBack = { model.back() }) { padding ->
        EvList(padding, onRefresh = { load() }) {
            if (station == null) {
                item { errorMessage?.let { ErrorBanner(it) } ?: LoadingRow() }
                return@EvList
            }
            section {
                StationHeader(station)
                station.coordinate?.let { coordinate ->
                    RowDivider()
                    ActionRow(stringResource(R.string.get_directions), icon = R.drawable.sym_directions, modifier = Modifier.testTag("station.directions")) {
                        val uri = Uri.parse("geo:${coordinate.latitude},${coordinate.longitude}?q=${coordinate.latitude},${coordinate.longitude}(${Uri.encode(station.name)})")
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        } catch (_: ActivityNotFoundException) {
                            model.alertMessage = EvApp_noMaps(context)
                        }
                    }
                }
            }
            section(stringResource(R.string.connectors)) {
                if (station.connectors.isEmpty()) TextRow(stringResource(R.string.this_station_has_not_declared_any_connector_yet))
                station.connectors.forEachIndexed { index, connector ->
                    if (index > 0) RowDivider()
                    ConnectorRow(connector, station)
                }
                if (!model.isSignedIn && station.connectors.any { ConnectorStatus.of(it.status).isStartable }) {
                    RowDivider()
                    TextRow(stringResource(R.string.to_start_a_charge_sign_in_or_scan_the_qr_code_on))
                }
                if (station.autoCharge) {
                    RowDivider()
                    NoticeRow(stringResource(R.string.automatic_charging_when_you_plug_in), R.drawable.sym_auto_mode)
                }
            }
            station.tariff?.let { tariff -> section(stringResource(R.string.price)) { TariffRows(tariff) } }
            if (model.isSignedIn) {
                StationPhotosSection(station.id)
                section {
                    ActionRow(stringResource(R.string.report_a_problem), icon = R.drawable.sym_feedback) {
                        model.open(Route.Report(station.id))
                    }
                }
            }
        }
    }
}

private fun EvApp_noMaps(context: Context) = context.getString(R.string.no_app_can_open_this_location)

@Composable
private fun StationHeader(station: CustomerStationResponse) {
    val model = LocalModel.current
    val availability = StationAvailability.of(station)
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(availability.color)
            Text(availabilityLabel(availability), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            station.distance(model.userLocation)?.let {
                Text(Formatters.distance(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        station.siteName?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        station.addressLine?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        listOfNotNull(station.vendor, station.model).joinToString(" ").ifEmpty { null }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun ConnectorRow(connector: ConnectorResponse, station: CustomerStationResponse) {
    val model = LocalModel.current
    val status = ConnectorStatus.of(connector.status)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Sym(R.drawable.sym_power, tint = status.color)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.x_no_x, connectorLabel(connector.type), connector.connectorId.toString()), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(androidx.compose.ui.res.stringResource(status.label), style = MaterialTheme.typography.bodySmall, color = status.color)
                connector.maximumPowerKw?.let { Text(Formatters.power(it), style = MaterialTheme.typography.bodySmall) }
            }
        }
        val session = connector.currentTransactionId
        if (session != null) {
            OutlinedButton(onClick = { model.openNotification(session) }) { Text(stringResource(R.string.open_my_session)) }
        } else if (model.isSignedIn && status.isStartable && station.connected) {
            Button(
                onClick = { model.open(Route.StartCharge(StartRequest(station.id, station.name, connector.connectorId))) },
                modifier = Modifier.testTag("connector.start.${connector.connectorId}"),
            ) { Text(stringResource(R.string.start)) }
        }
    }
}

@Composable
private fun TariffRows(tariff: CustomerTariff) {
    Text(tariff.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
    val positive: (BigDecimal?) -> Boolean = { it != null && it.signum() > 0 }
    if (positive(tariff.energyPrice)) LabeledRow(stringResource(R.string.energy), "${Formatters.price(tariff.energyPrice, tariff.currency)} / kWh")
    if (positive(tariff.timePrice)) LabeledRow(stringResource(R.string.charging_time), "${Formatters.price(tariff.timePrice, tariff.currency)} / h")
    if (positive(tariff.parkingPrice)) LabeledRow(stringResource(R.string.parking_after_charging), "${Formatters.price(tariff.parkingPrice, tariff.currency)} / h")
    if (positive(tariff.flatPrice)) LabeledRow(stringResource(R.string.session_fee), Formatters.price(tariff.flatPrice, tariff.currency))
}

object PhotoEncoder {
    private const val MAXIMUM_DIMENSION = 1600
    private const val MAXIMUM_BYTES = 5 * 1024 * 1024

    fun jpeg(bitmap: Bitmap): ByteArray? {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= 0) return null
        val scale = minOf(1f, MAXIMUM_DIMENSION.toFloat() / longest)
        val scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
        for (quality in listOf(80, 60, 40)) {
            val output = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)
            if (output.size() <= MAXIMUM_BYTES) return output.toByteArray()
        }
        return null
    }

    fun read(context: Context, uri: Uri): Bitmap? =
        runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                var sample = 1
                while (maxOf(options.outWidth, options.outHeight) / sample > MAXIMUM_DIMENSION * 2) sample *= 2
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
                val rotation = ExifInterface(bytes.inputStream()).rotationDegrees
                if (rotation == 0) bitmap
                else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
            }
            .getOrNull()
}

@Composable
private fun StationPhotosSection(stationId: String) {
    val model = LocalModel.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photos = remember(stationId) { mutableStateListOf<StationPhotoResponse>() }
    var uploading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var viewing by remember { mutableStateOf<StationPhotoResponse?>(null) }
    var captureUri by rememberSaveable { mutableStateOf<String?>(null) }
    val preparedError = stringResource(R.string.this_image_could_not_be_prepared)
    val readError = stringResource(R.string.this_image_could_not_be_read)

    fun upload(bitmap: Bitmap?) {
        scope.launch {
            if (bitmap == null) {
                errorMessage = readError
                return@launch
            }
            val data = withContext(Dispatchers.Default) { PhotoEncoder.jpeg(bitmap) }
            if (data == null) {
                errorMessage = preparedError
                return@launch
            }
            uploading = true
            errorMessage = null
            try {
                model.api?.uploadPhoto(stationId, data)?.let { photos.add(0, it) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage = model.message(error)
            } finally {
                uploading = false
            }
        }
    }

    fun uploadUri(uri: Uri) {
        scope.launch { upload(withContext(Dispatchers.IO) { PhotoEncoder.read(context, uri) }) }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) captureUri?.let { uploadUri(Uri.parse(it)) }
    }
    fun launchCamera() {
        val file = File(context.cacheDir, "photos").apply { mkdirs() }.let { File(it, "capture-${System.currentTimeMillis()}.jpg") }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        captureUri = uri.toString()
        camera.launch(uri)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else errorMessage = context.getString(R.string.camera_access_is_turned_off_for_this_app)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(::uploadUri)
    }
    LaunchedEffect(stationId) {
        try {
            photos.clear()
            photos.addAll(model.api?.photos(stationId).orEmpty())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            errorMessage = model.message(error)
        }
    }
    val hasCamera = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    Column(Modifier.padding(top = 12.dp, bottom = 4.dp)) {
        Text(stringResource(R.string.photos), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        androidx.compose.material3.Card(
            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (photos.isNotEmpty()) {
                LazyRow(contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(photos, key = { it.id }) { photo ->
                        PhotoThumbnail(photo) { viewing = photo }
                    }
                }
                RowDivider()
            }
            if (hasCamera) {
                ActionRow(stringResource(R.string.take_a_photo), icon = R.drawable.sym_photo_camera, modifier = Modifier.testTag("photo.camera")) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        launchCamera()
                    } else {
                        cameraPermission.launch(Manifest.permission.CAMERA)
                    }
                }
                RowDivider()
            }
            ActionRow(stringResource(R.string.choose_from_library), icon = R.drawable.sym_photo_library, modifier = Modifier.testTag("photo.library")) {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            if (DemoSupport.available && model.isDemo) {
                RowDivider()
                ActionRow(stringResource(R.string.add_a_demo_photo), icon = R.drawable.sym_add_circle, modifier = Modifier.testTag("photo.demo")) {
                    upload(DemoSupport.stationPhoto())
                }
            }
            if (uploading) LoadingRow()
            errorMessage?.let { Box(Modifier.padding(horizontal = 12.dp)) { ErrorBanner(it) } }
        }
        Text(
            stringResource(R.string.photos_are_visible_to_the_other_drivers_of_this),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp),
        )
    }
    viewing?.let { photo ->
        PhotoViewer(
            photo,
            stationId,
            onDismiss = { viewing = null },
            onDeleted = {
                photos.removeAll { it.id == photo.id }
                viewing = null
            },
        )
    }
}

@Composable
private fun rememberPhoto(photo: StationPhotoResponse): ImageBitmap? {
    val model = LocalModel.current
    val image by produceState<ImageBitmap?>(null, photo.id) {
        value =
            runCatching {
                    val data = model.api?.photoData(photo) ?: return@runCatching null
                    withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap() }
                }
                .getOrNull()
    }
    return image
}

@Composable
private fun PhotoThumbnail(photo: StationPhotoResponse, onClick: () -> Unit) {
    val image = rememberPhoto(photo)
    Box(
        Modifier.size(110.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(image, stringResource(R.string.station_photo), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            CircularProgressIndicator(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun PhotoViewer(photo: StationPhotoResponse, stationId: String, onDismiss: () -> Unit, onDeleted: () -> Unit) {
    val model = LocalModel.current
    val scope = rememberCoroutineScope()
    val image = rememberPhoto(photo)
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val inappropriate = stringResource(R.string.inappropriate_photo_x, photo.id.toString())
    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Card(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(Formatters.date(photo.createdAt), style = MaterialTheme.typography.titleMedium)
                if (image != null) {
                    Image(image, stringResource(R.string.station_photo), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth())
                } else {
                    LoadingRow()
                }
                errorMessage?.let { ErrorBanner(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    if (photo.mine) {
                        TextButton(onClick = {
                            scope.launch {
                                try {
                                    model.api?.deletePhoto(photo)
                                    onDeleted()
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    errorMessage = model.message(error)
                                }
                            }
                        }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                    } else {
                        TextButton(onClick = {
                            onDismiss()
                            model.open(Route.Report(stationId, null, inappropriate))
                        }) { Text(stringResource(R.string.report_this_photo)) }
                    }
                }
            }
        }
    }
}

@Composable
fun ReportProblemScreen(stationId: String?, connectorId: Int?, initialSubject: String) {
    val model = LocalModel.current
    val scope = rememberCoroutineScope()
    var subject by rememberSaveable { mutableStateOf(initialSubject) }
    var details by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    EvScaffold(title = stringResource(R.string.report_a_problem), onBack = { model.back() }) { padding ->
        EvList(padding) {
            if (done) {
                section { NoticeRow(stringResource(R.string.thank_you_the_operator_has_been_told_about_this)) }
                return@EvList
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                    stationId?.let { Text(stringResource(R.string.station_x, it), style = MaterialTheme.typography.bodyMedium) }
                    OutlinedTextField(subject, { subject = it }, label = { Text(stringResource(R.string.subject)) }, modifier = Modifier.fillMaxWidth().testTag("report.subject"))
                    OutlinedTextField(details, { details = it }, label = { Text(stringResource(R.string.what_happened)) }, minLines = 4, maxLines = 10, modifier = Modifier.fillMaxWidth().testTag("report.details"))
                    OutlinedTextField(
                        phone, { phone = it }, label = { Text(stringResource(R.string.phone_optional)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    errorMessage?.let { ErrorBanner(it) }
                    PrimaryButton(stringResource(R.string.send), enabled = !working && subject.isNotBlank() && details.isNotBlank(), modifier = Modifier.testTag("report.send")) {
                        scope.launch {
                            working = true
                            try {
                                model.api?.reportProblem(ErrorReportInput(subject, details, stationId, connectorId, phone.ifBlank { null }))
                                done = true
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                errorMessage = model.message(error)
                            } finally {
                                working = false
                            }
                        }
                    }
                }
            }
        }
    }
}
