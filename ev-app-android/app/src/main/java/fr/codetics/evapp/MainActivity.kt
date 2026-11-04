// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import fr.codetics.evapp.features.RootView
import fr.codetics.evapp.features.badges.WalletResultBus
import fr.codetics.evapp.ui.theme.EVAppTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val model = (application as EvApp).model
        model.bootstrap(
            demo = BuildConfig.DEBUG && intent.getBooleanExtra("demoAccount", false),
            demoGuest = BuildConfig.DEBUG && intent.getBooleanExtra("demoGuest", false),
            overrides =
                if (BuildConfig.DEBUG) {
                    intent.extras?.keySet().orEmpty().filter { it.startsWith("EV_") }
                        .associate { it.removePrefix("EV_") to intent.getStringExtra(it).orEmpty() }
                } else {
                    emptyMap()
                },
        )
        if (savedInstanceState == null) model.handle(intent)
        setContent {
            EVAppTheme {
                RootView(model, Modifier.semantics { testTagsAsResourceId = true })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        (application as EvApp).model.handle(intent)
    }

    @Deprecated("Required by the Google Pay save-passes API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        WalletResultBus.onActivityResult(requestCode, resultCode)
    }
}
