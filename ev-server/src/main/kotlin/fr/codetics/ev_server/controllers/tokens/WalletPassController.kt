// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.ev_server.controllers.tokens

import fr.codetics.ev_server.core.AppPrincipal
import fr.codetics.ev_server.services.tokens.GoogleWalletPassService
import fr.codetics.ev_server.services.tokens.WalletPassService
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
@PreAuthorize("isAuthenticated()")
class WalletPassController(
    private val wallet: WalletPassService,
    private val googleWallet: GoogleWalletPassService,
) {
    @GetMapping("/api/v1/account/tokens/{uid}/google-wallet-pass")
    fun googlePass(@PathVariable uid: String, @AuthenticationPrincipal user: AppPrincipal) =
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(googleWallet.pass(user, uid))

    @GetMapping("/api/v1/account/tokens/{uid}/wallet-pass")
    fun pass(@PathVariable uid: String, @AuthenticationPrincipal user: AppPrincipal): ResponseEntity<ByteArray> =
        ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/vnd.apple.pkpass"))
            .cacheControl(CacheControl.noStore())
            .body(wallet.pass(user, uid))
}
