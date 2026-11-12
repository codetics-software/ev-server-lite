// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp.features.badges

import androidx.compose.ui.res.stringResource
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import androidx.activity.compose.LocalActivity
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.google.android.gms.pay.Pay
import com.google.android.gms.pay.PayApiAvailabilityStatus
import com.google.android.gms.pay.PayClient
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import fr.codetics.evapp.R
import fr.codetics.evapp.app.BadgeMatcher
import fr.codetics.evapp.app.Route
import fr.codetics.evapp.core.Formatters
import fr.codetics.evapp.demo.DemoSupport
import fr.codetics.evapp.features.LocalModel
import fr.codetics.evapp.models.CardOfferResponse
import fr.codetics.evapp.models.CardOrderResponse
import fr.codetics.evapp.models.CardOrderState
import fr.codetics.evapp.models.ChargingTokenResponse
import fr.codetics.evapp.ui.ActionRow
import fr.codetics.evapp.ui.AppLogo
import fr.codetics.evapp.ui.ErrorBanner
import fr.codetics.evapp.ui.EvList
import fr.codetics.evapp.ui.EvScaffold
import fr.codetics.evapp.ui.LabeledRow
import fr.codetics.evapp.ui.LoadingRow
import fr.codetics.evapp.ui.NoticeRow
import fr.codetics.evapp.ui.PrimaryButton
import fr.codetics.evapp.ui.RowDivider
import fr.codetics.evapp.ui.Sym
import fr.codetics.evapp.ui.TextRow
import fr.codetics.evapp.ui.openInBrowser
import fr.codetics.evapp.ui.item
import fr.codetics.evapp.ui.section
import fr.codetics.evapp.ui.theme.StatusBlue
import fr.codetics.evapp.ui.theme.StatusGreen
import fr.codetics.evapp.ui.theme.StatusOrange
import fr.codetics.evapp.ui.theme.StatusRed
import fr.codetics.evapp.ui.theme.WalletGreen
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await

object NfcBadgeReader {
    fun isAvailable(activity: Activity?): Boolean = activity != null && NfcAdapter.getDefaultAdapter(activity) != null

    fun isEnabled(activity: Activity?): Boolean = activity != null && NfcAdapter.getDefaultAdapter(activity)?.isEnabled == true

    fun identifier(id: ByteArray): String = id.joinToString("") { "%02X".format(it) }

    suspend fun scan(activity: Activity): String? {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return null
        return suspendCancellableCoroutine { continuation ->
            val flags =
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
            adapter.enableReaderMode(
                activity,
                { tag: Tag ->
                    activity.runOnUiThread {
                        adapter.disableReaderMode(activity)
                        if (continuation.isActive) continuation.resume(identifier(tag.id))
                    }
                },
                flags,
                Bundle(),
            )
            continuation.invokeOnCancellation { activity.runOnUiThread { adapter.disableReaderMode(activity) } }
        }
    }
}

object WalletResultBus {
    const val REQUEST_CODE = 4212
    var onResult: ((Int) -> Unit)? = null

    fun onActivityResult(requestCode: Int, resultCode: Int) {
        if (requestCode == REQUEST_CODE) onResult?.invoke(resultCode)
    }
}

