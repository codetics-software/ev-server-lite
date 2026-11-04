// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.ev_server.controllers.workspaces

import fr.codetics.ev_server.core.AppProperties
import fr.codetics.ev_server.services.notifications.PushService
import fr.codetics.ev_server.services.tokens.GoogleWalletPassService
import fr.codetics.ev_server.services.tokens.WalletPassService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class MobileConfigurationResponse(
    val server: String,
    val apiVersion: Int,
    val publicUrl: String,
    val signupEnabled: Boolean,
    val publicTenant: String?,
    val pushEnabled: Boolean,
    val walletEnabled: Boolean,
    val walletNfcEnabled: Boolean,
    val androidPushEnabled: Boolean,
    val googleWalletEnabled: Boolean,
    val googleWalletNfcEnabled: Boolean,
)

@RestController
class MobileConfigurationController(
    private val properties: AppProperties,
    private val push: PushService,
    private val wallet: WalletPassService,
    private val googleWallet: GoogleWalletPassService,
) {
    @GetMapping("/api/v1/public/mobile/configuration")
    fun configuration() =
        MobileConfigurationResponse(
            server = "ev-supervision-lite",
            apiVersion = 1,
            publicUrl = properties.publicUrl.trimEnd('/'),
            signupEnabled = properties.signupEnabled && properties.publicTenant.isNotBlank(),
            publicTenant = properties.publicTenant.ifBlank { null },
            pushEnabled = push.configured,
            walletEnabled = wallet.configured,
            walletNfcEnabled = wallet.nfcEnabled,
            androidPushEnabled = push.androidConfigured,
            googleWalletEnabled = googleWallet.configured,
            googleWalletNfcEnabled = googleWallet.nfcEnabled,
        )
}
