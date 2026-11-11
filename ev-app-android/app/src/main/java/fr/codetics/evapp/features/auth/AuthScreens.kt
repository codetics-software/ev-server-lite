// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp.features.auth

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.codetics.evapp.R
import fr.codetics.evapp.app.Route
import fr.codetics.evapp.core.ApiError
import fr.codetics.evapp.features.LocalModel
import fr.codetics.evapp.models.ChallengeRequest
import fr.codetics.evapp.models.EulaResponse
import fr.codetics.evapp.models.SignupRequest
import fr.codetics.evapp.ui.ActionRow
import fr.codetics.evapp.ui.AppLogo
import fr.codetics.evapp.ui.ErrorBanner
import fr.codetics.evapp.ui.EvList
import fr.codetics.evapp.ui.EvScaffold
import fr.codetics.evapp.ui.LabeledRow
import fr.codetics.evapp.ui.NoticeRow
import fr.codetics.evapp.ui.PrimaryButton
import fr.codetics.evapp.ui.RowDivider
import fr.codetics.evapp.ui.TextRow
import fr.codetics.evapp.ui.openInBrowser
import fr.codetics.evapp.ui.item
import fr.codetics.evapp.ui.section
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun SignInEntryScreen() {
    val model = LocalModel.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var canResend by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val workspace = model.configuration?.workspaceSlug.orEmpty()
    EvScaffold(stringResource(R.string.account)) { padding ->
        EvList(padding) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppLogo(84.dp)
                    Text(model.brandName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.sign_in_to_see_your_badges_sessions_and_vehicles),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(email, { email = it }, label = { Text(stringResource(R.string.email)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth().testTag("signin.email"))
                    OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.password)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth().testTag("signin.password"))
                    errorMessage?.let { ErrorBanner(it) }
                    if (canResend) {
                        TextButton(onClick = {
                            scope.launch {
                                try {
                                    model.api?.resendVerification(ChallengeRequest(workspace, email))
                                    notice = context.getString(R.string.if_this_address_needs_verifying_an_email_has_bee)
                                    errorMessage = null
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    errorMessage = model.message(error)
                                }
                            }
                        }) { Text(stringResource(R.string.send_the_verification_email_again)) }
                    }
                    notice?.let { NoticeRow(it) }
                    if (working) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
                    } else {
                        PrimaryButton(stringResource(R.string.sign_in), enabled = email.isNotEmpty() && password.isNotEmpty(), modifier = Modifier.testTag("signin.submit")) {
                            scope.launch {
                                working = true
                                errorMessage = null
                                canResend = false
                                try {
                                    model.signIn(workspace, email, password)
                                    password = ""
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    errorMessage = model.message(error)
                                    canResend = (error as? ApiError.Server)?.code == "email_not_verified"
                                } finally {
                                    working = false
                                }
                            }
                        }
                    }
                    TextButton(onClick = { model.open(Route.PasswordReset(workspace, email)) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.forgot_password)) }
                    if (model.server?.signupEnabled == true) {
                        TextButton(onClick = { model.open(Route.SignUp) }, modifier = Modifier.fillMaxWidth().testTag("signin.signup")) { Text(stringResource(R.string.create_an_account)) }
                    }
                }
            }
            section {
                ActionRow(stringResource(R.string.about_this_app), icon = R.drawable.sym_info, chevron = true) { model.open(Route.About) }
            }
        }
    }
}

@Composable
fun SignUpScreen() {
    val model = LocalModel.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var accepts by rememberSaveable { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    val canSubmit = !working && name.isNotEmpty() && "@" in email && password.length in 16..128 && accepts
    EvScaffold(stringResource(R.string.create_an_account), onBack = { model.back() }) { padding ->
        EvList(padding) {
            if (done) {
                section { NoticeRow(stringResource(R.string.if_sign_up_is_available_for_this_address_a_verif), R.drawable.sym_mail) }
                return@EvList
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.full_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(email, { email = it }, label = { Text(stringResource(R.string.email)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        password, { password = it }, label = { Text(stringResource(R.string.password)) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        supportingText = { Text(stringResource(R.string.use_between_16_and_128_characters_a_sentence_wor)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(accepts, { accepts = it })
                        Text(stringResource(R.string.i_accept_the_terms_of_use_and_the_privacy_policy), style = MaterialTheme.typography.bodyMedium)
                    }
                    model.configuration?.let { configuration ->
                        TextButton(onClick = { context.openInBrowser(configuration.privacyPolicyUrl) }) { Text(stringResource(R.string.read_the_privacy_policy)) }
                    }
                    errorMessage?.let { ErrorBanner(it) }
                    PrimaryButton(stringResource(R.string.create), enabled = canSubmit) {
                        scope.launch {
                            working = true
                            try {
                                model.api?.signUp(SignupRequest(email.trim(), name, password))
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

@Composable
fun PasswordResetScreen(workspace: String, initialEmail: String) {
    val model = LocalModel.current
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf(initialEmail) }
    var done by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    EvScaffold(stringResource(R.string.forgot_password_98f130), onBack = { model.back() }) { padding ->
        EvList(padding) {
            if (done) {
                section { NoticeRow(stringResource(R.string.if_this_account_exists_a_link_to_choose_a_new_pa), R.drawable.sym_mail) }
                return@EvList
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(email, { email = it }, label = { Text(stringResource(R.string.email)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                    errorMessage?.let { ErrorBanner(it) }
                    PrimaryButton(stringResource(R.string.send), enabled = email.isNotEmpty()) {
                        scope.launch {
                            try {
                                model.api?.requestPasswordReset(ChallengeRequest(workspace, email))
                                done = true
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                errorMessage = model.message(error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EulaScreen() {
    val model = LocalModel.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var eula by remember { mutableStateOf<EulaResponse?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { eula = runCatching { model.api?.eula() }.getOrNull() }
    EvScaffold(stringResource(R.string.terms_of_use)) { padding ->
        EvList(padding) {
            section { TextRow(stringResource(R.string.before_you_continue_please_accept_the_terms_of_u), MaterialTheme.colorScheme.onSurface) }
            eula?.let { current ->
                section(stringResource(R.string.terms)) {
                    LabeledRow(stringResource(R.string.version), current.version)
                    RowDivider()
                    TextRow(current.attribution)
                    RowDivider()
                    ActionRow(current.url, titleColor = MaterialTheme.colorScheme.primary) { context.openInBrowser(current.url) }
                }
            }
            errorMessage?.let { message -> item { ErrorBanner(message) } }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                    PrimaryButton(stringResource(R.string.accept_and_continue), enabled = !working) {
                        scope.launch {
                            working = true
                            try {
                                model.acceptEula()
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                errorMessage = model.message(error)
                            } finally {
                                working = false
                            }
                        }
                    }
                    TextButton(onClick = { scope.launch { model.signOut() } }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.sign_out), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
