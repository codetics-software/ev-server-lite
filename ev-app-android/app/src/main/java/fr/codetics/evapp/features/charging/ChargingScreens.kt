// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp.features.charging

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import fr.codetics.evapp.R
import fr.codetics.evapp.app.AppTab
import fr.codetics.evapp.app.ConnectorStatus
import fr.codetics.evapp.app.GuestOrder
import fr.codetics.evapp.app.PortalLink
import fr.codetics.evapp.app.QrCodeParser
import fr.codetics.evapp.app.Route
import fr.codetics.evapp.app.StartRequest
import fr.codetics.evapp.core.ApiError
import fr.codetics.evapp.core.Formatters
import fr.codetics.evapp.demo.DemoSupport
import fr.codetics.evapp.features.LocalModel
import fr.codetics.evapp.models.ChargingSessionResponse
import fr.codetics.evapp.models.ChargingTariff
import fr.codetics.evapp.models.CheckoutRequest
import fr.codetics.evapp.models.ConsumptionPoint
import fr.codetics.evapp.models.PaymentState
import fr.codetics.evapp.models.PortalEvseResponse
import fr.codetics.evapp.models.PortalPaymentResponse
import fr.codetics.evapp.ui.ActionRow
import fr.codetics.evapp.ui.ConfirmDialog
import fr.codetics.evapp.ui.ErrorBanner
import fr.codetics.evapp.ui.EvList
import fr.codetics.evapp.ui.EvScaffold
import fr.codetics.evapp.ui.LabeledRow
import fr.codetics.evapp.ui.LoadingRow
import fr.codetics.evapp.ui.MetricTile
import fr.codetics.evapp.ui.NoticeRow
import fr.codetics.evapp.ui.PrimaryButton
import fr.codetics.evapp.ui.RowDivider
import fr.codetics.evapp.ui.StatusDot
import fr.codetics.evapp.ui.Sym
import fr.codetics.evapp.ui.TextRow
import fr.codetics.evapp.ui.openInBrowser
import fr.codetics.evapp.ui.item
import fr.codetics.evapp.ui.section
import fr.codetics.evapp.ui.theme.StatusGreen
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChargeScreen() {
    val model = LocalModel.current
    EvScaffold(stringResource(R.string.charge)) { padding ->
        EvList(padding, onRefresh = { model.charging.refresh() }) {
            if (model.charging.active.isNotEmpty()) {
                section(stringResource(R.string.in_progress)) {
                    model.charging.active.forEachIndexed { index, session ->
                        if (index > 0) RowDivider()
                        ActionRow(
                            title = model.stations.station(session.stationId)?.name ?: session.stationId,
                            detail = "${Formatters.power(session.currentPowerKw ?: 0.0)} · ${Formatters.energy(session.energyKwh)}",
                            icon = R.drawable.sym_electric_car_fill,
                            chevron = true,
                        ) { model.open(Route.LiveSession(session.id)) }
                    }
                }
            }
            if (model.guestOrders.orders.isNotEmpty()) {
                section(stringResource(R.string.paid_sessions_on_this_device)) {
                    model.guestOrders.orders.forEachIndexed { index, order ->
                        if (index > 0) RowDivider()
                        ActionRow(order.evseName, Formatters.dateTime(order.createdAt), R.drawable.sym_credit_card, chevron = true) {
                            model.open(Route.GuestSession(order))
                        }
                    }
                }
            }
            section {
                ActionRow(
                    stringResource(R.string.scan_a_qr_code),
                    if (model.isSignedIn) stringResource(R.string.point_the_camera_at_the_code_on_the_station) else stringResource(R.string.no_account_needed_pay_by_card_and_start_charging),
                    R.drawable.sym_qr_code_scanner,
                    modifier = Modifier.testTag("charge.scan"),
                ) { model.open(Route.Scanner) }
                RowDivider()
                ActionRow(
                    stringResource(R.string.enter_a_charging_point_id),
                    stringResource(R.string.type_the_id_printed_on_the_station_such_as),
                    R.drawable.sym_search,
                    modifier = Modifier.testTag("charge.enterId"),
                ) { model.open(Route.ChargingPointLookup()) }
                RowDivider()
                ActionRow(stringResource(R.string.find_a_station), stringResource(R.string.see_the_stations_around_you_on_the_map), R.drawable.sym_map) {
                    model.selectedTab = AppTab.Map
                }
            }
        }
    }
}

