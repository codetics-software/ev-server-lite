// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp.features.account

import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.codetics.evapp.BuildConfig
import fr.codetics.evapp.R
import fr.codetics.evapp.app.Route
import fr.codetics.evapp.features.LocalModel
import fr.codetics.evapp.features.map.usesGoogleMaps
import fr.codetics.evapp.models.ChallengeRequest
import fr.codetics.evapp.models.ProfileInput
import fr.codetics.evapp.models.UserRole
import fr.codetics.evapp.models.UserSiteResponse
import fr.codetics.evapp.ui.ActionRow
import fr.codetics.evapp.ui.AppLogo
import fr.codetics.evapp.ui.ConfirmDialog
import fr.codetics.evapp.ui.EmptyState
import fr.codetics.evapp.ui.ErrorBanner
import fr.codetics.evapp.ui.EvList
import fr.codetics.evapp.ui.EvScaffold
import fr.codetics.evapp.ui.LabeledRow
import fr.codetics.evapp.ui.NoticeRow
import fr.codetics.evapp.ui.PrimaryButton
import fr.codetics.evapp.ui.RowDivider
import fr.codetics.evapp.ui.Sym
import fr.codetics.evapp.ui.TextRow
import fr.codetics.evapp.ui.openInBrowser
import fr.codetics.evapp.ui.item
import fr.codetics.evapp.ui.section
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun AccountScreen() {
    val model = LocalModel.current
    val scope = rememberCoroutineScope()
    var confirmsEverywhere by remember { mutableStateOf(false) }
    var confirmsDeletion by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    EvScaffold(stringResource(R.string.account)) { padding ->
        EvList(padding) {
            section {
                ActionRow(
                    model.principal?.name.orEmpty(),
                    model.principal?.email,
                    R.drawable.sym_account_circle,
                    chevron = true,
                    modifier = Modifier.testTag("account.profile"),
                ) { model.open(Route.Profile) }
            }
            section {
                if (model.components?.cars != false) {
                    ActionRow(stringResource(R.string.my_vehicles), icon = R.drawable.sym_directions_car, chevron = true, modifier = Modifier.testTag("account.vehicles")) { model.open(Route.Vehicles) }
                    RowDivider()
                }
                if (model.components?.billing != false) {
                    ActionRow(stringResource(R.string.payment_methods), icon = R.drawable.sym_credit_card, chevron = true, modifier = Modifier.testTag("account.payments")) { model.open(Route.PaymentMethods) }
                    RowDivider()
                    ActionRow(stringResource(R.string.invoices), icon = R.drawable.sym_receipt_long, chevron = true, modifier = Modifier.testTag("account.invoices")) { model.open(Route.Invoices) }
                    RowDivider()
                }
                ActionRow(stringResource(R.string.notifications), icon = R.drawable.sym_notifications, chevron = true, modifier = Modifier.testTag("account.notifications")) { model.open(Route.Notifications) }
                RowDivider()
                ActionRow(stringResource(R.string.my_sites), icon = R.drawable.sym_domain, chevron = true) { model.open(Route.Sites) }
            }
            section {
                ActionRow(stringResource(R.string.report_a_problem), icon = R.drawable.sym_feedback) { model.open(Route.Report()) }
                RowDivider()
                ActionRow(stringResource(R.string.about), icon = R.drawable.sym_info, chevron = true, modifier = Modifier.testTag("account.about")) { model.open(Route.About) }
            }
            section {
                ActionRow(stringResource(R.string.sign_out), icon = R.drawable.sym_logout, modifier = Modifier.testTag("account.signout")) { scope.launch { model.signOut() } }
                RowDivider()
                ActionRow(stringResource(R.string.sign_out_from_all_devices), icon = R.drawable.sym_logout) { confirmsEverywhere = true }
            }
            section {
                if (model.principal?.role != UserRole.ADMIN) {
                    ActionRow(
                        stringResource(R.string.delete_my_account),
                        icon = R.drawable.sym_delete,
                        tint = MaterialTheme.colorScheme.error,
                        titleColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("account.delete"),
                    ) { confirmsDeletion = true }
                }
                errorMessage?.let { Box(Modifier.padding(horizontal = 12.dp)) { ErrorBanner(it) } }
            }
        }
    }
    if (confirmsEverywhere) {
        ConfirmDialog(stringResource(R.string.sign_out_from_all_your_devices), confirm = stringResource(R.string.sign_out_everywhere), onDismiss = { confirmsEverywhere = false }) {
            scope.launch { model.signOut(everywhere = true) }
        }
    }
    if (confirmsDeletion) {
        ConfirmDialog(
            stringResource(R.string.delete_your_account_permanently),
            stringResource(R.string.your_profile_vehicles_photos_and_device_registra),
            stringResource(R.string.delete_my_account),
            onDismiss = { confirmsDeletion = false },
        ) {
            scope.launch {
                try {
                    model.deleteAccount()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    errorMessage = model.message(error)
                }
            }
        }
    }
}

