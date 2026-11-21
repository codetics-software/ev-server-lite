// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp

import android.app.Activity
import fr.codetics.evapp.app.Launch
import fr.codetics.evapp.app.Route
import fr.codetics.evapp.core.JsonCoding
import fr.codetics.evapp.demo.DemoSupport
import fr.codetics.evapp.features.badges.ActivateCardScreen
import fr.codetics.evapp.features.badges.BadgeDetailScreen
import fr.codetics.evapp.features.badges.BadgesScreen
import fr.codetics.evapp.features.badges.NfcBadgeReader
import fr.codetics.evapp.features.badges.WalletResultBus
import fr.codetics.evapp.features.badges.qrBitmap
import fr.codetics.evapp.models.ChargingTokenResponse
import fr.codetics.evapp.support.FixtureServer
import fr.codetics.evapp.support.ScreenTest
import java.time.Instant
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgeScreensTests : ScreenTest() {
    private val tokens: List<ChargingTokenResponse>
        get() = JsonCoding.decode(ListSerializer(ChargingTokenResponse.serializer()), FixtureServer.fixture("tokens"))

    private fun startDemo(signIn: Boolean = true) {
        model.startDemo(signIn = signIn)
        await { model.launch == Launch.Ready && model.isSignedIn == signIn && model.stations.stations.isNotEmpty() }
        if (signIn) await { model.charging.tokens.isNotEmpty() }
    }

    @Test
    fun badgesListTheCardsAndTheOrder() {
        connect()
        show { BadgesScreen() }
        awaitText("CARTE-1")
        assertTrue(hasText("CARTE-2"))
        assertTrue(hasText("${string(R.string.active)} · ${string(R.string.default_label)}"))
        awaitText(string(R.string.waiting_for_payment))
        awaitText(string(R.string.on_your_online_account_for_x, "€15.00"))
        startedIntents()
        clickTag("cards.order")
        assertTrue(startedIntents().any { it.data.toString() == "https://portal.codetics.test/card-orders" })
        clickTag("cards.activate")
        assertEquals(Route.ActivateCard, top())
        click("CARTE-2")
        assertEquals("04AABBCCDDEE80", (top() as Route.BadgeDetail).token.uid)
    }

    @Test
    fun aShippedCardIsReadyToActivate() {
        fixtures.respond("GET", "account/card-orders", FixtureServer.fixture("card_orders").replace("PENDING_PAYMENT", "SHIPPED"))
        fixtures.respond("GET", "account/tokens", "[]")
        connect()
        show { BadgesScreen() }
        awaitText(string(R.string.your_card_is_ready_activate_it_when_you_receive))
        assertTrue(hasText(string(R.string.ready_to_activate)))
        assertTrue(hasText(string(R.string.no_badge_is_registered_on_your_account_yet_ask_y)))
    }

    @Test
    fun phonesWithoutNfcExplainIt() {
        connect()
        show { BadgesScreen() }
        clickTag("badges.scan")
        awaitText(string(R.string.this_device_cannot_read_nfc_cards))
        assertFalse(hasTag("nfc.simulate.own"))
        click(string(R.string.close))
        assertFalse(hasText(string(R.string.this_device_cannot_read_nfc_cards)))
    }

    @Test
    fun theDemoSimulatesEachKindOfCard() {
        startDemo()
        show { BadgesScreen() }
        awaitText("CARTE-1042")
        clickTag("badges.scan")
        clickTag("nfc.simulate.own")
        awaitText(string(R.string.this_card_is_registered_on_your_account))
        assertTrue(hasText(string(R.string.badge_x_held_by_x, "CARTE-1042", "Thomas Bernard")))
        clickTag("badges.scan")
        clickTag("nfc.simulate.unknown")
        awaitText(string(R.string.this_card_is_not_registered_on_your_account))
        click(string(R.string.ask_my_operator_to_add_it))
        assertEquals(Route.Report(subject = string(R.string.please_add_my_card_x, "04FFEEDDCCBB80")), top())
    }

    @Test
    fun aCardCanBeActivatedWithItsNumber() {
        connect()
        show { ActivateCardScreen() }
        inputTag("activation.number", "carte-2")
        clickTag("activation.submit")
        awaitTag("activation.success")
        assertTrue(hasText(string(R.string.card_x_can_now_start_charging_on_this_network, "CARTE-2")))
        assertTrue(fixtures.calls("POST", "account/card-orders/activate").single().body.contains("\"number\":\"carte-2\""))
    }

    @Test
    fun anUnknownCardNumberIsRefused() {
        connect()
        fixtures.fail("POST", "account/card-orders/activate", 404, "Aucune carte commandée ne correspond.")
        show { ActivateCardScreen() }
        assertFalse(hasTag("activation.scan"))
        inputTag("activation.number", "CARTE-9")
        clickTag("activation.submit")
        awaitText("Aucune carte commandée ne correspond.")
    }

    @Test
    fun theDemoActivatesTheOrderedCardBySimulatedScan() {
        startDemo()
        show { ActivateCardScreen() }
        clickTag("activation.scan")
        clickTag("nfc.simulate.ordered")
        awaitTag("activation.success")
        await { model.charging.tokens.any { it.uid == DemoSupport.ORDERED_CARD } }
    }

    @Test
    fun badgeDetailsShowTheCardAndItsQrCode() {
        connect()
        val token = tokens.first().copy(expiresAt = Instant.parse("2027-01-31T00:00:00Z"), lastUsedAt = Instant.parse("2026-11-01T08:00:00Z"), parentIdTag = "GROUPE-A")
        show { BadgeDetailScreen(token) }
        awaitTag("wallet.preview")
        assertTrue(hasText("04A1B2C3D4E580"))
        assertTrue(hasText("GROUPE-A"))
        assertTrue(hasText("Réseau Contrat"))
        assertTrue(hasText(string(R.string.google_wallet_is_not_available_for_this_badge_on)))
        assertTrue(hasText(string(R.string.this_pass_shows_your_badge_as_a_qr_code_contact)))
        assertFalse(hasTag("wallet.add"))
    }

    @Test
    fun walletPassesOpenTheSaveLinkWithoutThePayApi() {
        fixtures.respond("GET", "public/mobile/configuration", FixtureServer.fixture("configuration").replace("}", ",\"google_wallet_enabled\":true,\"google_wallet_nfc_enabled\":true}"))
        connect()
        show { BadgeDetailScreen(tokens.first()) }
        awaitTag("wallet.add")
        assertTrue(hasText(string(R.string.the_pass_is_sent_over_nfc_only_to_readers_compat)))
        startedIntents()
        clickTag("wallet.add")
        await { fixtures.calls("GET", "account/tokens/04A1B2C3D4E580/google-wallet-pass").isNotEmpty() }
        await { startedIntents().any { it.data.toString() == "https://pay.google.com/gp/v/save/a.b.c" } }
        WalletResultBus.onActivityResult(WalletResultBus.REQUEST_CODE, Activity.RESULT_OK)
        awaitText(string(R.string.the_pass_has_been_added_to_google_wallet))
        WalletResultBus.onActivityResult(WalletResultBus.REQUEST_CODE, 7)
        awaitText(string(R.string.google_wallet_could_not_save_this_pass))
    }

    @Test
    fun walletErrorsFromTheServerAreShown() {
        startDemo()
        val token = model.charging.tokens.first { it.uid == DemoSupport.OWN_CARD }
        show { BadgeDetailScreen(token) }
        clickTag("wallet.add")
        await { hasText("passes signés", substring = true) }
        assertFalse(hasText(string(R.string.the_pass_has_been_added_to_google_wallet)))
    }

    @Test
    fun nfcHelpersAreConsistent() {
        assertEquals("04A1FF", NfcBadgeReader.identifier(byteArrayOf(0x04, 0xA1.toByte(), 0xFF.toByte())))
        assertFalse(NfcBadgeReader.isAvailable(null))
        assertFalse(NfcBadgeReader.isEnabled(null))
        val code = qrBitmap("04A1B2C3D4E580", 120)
        assertEquals(120, code.width)
        assertEquals(120, code.height)
        WalletResultBus.onResult = null
        WalletResultBus.onActivityResult(1, Activity.RESULT_OK)
    }
}
