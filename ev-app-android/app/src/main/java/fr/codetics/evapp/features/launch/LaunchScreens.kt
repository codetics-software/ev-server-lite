// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp.features.launch

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.codetics.evapp.R
import fr.codetics.evapp.core.ConfigurationIssue
import fr.codetics.evapp.demo.DemoSupport
import fr.codetics.evapp.features.LocalModel
import fr.codetics.evapp.ui.ActionRow
import fr.codetics.evapp.ui.AppLogo
import fr.codetics.evapp.ui.EvList
import fr.codetics.evapp.ui.EvScaffold
import fr.codetics.evapp.ui.NoticeRow
import fr.codetics.evapp.ui.RowDivider
import fr.codetics.evapp.ui.Sym
import fr.codetics.evapp.ui.TextRow
import fr.codetics.evapp.ui.section

@Composable
fun LaunchView() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        AppLogo(112.dp)
        CircularProgressIndicator()
        Text(stringResource(R.string.connecting_to_the_server), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun issueMessage(issue: ConfigurationIssue): String =
    when (issue) {
        ConfigurationIssue.MissingFile -> stringResource(R.string.appconfiguration_properties_is_missing_from_the)
        is ConfigurationIssue.Placeholder -> stringResource(R.string.x_still_contains_a_placeholder, issue.key)
        is ConfigurationIssue.InvalidValue -> stringResource(R.string.x_has_an_invalid_value, issue.key)
        is ConfigurationIssue.InsecureUrl -> stringResource(R.string.x_must_use_https, issue.key)
    }

@Composable
fun ConfigurationIssuesView(issues: List<ConfigurationIssue>) {
    val model = LocalModel.current
    EvScaffold(stringResource(R.string.app_not_configured)) { padding ->
        EvList(padding) {
            section {
                TextRow(stringResource(R.string.this_copy_of_the_app_has_not_been_configured_for), MaterialTheme.colorScheme.onSurface)
            }
            section(stringResource(R.string.to_fix)) {
                issues.forEachIndexed { index, issue ->
                    if (index > 0) RowDivider()
                    NoticeRow(issueMessage(issue), R.drawable.sym_cancel_fill, MaterialTheme.colorScheme.error)
                }
            }
            if (DemoSupport.available) {
                section(stringResource(R.string.debug_only), stringResource(R.string.the_demo_uses_simulated_data_and_is_not_included)) {
                    ActionRow(stringResource(R.string.continue_with_the_demo_account), icon = R.drawable.sym_play_circle, modifier = Modifier.testTag("demo.start")) {
                        model.startDemo(signIn = true)
                    }
                    RowDivider()
                    ActionRow(stringResource(R.string.continue_as_a_demo_guest), icon = R.drawable.sym_help, modifier = Modifier.testTag("demo.guest")) {
                        model.startDemo(signIn = false)
                    }
                }
            }
        }
    }
}

@Composable
fun ServerUnavailableView(message: String) {
    val model = LocalModel.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Sym(R.drawable.sym_wifi_off, size = 56.dp)
        Text(stringResource(R.string.server_unavailable), style = MaterialTheme.typography.titleLarge)
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = model::retryConnection) { Text(stringResource(R.string.try_again)) }
    }
}