@Composable
private fun QrCamera(onCode: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val preview = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    DisposableEffect(owner) {
        val executor = Executors.newSingleThreadExecutor()
        val scanner =
            BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                val provider = future.get()
                val usecase = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }
                val analysis =
                    ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { frame ->
                    val media = frame.image
                    if (media == null) {
                        frame.close()
                        return@setAnalyzer
                    }
                    scanner.process(InputImage.fromMediaImage(media, frame.imageInfo.rotationDegrees))
                        .addOnSuccessListener(ContextCompat.getMainExecutor(context)) { codes ->
                            codes.firstOrNull()?.rawValue?.let(onCode)
                        }
                        .addOnCompleteListener { frame.close() }
                }
                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, usecase, analysis)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            runCatching { future.get().unbindAll() }
            scanner.close()
            executor.shutdown()
        }
    }
    AndroidView(factory = { preview }, modifier = modifier)
}

@Composable
fun QrScannerScreen() {
    val model = LocalModel.current
    val context = LocalContext.current
    var manualCode by rememberSaveable { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val hasCamera = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    LaunchedEffect(Unit) { if (hasCamera && !granted) permission.launch(Manifest.permission.CAMERA) }
    var handled by remember { mutableStateOf(false) }
    var foreign by remember { mutableStateOf<QrCodeParser.Scan.OperatorPortal?>(null) }
    val haptics = LocalHapticFeedback.current
    fun replaceWith(route: Route) {
        handled = true
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        model.back()
        model.open(route, AppTab.Charge)
    }
    fun handle(value: String) {
        val portal = model.configuration?.portalUrl ?: return
        if (handled || foreign != null) return
        when (val scan = QrCodeParser.read(value, portal)) {
            is QrCodeParser.Scan.Portal -> replaceWith(Route.Portal(scan.link))
            is QrCodeParser.Scan.ChargingPoint -> replaceWith(Route.ChargingPointLookup(scan.id))
            is QrCodeParser.Scan.OperatorPortal -> {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                errorMessage = null
                foreign = scan
            }
            is QrCodeParser.Scan.Rejected -> errorMessage = context.getString(scan.failure.message)
        }
    }
    foreign?.let { scan ->
        AlertDialog(
            onDismissRequest = { foreign = null },
            title = { Text(stringResource(R.string.charging_point_of_another_operator)) },
            text = { Text(stringResource(R.string.this_qr_code_opens_x, Uri.parse(scan.url).host ?: scan.url)) },
            confirmButton = {
                TextButton(onClick = { foreign = null; replaceWith(Route.ChargingPointLookup(scan.chargingPoint, scan.url)) }, modifier = Modifier.testTag("scan.foreign.app")) {
                    Text(stringResource(R.string.charge_with_this_app))
                }
            },
            dismissButton = {
                TextButton(onClick = { foreign = null; context.openInBrowser(scan.url) }, modifier = Modifier.testTag("scan.foreign.web")) {
                    Text(stringResource(R.string.open_the_operators_website))
                }
            },
        )
    }
    EvScaffold(stringResource(R.string.scan_a_qr_code), onBack = { model.back() }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (hasCamera && granted) {
                Box(Modifier.fillMaxWidth().weight(1.2f).clipToBounds().background(androidx.compose.ui.graphics.Color.Black), contentAlignment = Alignment.Center) {
                    QrCamera(::handle, Modifier.fillMaxSize().testTag("scan.camera"))
                    Viewfinder(Modifier.size(240.dp))
                    Text(
                        stringResource(R.string.point_the_camera_at_the_qr_code_on_the_charging_point),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = androidx.compose.ui.graphics.Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.TopCenter).padding(16.dp)
                            .clip(RoundedCornerShape(50))
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            Box(Modifier.weight(if (hasCamera && granted) 0.8f else 1f)) {
                EvList(androidx.compose.foundation.layout.PaddingValues()) {
                    if (!hasCamera || !granted) {
                        section { NoticeRow(stringResource(R.string.the_camera_is_not_available_on_this_device_paste), R.drawable.sym_photo_camera) }
                    }
                    item {
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                manualCode, { manualCode = it },
                                label = { Text(stringResource(R.string.charging_point_link)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                modifier = Modifier.weight(1f).testTag("scan.manual"),
                            )
                            Button(onClick = { handle(manualCode) }, enabled = manualCode.isNotBlank()) { Text(stringResource(R.string.open)) }
                        }
                    }
                    errorMessage?.let { message -> item { ErrorBanner(message) } }
                    section {
                        ActionRow(
                            stringResource(R.string.enter_the_charging_point_id),
                            icon = R.drawable.sym_search,
                            chevron = true,
                            modifier = Modifier.testTag("scan.enterId"),
                        ) { replaceWith(Route.ChargingPointLookup()) }
                    }
                    if (DemoSupport.available && model.isDemo) {
                        section(stringResource(R.string.debug_only)) {
                            ActionRow(stringResource(R.string.simulate_scanning_the_demo_qr_code), icon = R.drawable.sym_qr_code_scanner, modifier = Modifier.testTag("scan.demo")) {
                                handle(DemoSupport.PORTAL_LINK)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Viewfinder(modifier: Modifier = Modifier) {
    val color = androidx.compose.ui.graphics.Color.White
    Canvas(modifier) {
        val stroke = 4.dp.toPx()
        val arm = size.minDimension * 0.18f
        val inset = stroke / 2
        val right = size.width - inset
        val bottom = size.height - inset
        listOf(
            listOf(Offset(inset, inset + arm), Offset(inset, inset), Offset(inset + arm, inset)),
            listOf(Offset(right - arm, inset), Offset(right, inset), Offset(right, inset + arm)),
            listOf(Offset(inset, bottom - arm), Offset(inset, bottom), Offset(inset + arm, bottom)),
            listOf(Offset(right - arm, bottom), Offset(right, bottom), Offset(right, bottom - arm)),
        ).forEach { points ->
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                lineTo(points[1].x, points[1].y)
                lineTo(points[2].x, points[2].y)
            }
            drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
    }
}

@Composable
private fun PortalTariffRows(tariff: ChargingTariff) {
    if (tariff.energyPrice.signum() > 0) LabeledRow(stringResource(R.string.energy), "${Formatters.price(tariff.energyPrice, tariff.currency)} / kWh")
    if (tariff.timePrice.signum() > 0) LabeledRow(stringResource(R.string.charging_time), "${Formatters.price(tariff.timePrice, tariff.currency)} / h")
    if (tariff.flatPrice.signum() > 0) LabeledRow(stringResource(R.string.session_fee), Formatters.price(tariff.flatPrice, tariff.currency))
}

@Composable
fun ChargingPointScreen(link: PortalLink) {
    val model = LocalModel.current
    var portal by remember { mutableStateOf<PortalEvseResponse?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    suspend fun load() {
        try {
            portal = model.api?.portal(link.workspace, link.evseId)
            errorMessage = null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            errorMessage = model.message(error)
        }
    }
    LaunchedEffect(link) { load() }
    val canUseBadge = model.isSignedIn && model.workspace?.slug == link.workspace
    EvScaffold(stringResource(R.string.charging_point), onBack = { model.back() }) { padding ->
        EvList(padding, onRefresh = { load() }) {
            val current = portal
            if (current == null) {
                item { errorMessage?.let { ErrorBanner(it) } ?: LoadingRow() }
                return@EvList
            }
            val status = ConnectorStatus.of(current.status)
            section {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(current.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(current.workspace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(status.color)
                        Text(stringResource(status.label), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            section(stringResource(R.string.price)) { PortalTariffRows(current.tariff) }
            if (canUseBadge) {
                section(footer = stringResource(R.string.the_session_is_billed_to_your_account_according)) {
                    ActionRow(
                        stringResource(R.string.start_with_my_badge),
                        icon = R.drawable.sym_contactless_fill,
                        enabled = status.isStartable && current.connected,
                        modifier = Modifier.testTag("portal.badge"),
                    ) { model.open(Route.StartCharge(StartRequest(current.stationId, current.name, current.connectorId))) }
                }
            }
            section(
                footer =
                    if (current.available) stringResource(R.string.you_pay_a_credit_in_advance_whatever_you_do_not)
                    else stringResource(R.string.card_payment_is_not_available_on_this_charging_p)
            ) {
                ActionRow(
                    stringResource(R.string.pay_by_card_without_an_account),
                    icon = R.drawable.sym_credit_card_fill,
                    enabled = current.available,
                    modifier = Modifier.testTag("portal.guest"),
                ) { model.open(Route.Checkout(current)) }
            }
        }
    }
}

@Composable
fun StartChargeScreen(request: StartRequest) {
    val model = LocalModel.current
    val scope = rememberCoroutineScope()
    var badge by rememberSaveable { mutableStateOf<String?>(null) }
    var starting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        model.charging.refresh()
        if (badge == null) badge = model.charging.usableBadges.firstOrNull()?.uid
    }
    val badges = model.charging.usableBadges
    EvScaffold(stringResource(R.string.charge), onBack = { if (!starting) model.back() }) { padding ->
        EvList(padding) {
            section {
                LabeledRow(stringResource(R.string.station), request.stationName)
                RowDivider()
                LabeledRow(stringResource(R.string.connector), request.connectorId.toString())
            }
            if (badges.isEmpty()) {
                section { NoticeRow(stringResource(R.string.you_have_no_active_badge_ask_your_charging_opera), R.drawable.sym_contactless) }
            } else {
                section(stringResource(R.string.badge)) {
                    badges.forEachIndexed { index, token ->
                        if (index > 0) RowDivider()
                        Row(
                            Modifier.fillMaxWidth()
                                .selectable(token.uid == badge) { badge = token.uid }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RadioButton(selected = token.uid == badge, onClick = null)
                            Text(token.visualId ?: token.description ?: token.uid)
                        }
                    }
                }
            }
            section { NoticeRow(stringResource(R.string.plug_in_your_vehicle_before_you_start), R.drawable.sym_power) }
            if (starting) {
                section {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                        Text(stringResource(R.string.waiting_for_the_station_to_confirm))
                    }
                }
            }
            errorMessage?.let { message -> item { ErrorBanner(message) } }
            item {
                PrimaryButton(stringResource(R.string.start), enabled = !starting && badge != null, modifier = Modifier.padding(top = 12.dp).testTag("start.confirm")) {
                    scope.launch {
                        starting = true
                        errorMessage = null
                        try {
                            val session = model.charging.start(request.stationId, request.connectorId, badge.orEmpty())
                            model.openNotification(session.id)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            errorMessage = model.message(error)
                        } finally {
                            starting = false
                        }
                    }
                }
            }
        }
    }
}

private fun generateAccessToken(): String {
    val bytes = ByteArray(32)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}

@Composable
fun GuestCheckoutScreen(portal: PortalEvseResponse) {
    val model = LocalModel.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val minimum = portal.minimumCreditMinor / 100f
    val maximum = portal.maximumCreditMinor / 100f
    var credit by remember { mutableFloatStateOf(20f.coerceIn(minimum, maximum)) }
    var attempt by remember { mutableStateOf<CheckoutRequest?>(null) }
    var pendingOrder by remember { mutableStateOf<GuestOrder?>(null) }
    var working by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val minor = (credit * 100).roundToInt()
    val owner = LocalLifecycleOwner.current
    fun finish(order: GuestOrder) {
        pendingOrder = null
        model.back()
        model.open(Route.GuestSession(order), AppTab.Charge)
    }
    DisposableEffect(owner) {
        var left = false
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && pendingOrder != null) left = true
            if (event == Lifecycle.Event.ON_RESUME && left) {
                left = false
                pendingOrder?.let(::finish)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    EvScaffold(stringResource(R.string.pay_and_charge), onBack = { model.back() }) { padding ->
        EvList(padding) {
            section(stringResource(R.string.charging_point)) {
                TextRow(portal.name, MaterialTheme.colorScheme.onSurface)
                RowDivider()
                PortalTariffRows(portal.tariff)
            }
            section(footer = stringResource(R.string.charging_stops_when_the_credit_runs_out_the_bala)) {
                LabeledRow(stringResource(R.string.credit), Formatters.minor(minor, portal.tariff.currency))
                val steps = (((maximum - minimum) / 5f).roundToInt() - 1).coerceAtLeast(0)
                Slider(
                    value = credit,
                    onValueChange = { credit = it },
                    valueRange = minimum..maximum,
                    steps = steps,
                    modifier = Modifier.padding(horizontal = 16.dp).testTag("checkout.credit"),
                )
            }
            section {
                NoticeRow(stringResource(R.string.plug_in_your_vehicle_before_you_pay), R.drawable.sym_power)
                RowDivider()
                NoticeRow(stringResource(R.string.payment_is_handled_by_stripe_your_card_details_a), R.drawable.sym_lock)
            }
            errorMessage?.let { message -> item { ErrorBanner(message) } }
            item {
                PrimaryButton(
                    stringResource(R.string.pay_x, Formatters.minor(minor, portal.tariff.currency)),
                    enabled = !working,
                    modifier = Modifier.padding(top = 12.dp).testTag("checkout.pay"),
                ) {
                    scope.launch {
                        working = true
                        errorMessage = null
                        try {
                            val api = model.api ?: return@launch
                            val request =
                                attempt?.takeIf { it.creditMinor == minor }
                                    ?: CheckoutRequest(UUID.randomUUID(), generateAccessToken(), minor)
                            attempt = request
                            val response = api.checkout(portal.workspaceSlug, portal.id, request)
                            val order = GuestOrder(response.orderId, request.accessToken, portal.name, Instant.now())
                            model.guestOrders.add(order)
                            if (DemoSupport.available && model.isDemo) {
                                finish(order)
                                return@launch
                            }
                            if (!response.checkoutUrl.startsWith("https://")) throw ApiError.InvalidResponse
                            pendingOrder = order
                            context.openInBrowser(response.checkoutUrl)
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

@Composable
private fun paymentLabel(state: PaymentState) =
    when (state) {
        PaymentState.PendingPayment -> stringResource(R.string.waiting_for_payment)
        PaymentState.Paid -> stringResource(R.string.credit_paid)
        PaymentState.StartRequested -> stringResource(R.string.starting)
        PaymentState.Charging -> stringResource(R.string.charging)
        PaymentState.StopRequested -> stringResource(R.string.stopping)
        PaymentState.Settling -> stringResource(R.string.calculating_the_balance)
        PaymentState.RefundPending -> stringResource(R.string.refund_in_progress)
        PaymentState.Completed -> stringResource(R.string.finished)
        PaymentState.Cancelled -> stringResource(R.string.cancelled)
        PaymentState.Failed -> stringResource(R.string.needs_attention)
        PaymentState.Disputed -> stringResource(R.string.payment_disputed)
    }

@Composable
private fun paymentExplanation(state: PaymentState): String? =
    when (state) {
        PaymentState.PendingPayment -> stringResource(R.string.if_you_closed_the_payment_page_this_request_expi)
        PaymentState.StartRequested -> stringResource(R.string.payment_confirmed_waiting_for_the_station_to_sta)
        PaymentState.StopRequested -> stringResource(R.string.the_stop_request_has_been_sent_if_the_station_do)
        PaymentState.Failed -> stringResource(R.string.the_operator_has_been_told_and_will_take_care_of)
        else -> null
    }

@Composable
fun GuestSessionScreen(order: GuestOrder) {
    val model = LocalModel.current
    val scope = rememberCoroutineScope()
    var payment by remember { mutableStateOf<PortalPaymentResponse?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var stopping by remember { mutableStateOf(false) }
    var confirmsStop by remember { mutableStateOf(false) }
    LaunchedEffect(order.id) {
        while (true) {
            try {
                val latest = model.api?.guestSession(order.id, order.accessToken)
                payment = latest
                errorMessage = null
                if (latest?.state?.isFinished == true) break
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage = model.message(error)
            }
            delay(5.seconds)
        }
    }
    EvScaffold(stringResource(R.string.your_session), onBack = { model.back() }) { padding ->
        EvList(padding) {
            val current = payment
            if (current != null) {
                section {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(order.evseName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(paymentLabel(current.state), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        paymentExplanation(current.state)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                section {
                    LabeledRow(stringResource(R.string.credit_paid), Formatters.minor(current.prepaidMinor, current.currency))
                    RowDivider()
                    LabeledRow(stringResource(R.string.charging_amount), Formatters.minor(current.billedMinor, current.currency))
                    RowDivider()
                    LabeledRow(stringResource(R.string.refunded), Formatters.minor(current.refundedMinor, current.currency))
                }
                current.message?.let { message -> section(stringResource(R.string.operator_information)) { TextRow(message) } }
                if (current.state == PaymentState.Charging) {
                    section {
                        ActionRow(
                            stringResource(R.string.stop_charging),
                            icon = R.drawable.sym_stop_circle_fill,
                            enabled = !stopping,
                            tint = MaterialTheme.colorScheme.error,
                            titleColor = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("guest.stop"),
                        ) { confirmsStop = true }
                    }
                }
                if (current.refundedMinor > 0) {
                    section { TextRow(stringResource(R.string.the_refund_has_been_sent_to_stripe_how_quickly_i)) }
                }
                section {
                    TextRow(stringResource(R.string.reference_x, current.id.toString()))
                    if (current.state.isFinished) {
                        RowDivider()
                        ActionRow(stringResource(R.string.remove_from_this_device), titleColor = MaterialTheme.colorScheme.error) {
                            model.guestOrders.remove(order.id)
                            model.back()
                        }
                    }
                }
            } else if (errorMessage == null) {
                item { LoadingRow() }
            }
            errorMessage?.let { message -> item { ErrorBanner(message) } }
        }
    }
    if (confirmsStop) {
        ConfirmDialog(stringResource(R.string.stop_this_charging_session), confirm = stringResource(R.string.stop_charging), onDismiss = { confirmsStop = false }) {
            scope.launch {
                stopping = true
                try {
                    payment = model.api?.stopGuestSession(order.id, order.accessToken)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    errorMessage = model.message(error)
                } finally {
                    stopping = false
                }
            }
        }
    }
}

@Composable
fun ConsumptionChart(points: List<ConsumptionPoint>, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val values = points.mapNotNull { it.powerKw }
    if (values.size < 2) return
    val top = (values.max() * 1.15).coerceAtLeast(1.0)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.charging_power), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(Formatters.power(values.last()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(Modifier.fillMaxWidth().height(170.dp).testTag("session.chart")) {
            for (index in 0..3) {
                val y = size.height * index / 3f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
            }
            val path = Path()
            values.forEachIndexed { index, value ->
                val x = size.width * index / (values.size - 1)
                val y = size.height * (1 - (value / top).toFloat())
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val area = Path().apply {
                addPath(path)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(line.copy(alpha = 0.35f), line.copy(alpha = 0.03f))))
            drawPath(path, line, style = Stroke(width = 4f, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun LiveSessionGauge(session: ChargingSessionResponse) {
    val model = LocalModel.current
    val track = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val progress = session.stateOfCharge?.let { it / 100f } ?: 1f
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(210.dp).testTag("session.gauge"), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 16.dp.toPx()
                val arc = Size(size.width - stroke, size.height - stroke)
                val origin = Offset(stroke / 2, stroke / 2)
                drawArc(track, 0f, 360f, false, origin, arc, style = Stroke(stroke))
                drawArc(accent, -90f, 360f * progress, false, origin, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (session.isActive) {
                    Text(Formatters.power(session.currentPowerKw ?: 0.0), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                } else {
                    Sym(R.drawable.sym_check_circle_fill, tint = StatusGreen, size = 44.dp)
                }
                session.stateOfCharge?.let {
                    Text(stringResource(R.string.battery_x, it.toString()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text(model.stations.station(session.stationId)?.name ?: session.stationId, style = MaterialTheme.typography.titleMedium)
        if (!session.isActive) {
            session.stopReason?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun LiveSessionScreen(sessionId: Int) {
    val model = LocalModel.current
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf<ChargingSessionResponse?>(null) }
    var points by remember { mutableStateOf<List<ConsumptionPoint>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var stopping by remember { mutableStateOf(false) }
    var stopRequested by remember { mutableStateOf(false) }
    var confirmsStop by remember { mutableStateOf(false) }
    suspend fun refresh() {
        val api = model.api ?: return
        try {
            coroutineScope {
                val current = async { api.session(sessionId) }
                val curve = async { api.consumptions(sessionId) }
                session = current.await()
                points = curve.await()
            }
            errorMessage = null
        } catch (error: CancellationException) {
            throw error
        } catch (_: ApiError.Unauthorized) {
            model.sessionExpired()
        } catch (error: Exception) {
            errorMessage = model.message(error)
        }
    }
    LaunchedEffect(sessionId) {
        while (true) {
            refresh()
            if (session?.isActive == false) break
            delay(5.seconds)
        }
    }
    val title = if (session?.isActive == false) stringResource(R.string.session_finished) else stringResource(R.string.charging)
    EvScaffold(title, onBack = { model.back() }) { padding ->
        EvList(padding) {
            val current = session
            if (current != null) {
                item { Box(Modifier.padding(vertical = 12.dp)) { LiveSessionGauge(current) } }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricTile(stringResource(R.string.energy), Formatters.energy(current.energyKwh), R.drawable.sym_bolt_fill, Modifier.weight(1f))
                            MetricTile(stringResource(R.string.duration), Formatters.duration(current.durationSeconds), R.drawable.sym_timer, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricTile(
                                if (current.isActive) stringResource(R.string.estimated_price) else stringResource(R.string.price),
                                Formatters.price(current.price, current.currency),
                                R.drawable.sym_euro_symbol,
                                Modifier.weight(1f),
                            )
                            MetricTile(stringResource(R.string.connector), current.connectorId.toString(), R.drawable.sym_power, Modifier.weight(1f))
                        }
                    }
                }
                if (current.isActive) {
                    item {
                        Button(
                            onClick = { confirmsStop = true },
                            enabled = !stopping,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(52.dp).testTag("session.stop"),
                        ) {
                            Sym(R.drawable.sym_stop_circle_fill, tint = MaterialTheme.colorScheme.onError, size = 20.dp)
                            Text(if (stopping) stringResource(R.string.stopping_47b5a5) else stringResource(R.string.stop_charging), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    if (stopRequested) {
                        item {
                            Text(
                                stringResource(R.string.the_stop_request_has_been_sent_if_the_station_do),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
                if (points.size > 1) {
                    section { ConsumptionChart(points, Modifier.padding(16.dp)) }
                }
            } else if (errorMessage == null) {
                item { LoadingRow() }
            }
            errorMessage?.let { message -> item { ErrorBanner(message) } }
        }
    }
    if (confirmsStop) {
        ConfirmDialog(stringResource(R.string.stop_this_charging_session), confirm = stringResource(R.string.stop_charging), onDismiss = { confirmsStop = false }) {
            scope.launch {
                stopping = true
                try {
                    model.api?.stopSession(sessionId)
                    stopRequested = true
                    refresh()
                    model.charging.refresh()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    errorMessage = model.message(error)
                } finally {
                    stopping = false
                }
            }
        }
    }
}