@Composable
private fun NfcScanDialog(onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Sym(R.drawable.sym_contactless_fill, tint = MaterialTheme.colorScheme.primary, size = 40.dp) },
        title = { Text(stringResource(R.string.ready_to_scan)) },
        text = { Text(stringResource(R.string.hold_your_charging_card_near_the_back_of_the_pho)) },
        confirmButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun NfcUnavailableDialog(onDismiss: () -> Unit, onSimulate: (String) -> Unit) {
    val model = LocalModel.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Sym(R.drawable.sym_contactless, size = 40.dp) },
        title = { Text("NFC") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.this_device_cannot_read_nfc_cards))
                if (DemoSupport.available && model.isDemo) {
                    Text(stringResource(R.string.debug_only), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp))
                    TextButton(onClick = { onSimulate(DemoSupport.OWN_CARD) }, modifier = Modifier.testTag("nfc.simulate.own")) { Text(stringResource(R.string.simulate_my_registered_card)) }
                    TextButton(onClick = { onSimulate(DemoSupport.ORDERED_CARD) }, modifier = Modifier.testTag("nfc.simulate.ordered")) { Text(stringResource(R.string.simulate_my_ordered_card)) }
                    TextButton(onClick = { onSimulate("04FFEEDDCCBB80") }, modifier = Modifier.testTag("nfc.simulate.unknown")) { Text(stringResource(R.string.simulate_an_unknown_card)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
fun BadgesScreen() {
    val model = LocalModel.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    var outcome by remember { mutableStateOf<BadgeMatcher.Outcome?>(null) }
    var showsSimulator by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var offer by remember { mutableStateOf<CardOfferResponse?>(null) }
    var orders by remember { mutableStateOf<List<CardOrderResponse>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    suspend fun load() {
        model.charging.refresh()
        offer = runCatching { model.api?.cardOffer() }.getOrNull()
        orders = runCatching { model.api?.cardOrders() }.getOrNull().orEmpty()
    }
    LaunchedEffect(Unit) { load() }
    val nfcDisabled = stringResource(R.string.nfc_is_turned_off_turn_it_on_in_your_phone_setti)
    fun evaluate(uid: String) {
        outcome = BadgeMatcher.match(uid, model.charging.tokens)
    }
    val tokens = model.charging.tokens.filterNot { it.uid.uppercase().startsWith("VID:") }
    EvScaffold(stringResource(R.string.badges)) { padding ->
        EvList(padding, onRefresh = { load() }) {
            section {
                ActionRow(
                    stringResource(R.string.check_a_card_with_nfc),
                    stringResource(R.string.hold_your_card_near_the_back_of_the_phone_to_see),
                    R.drawable.sym_contactless_fill,
                    enabled = scanning == null,
                    modifier = Modifier.testTag("badges.scan"),
                ) {
                    errorMessage = null
                    if (!NfcBadgeReader.isAvailable(activity)) {
                        showsSimulator = true
                    } else if (!NfcBadgeReader.isEnabled(activity)) {
                        errorMessage = nfcDisabled
                    } else {
                        scanning = scope.launch {
                            try {
                                val uid = NfcBadgeReader.scan(activity!!)
                                model.charging.refresh()
                                uid?.let(::evaluate)
                            } finally {
                                scanning = null
                            }
                        }
                    }
                }
                errorMessage?.let { Box(Modifier.padding(horizontal = 12.dp)) { ErrorBanner(it) } }
            }
            outcome?.let { current -> section(stringResource(R.string.result)) { NfcResult(current) } }
            section(stringResource(R.string.charging_card)) { CardPurchase(offer, orders) }
            section(stringResource(R.string.my_badges)) {
                if (tokens.isEmpty()) TextRow(stringResource(R.string.no_badge_is_registered_on_your_account_yet_ask_y))
                tokens.forEachIndexed { index, token ->
                    if (index > 0) RowDivider()
                    val status = if (token.enabled) stringResource(R.string.active) else stringResource(R.string.disabled)
                    val defaultLabel = stringResource(R.string.default_label)
                    ActionRow(
                        title = token.visualId ?: token.description ?: token.uid,
                        detail = if (token.default) "$status · $defaultLabel" else status,
                        icon = R.drawable.sym_credit_card_fill,
                        tint = if (token.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        chevron = true,
                    ) { model.open(Route.BadgeDetail(token)) }
                }
            }
        }
    }
    if (scanning != null) NfcScanDialog { scanning?.cancel() }
    if (showsSimulator) {
        NfcUnavailableDialog(onDismiss = { showsSimulator = false }) {
            showsSimulator = false
            evaluate(it)
        }
    }
}

@Composable
private fun ResultRow(title: String, @DrawableRes icon: Int, color: Color, detail: String) {
    Row(Modifier.fillMaxWidth().padding(16.dp).testTag("nfc.result"), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Sym(icon, tint = color, size = 28.dp)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NfcResult(outcome: BadgeMatcher.Outcome) {
    val model = LocalModel.current
    when (outcome) {
        is BadgeMatcher.Outcome.Registered ->
            ResultRow(
                stringResource(R.string.this_card_is_registered_on_your_account), R.drawable.sym_verified_fill, StatusGreen,
                stringResource(R.string.badge_x_held_by_x, outcome.token.visualId ?: outcome.token.uid, outcome.token.userName ?: model.principal?.name.orEmpty()),
            )
        is BadgeMatcher.Outcome.Disabled ->
            ResultRow(
                stringResource(R.string.this_card_is_on_your_account_but_disabled), R.drawable.sym_pause_circle_fill, StatusOrange,
                stringResource(R.string.badge_x_contact_your_operator_to_enable_it_again, outcome.token.visualId ?: outcome.token.uid),
            )
        is BadgeMatcher.Outcome.Expired ->
            ResultRow(
                stringResource(R.string.this_card_is_on_your_account_but_has_expired), R.drawable.sym_event_busy, StatusOrange,
                stringResource(R.string.badge_x_contact_your_operator_to_renew_it, outcome.token.visualId ?: outcome.token.uid),
            )
        is BadgeMatcher.Outcome.NotRegistered -> {
            ResultRow(
                stringResource(R.string.this_card_is_not_registered_on_your_account), R.drawable.sym_cancel_fill, StatusRed,
                stringResource(R.string.card_number_x_it_may_be_unknown_to_this_network, outcome.uid),
            )
            RowDivider()
            val subject = stringResource(R.string.please_add_my_card_x, outcome.uid)
            ActionRow(stringResource(R.string.ask_my_operator_to_add_it), icon = R.drawable.sym_feedback) {
                model.open(Route.Report(subject = subject))
            }
        }
    }
}

@Composable
private fun orderLabel(state: CardOrderState) =
    when (state) {
        CardOrderState.PendingPayment -> stringResource(R.string.waiting_for_payment)
        CardOrderState.Paid -> stringResource(R.string.paid_being_prepared)
        CardOrderState.Shipped -> stringResource(R.string.ready_to_activate)
        CardOrderState.Activated -> stringResource(R.string.activated)
        CardOrderState.Cancelled -> stringResource(R.string.cancelled)
    }

@Composable
private fun orderColor(state: CardOrderState) =
    when (state) {
        CardOrderState.PendingPayment, CardOrderState.Cancelled -> MaterialTheme.colorScheme.onSurfaceVariant
        CardOrderState.Paid -> StatusOrange
        CardOrderState.Shipped -> StatusBlue
        CardOrderState.Activated -> StatusGreen
    }

@Composable
private fun CardPurchase(offer: CardOfferResponse?, orders: List<CardOrderResponse>) {
    val model = LocalModel.current
    val context = LocalContext.current
    if (orders.any { it.state == CardOrderState.Shipped }) {
        NoticeRow(stringResource(R.string.your_card_is_ready_activate_it_when_you_receive), R.drawable.sym_verified)
        RowDivider()
    }
    ActionRow(
        stringResource(R.string.activate_a_card),
        stringResource(R.string.scan_your_new_card_or_enter_the_number_printed_o),
        R.drawable.sym_add_card,
        modifier = Modifier.testTag("cards.activate"),
    ) { model.open(Route.ActivateCard) }
    val portal = model.configuration?.portalUrl
    if (offer?.available == true && portal != null) {
        RowDivider()
        val price = offer.priceMinor
        ActionRow(
            stringResource(R.string.order_a_card),
            if (price != null) stringResource(R.string.on_your_online_account_for_x, Formatters.minor(price, offer.currency)) else stringResource(R.string.on_your_online_account),
            R.drawable.sym_shopping_cart,
            modifier = Modifier.testTag("cards.order"),
        ) { context.openInBrowser(portal.trimEnd('/') + "/card-orders") }
    }
    orders.forEach { order ->
        RowDivider()
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(Formatters.date(order.createdAt), style = MaterialTheme.typography.bodyMedium)
            Text(if (order.refunded) stringResource(R.string.refunded) else orderLabel(order.state), style = MaterialTheme.typography.bodyMedium, color = orderColor(order.state))
        }
    }
}

@Composable
fun ActivateCardScreen() {
    val model = LocalModel.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    var number by rememberSaveable { mutableStateOf("") }
    var activated by remember { mutableStateOf<ChargingTokenResponse?>(null) }
    var working by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showsSimulator by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun activate(value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || working) return
        scope.launch {
            working = true
            errorMessage = null
            try {
                activated = model.api?.activateCard(trimmed)
                model.charging.refresh()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage = model.message(error)
            } finally {
                working = false
            }
        }
    }
    val nfcOffered = NfcBadgeReader.isAvailable(activity) || (DemoSupport.available && model.isDemo)
    EvScaffold(stringResource(R.string.activate_a_card), onBack = { model.back() }) { padding ->
        EvList(padding) {
            val done = activated
            if (done != null) {
                section {
                    Box(Modifier.testTag("activation.success")) {
                        ResultRow(
                            stringResource(R.string.your_card_is_active), R.drawable.sym_verified_fill, StatusGreen,
                            stringResource(R.string.card_x_can_now_start_charging_on_this_network, done.visualId ?: done.uid),
                        )
                    }
                }
                return@EvList
            }
            if (nfcOffered) {
                section {
                    ActionRow(
                        stringResource(R.string.scan_my_card), stringResource(R.string.hold_the_card_near_the_back_of_the_phone), R.drawable.sym_contactless_fill,
                        enabled = !working, modifier = Modifier.testTag("activation.scan"),
                    ) {
                        if (NfcBadgeReader.isAvailable(activity)) {
                            scanning = scope.launch {
                                try {
                                    NfcBadgeReader.scan(activity!!)?.let(::activate)
                                } finally {
                                    scanning = null
                                }
                            }
                        } else {
                            showsSimulator = true
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
                    Text(stringResource(R.string.or_enter_its_number), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(
                        number, { number = it },
                        label = { Text(stringResource(R.string.card_number)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { activate(number) }),
                        modifier = Modifier.fillMaxWidth().testTag("activation.number"),
                    )
                    PrimaryButton(stringResource(R.string.activate), enabled = !working && number.isNotBlank(), modifier = Modifier.testTag("activation.submit")) { activate(number) }
                    Text(
                        stringResource(R.string.use_the_number_printed_on_the_card_or_given_by_y),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (working) LoadingRow()
                    errorMessage?.let { ErrorBanner(it) }
                }
            }
        }
    }
    if (scanning != null) NfcScanDialog { scanning?.cancel() }
    if (showsSimulator) {
        NfcUnavailableDialog(onDismiss = { showsSimulator = false }) {
            showsSimulator = false
            activate(it)
        }
    }
}

fun qrBitmap(text: String, size: Int = 320): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size) { if (matrix[it % size, it / size]) AndroidColor.BLACK else AndroidColor.WHITE }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

@Composable
private fun WalletPassPreview(token: ChargingTokenResponse, holder: String, network: String) {
    val code = remember(token.uid) { qrBitmap(token.uid).asImageBitmap() }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(WalletGreen).padding(18.dp).testTag("wallet.preview"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppLogo(32.dp)
            Text(network, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Sym(R.drawable.sym_contactless, tint = Color.White)
        }
        Column {
            Text(stringResource(R.string.driver).uppercase(), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
            Text(holder, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        Column {
            Text(stringResource(R.string.badge).uppercase(), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
            Text(token.visualId ?: token.uid, color = Color.White, fontFamily = FontFamily.Monospace)
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Image(
                code,
                stringResource(R.string.badge_qr_code),
                filterQuality = FilterQuality.None,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White).padding(8.dp).size(112.dp),
            )
        }
    }
}

@Composable
fun BadgeDetailScreen(token: ChargingTokenResponse) {
    val model = LocalModel.current
    val activity = LocalActivity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var added by remember { mutableStateOf(false) }
    val payClient = remember(activity) { activity?.let { Pay.getClient(it) } }
    var walletApi by remember { mutableStateOf(false) }
    LaunchedEffect(payClient) {
        walletApi =
            runCatching {
                    payClient?.getPayApiAvailabilityStatus(PayClient.RequestType.SAVE_PASSES)?.await() ==
                        PayApiAvailabilityStatus.AVAILABLE
                }
                .getOrDefault(false)
    }
    val walletError = stringResource(R.string.google_wallet_could_not_save_this_pass)
    DisposableEffect(Unit) {
        WalletResultBus.onResult = { result ->
            when (result) {
                Activity.RESULT_OK -> added = true
                Activity.RESULT_CANCELED -> Unit
                else -> errorMessage = walletError
            }
        }
        onDispose { WalletResultBus.onResult = null }
    }
    val walletEnabled = model.server?.googleWalletEnabled == true
    EvScaffold(token.visualId ?: token.uid, onBack = { model.back() }) { padding ->
        EvList(padding) {
            item {
                WalletPassPreview(token, model.principal?.name.orEmpty(), model.workspace?.name ?: model.brandName)
            }
            section(stringResource(R.string.details)) {
                LabeledRow(stringResource(R.string.number), token.uid)
                token.visualId?.let {
                    RowDivider()
                    LabeledRow(stringResource(R.string.printed_number), it)
                }
                RowDivider()
                LabeledRow(stringResource(R.string.status), if (token.enabled) stringResource(R.string.active) else stringResource(R.string.disabled))
                token.parentIdTag?.let {
                    RowDivider()
                    LabeledRow(stringResource(R.string.badge_group), it)
                }
                token.expiresAt?.let {
                    RowDivider()
                    LabeledRow(stringResource(R.string.expires), Formatters.date(it))
                }
                token.lastUsedAt?.let {
                    RowDivider()
                    LabeledRow(stringResource(R.string.last_used), Formatters.dateTime(it))
                }
            }
            section(
                "Google Wallet",
                if (model.server?.googleWalletNfcEnabled == true) {
                    stringResource(R.string.the_pass_is_sent_over_nfc_only_to_readers_compat)
                } else {
                    stringResource(R.string.this_pass_shows_your_badge_as_a_qr_code_contact)
                },
            ) {
                if (walletEnabled) {
                    Box(Modifier.padding(12.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    loading = true
                                    errorMessage = null
                                    try {
                                        val pass = model.api?.googleWalletPass(token.uid) ?: return@launch
                                        val current = activity
                                        if (walletApi && current != null) {
                                            payClient?.savePassesJwt(pass.jwt, current, WalletResultBus.REQUEST_CODE)
                                        } else {
                                            context.openInBrowser(pass.saveUrl)
                                        }
                                    } catch (error: CancellationException) {
                                        throw error
                                    } catch (error: Exception) {
                                        errorMessage = model.message(error)
                                    } finally {
                                        loading = false
                                    }
                                }
                            },
                            enabled = !loading && token.enabled,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth().testTag("wallet.add"),
                        ) {
                            Sym(R.drawable.sym_wallet, tint = Color.White, size = 20.dp)
                            Text(stringResource(R.string.add_to_google_wallet), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                } else {
                    NoticeRow(stringResource(R.string.google_wallet_is_not_available_for_this_badge_on), R.drawable.sym_wallet)
                }
                if (added) NoticeRow(stringResource(R.string.the_pass_has_been_added_to_google_wallet))
                if (loading) LoadingRow()
                errorMessage?.let { Box(Modifier.padding(horizontal = 12.dp)) { ErrorBanner(it) } }
            }
        }
    }
}
