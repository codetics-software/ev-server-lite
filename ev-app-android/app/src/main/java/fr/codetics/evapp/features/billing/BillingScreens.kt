// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp.features.billing

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import fr.codetics.evapp.R
import fr.codetics.evapp.core.Formatters
import fr.codetics.evapp.features.LocalModel
import fr.codetics.evapp.models.InvoiceResponse
import fr.codetics.evapp.models.PaymentMethodResponse
import fr.codetics.evapp.ui.ActionRow
import fr.codetics.evapp.ui.ConfirmDialog
import fr.codetics.evapp.ui.EmptyState
import fr.codetics.evapp.ui.ErrorBanner
import fr.codetics.evapp.ui.EvList
import fr.codetics.evapp.ui.EvScaffold
import fr.codetics.evapp.ui.RowDivider
import fr.codetics.evapp.ui.Sym
import fr.codetics.evapp.ui.openInBrowser
import fr.codetics.evapp.ui.item
import fr.codetics.evapp.ui.section
import fr.codetics.evapp.ui.theme.StatusOrange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
private fun invoiceStatusLabel(status: String) =
    when (status) {
        "DRAFT" -> stringResource(R.string.draft)
        "OPEN" -> stringResource(R.string.to_pay)
        "PAID" -> stringResource(R.string.paid)
        "FAILED" -> stringResource(R.string.failed)
        "VOID" -> stringResource(R.string.cancelled)
        "UNCOLLECTIBLE" -> stringResource(R.string.unpaid)
        else -> stringResource(R.string.unknown)
    }

@Composable
fun InvoicesScreen() {
    val model = LocalModel.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var invoices by remember { mutableStateOf<List<InvoiceResponse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            invoices = model.api?.invoices()?.items.orEmpty()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            errorMessage = model.message(error)
        } finally {
            loading = false
        }
    }
    fun open(invoice: InvoiceResponse) {
        scope.launch {
            try {
                val hosted = invoice.hostedUrl
                val target = if (invoice.status == "OPEN" && hosted != null) hosted else model.api?.invoiceDownload(invoice.id)?.url
                if (target?.startsWith("https://") == true) context.openInBrowser(target)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage = model.message(error)
            }
        }
    }
    EvScaffold(stringResource(R.string.invoices), onBack = { model.back() }) { padding ->
        EvList(padding) {
            errorMessage?.let { message -> item { ErrorBanner(message) } }
            if (invoices.isEmpty() && !loading && errorMessage == null) {
                item { EmptyState(R.drawable.sym_receipt_long, stringResource(R.string.no_invoices), stringResource(R.string.your_invoices_will_appear_here)) }
            }
            invoices.forEach { invoice ->
                item(key = invoice.id) {
                    Card(
                        onClick = { open(invoice) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(invoice.number ?: stringResource(R.string.draft_invoice), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(Formatters.minor(invoice.amountMinor, invoice.currency), style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(Formatters.date(invoice.createdAt), style = MaterialTheme.typography.bodySmall)
                                Text(
                                    invoiceStatusLabel(invoice.status),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (invoice.status == "OPEN" || invoice.status == "FAILED") StatusOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PaymentMethodsScreen() {
    val model = LocalModel.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var methods by remember { mutableStateOf<List<PaymentMethodResponse>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<PaymentMethodResponse?>(null) }
    var awaitingCheckout by remember { mutableStateOf(false) }
    suspend fun perform(action: suspend () -> Unit) {
        try {
            action()
            errorMessage = null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            errorMessage = model.message(error)
        }
    }
    suspend fun load() = perform { methods = model.api?.paymentMethods().orEmpty() }
    LaunchedEffect(Unit) { load() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (awaitingCheckout) {
            awaitingCheckout = false
            scope.launch { load() }
        }
    }
    EvScaffold(stringResource(R.string.payment_methods), onBack = { model.back() }) { padding ->
        EvList(padding, onRefresh = { load() }) {
            errorMessage?.let { message -> item { ErrorBanner(message) } }
            if (methods.isNotEmpty()) {
                section {
                    methods.forEachIndexed { index, method ->
                        if (index > 0) RowDivider()
                        val month = method.expMonth
                        val year = method.expYear
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Sym(R.drawable.sym_credit_card_fill, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text("${method.brand.orEmpty().replaceFirstChar(Char::uppercase)} •••• ${method.last4.orEmpty()}", style = MaterialTheme.typography.titleSmall)
                                if (month != null && year != null) {
                                    Text(stringResource(R.string.expires_x_x, month.toString(), year.toString()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (method.default) {
                                Text(stringResource(R.string.default_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            } else {
                                TextButton(onClick = {
                                    scope.launch {
                                        perform { model.api?.setDefaultPaymentMethod(method.id) }
                                        load()
                                    }
                                }) { Text(stringResource(R.string.set_as_default)) }
                            }
                            IconButton(onClick = { deleting = method }) { Sym(R.drawable.sym_delete, description = stringResource(R.string.delete)) }
                        }
                    }
                }
            }
            section(footer = stringResource(R.string.your_card_is_entered_on_stripes_secure_page_and)) {
                ActionRow(stringResource(R.string.add_a_payment_method), icon = R.drawable.sym_add_circle, modifier = Modifier.testTag("payments.add")) {
                    scope.launch {
                        perform {
                            val response = model.api?.paymentMethodCheckout() ?: return@perform
                            awaitingCheckout = true
                            context.openInBrowser(response.url)
                        }
                    }
                }
            }
        }
    }
    deleting?.let { method ->
        ConfirmDialog(stringResource(R.string.delete_this_payment_method), confirm = stringResource(R.string.delete), onDismiss = { deleting = null }) {
            scope.launch {
                perform { model.api?.deletePaymentMethod(method.id) }
                load()
            }
        }
    }
}