@Composable
fun MySitesScreen() {
    val model = LocalModel.current
    var sites by remember { mutableStateOf<List<UserSiteResponse>?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            sites = model.api?.sites().orEmpty()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            errorMessage = model.message(error)
        }
    }
    EvScaffold(stringResource(R.string.my_sites), onBack = { model.back() }) { padding ->
        EvList(padding) {
            errorMessage?.let { message -> item { ErrorBanner(message) } }
            val current = sites
            if (current != null && current.isEmpty() && errorMessage == null) {
                item { EmptyState(R.drawable.sym_domain, stringResource(R.string.no_private_sites), stringResource(R.string.you_can_use_every_station_open_to_all_drivers_on)) }
            } else if (!current.isNullOrEmpty()) {
                section {
                    current.forEachIndexed { index, site ->
                        if (index > 0) RowDivider()
                        NoticeRow(site.name, R.drawable.sym_domain)
                    }
                }
            }
        }
    }
}

private val profileLocales: List<Pair<String, Int>> =
    listOf(
        "fr" to R.string.french,
        "en" to R.string.english,
        "es" to R.string.spanish,
    )

@Composable
fun ProfileScreen() {
    val model = LocalModel.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var locale by rememberSaveable { mutableStateOf("fr") }
    var email by rememberSaveable { mutableStateOf("") }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (loaded) return@LaunchedEffect
        val profile = runCatching { model.api?.profile() }.getOrNull() ?: return@LaunchedEffect
        name = profile.displayName
        phone = profile.phone.orEmpty()
        locale = profile.locale
        email = profile.email
        loaded = true
    }
    fun perform(success: Int, action: suspend () -> Unit) {
        scope.launch {
            try {
                action()
                notice = context.getString(success)
                errorMessage = null
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage = model.message(error)
            }
        }
    }
    EvScaffold(stringResource(R.string.profile), onBack = { model.back() }) { padding ->
        EvList(padding) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.full_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(email, {}, label = { Text(stringResource(R.string.email)) }, readOnly = true, enabled = false, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(phone, { phone = it }, label = { Text(stringResource(R.string.phone)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.language))
                        Box {
                            OutlinedButton(onClick = { expanded = true }) {
                                Text(stringResource(profileLocales.firstOrNull { it.first == locale }?.second ?: R.string.french))
                            }
                            DropdownMenu(expanded, { expanded = false }) {
                                profileLocales.forEach { (code, label) ->
                                    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                                        locale = code
                                        expanded = false
                                    })
                                }
                            }
                        }
                    }
                    PrimaryButton(stringResource(R.string.save), enabled = name.isNotBlank(), modifier = Modifier.testTag("profile.save")) {
                        perform(R.string.profile_saved) {
                            model.api?.updateProfile(ProfileInput(name.trim(), phone.ifBlank { null }, locale))
                            model.refreshPrincipal()
                        }
                    }
                    TextButton(onClick = {
                        perform(R.string.a_link_to_choose_a_new_password_has_been_sent_to) {
                            model.api?.requestPasswordReset(ChallengeRequest(model.workspace?.slug.orEmpty(), email))
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.change_my_password)) }
                    notice?.let { NoticeRow(it) }
                    errorMessage?.let { ErrorBanner(it) }
                }
            }
        }
    }
}

@Composable
fun AboutScreen() {
    val model = LocalModel.current
    val context = LocalContext.current
    EvScaffold(stringResource(R.string.about), onBack = { model.back() }) { padding ->
        EvList(padding) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppLogo(76.dp)
                    Text(model.brandName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.version_x, "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            section(stringResource(R.string.credits)) {
                Box(Modifier.testTag("about.attribution")) { TextRow("EV Supervision Lite — built by Kevin Vavelin, Codetics", MaterialTheme.colorScheme.onSurface) }
                RowDivider()
                ActionRow("https://codetics.fr", modifier = Modifier.testTag("about.link"), titleColor = MaterialTheme.colorScheme.primary) { context.openInBrowser("https://codetics.fr") }
                RowDivider()
                TextRow("Copyright © 2026 Kevin Vavelin, Codetics")
                RowDivider()
                TextRow("Licence d’attribution Codetics 1.0 (LicenseRef-Codetics-Attribution-1.0)")
            }
            section(stringResource(R.string.map)) {
                TextRow(
                    if (usesGoogleMaps(context, model.configuration?.googleMapsApiKey)) "Google Maps"
                    else "© OpenStreetMap contributors, OpenMapTiles, OpenFreeMap, MapLibre"
                )
                RowDivider()
                TextRow("Material Symbols © Google, Apache License 2.0")
            }
            model.configuration?.let { configuration ->
                section(stringResource(R.string.support)) {
                    ActionRow(configuration.supportEmail, icon = R.drawable.sym_mail) {
                        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${configuration.supportEmail}")))
                    }
                    RowDivider()
                    ActionRow(stringResource(R.string.privacy_policy), icon = R.drawable.sym_open_in_new) { context.openInBrowser(configuration.privacyPolicyUrl) }
                }
            }
        }
    }
}
