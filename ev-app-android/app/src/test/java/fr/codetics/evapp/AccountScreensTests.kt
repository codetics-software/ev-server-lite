// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp

import fr.codetics.evapp.app.AppTab
import fr.codetics.evapp.app.Route
import fr.codetics.evapp.features.account.AboutScreen
import fr.codetics.evapp.features.account.AccountScreen
import fr.codetics.evapp.features.account.MySitesScreen
import fr.codetics.evapp.features.account.ProfileScreen
import fr.codetics.evapp.support.FixtureServer
import fr.codetics.evapp.support.ScreenTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountScreensTests : ScreenTest() {
    @Test
    fun accountShowsTheSignedInDriver() {
        connect()
        showRoot(AppTab.Account)
        awaitText("DRIVER contrat")
        assertTrue(hasText("driver-afb1d256-3e34-4009-97b2-527553b608ae@example.test"))
        assertTrue(hasTag("account.vehicles"))
        assertFalse(hasTag("account.payments"))
        assertTrue(hasTag("account.delete"))
        clickTag("account.about")
        awaitText("EV Supervision Lite — built by Kevin Vavelin, Codetics")
        assertTrue(hasText("https://codetics.fr"))
    }

    @Test
    fun everyAccountEntryOpensItsScreen() {
        fixtures.respond("GET", "workspace", FixtureServer.fixture("workspace").replace("\"billing\":false", "\"billing\":true"))
        connect()
        showRoot(AppTab.Account)
        val entries =
            listOf(
                "account.profile" to Route.Profile,
                "account.vehicles" to Route.Vehicles,
                "account.payments" to Route.PaymentMethods,
                "account.invoices" to Route.Invoices,
                "account.notifications" to Route.Notifications,
            )
        entries.forEach { (tag, route) ->
            clickTag(tag)
            assertEquals(route, top(AppTab.Account))
            model.back(AppTab.Account)
            compose.waitForIdle()
        }
        click(string(R.string.my_sites))
        assertEquals(Route.Sites, top(AppTab.Account))
        model.back(AppTab.Account)
        click(string(R.string.report_a_problem))
        assertEquals(Route.Report(), top(AppTab.Account))
    }

    @Test
    fun signingOutForgetsTheSession() {
        connect()
        show { AccountScreen() }
        clickTag("account.signout")
        await { !model.isSignedIn }
        assertEquals(1, fixtures.calls("POST", "auth/signout").size)
        assertEquals(AppTab.Map, model.selectedTab)
    }

    @Test
    fun signingOutEverywhereAsksForConfirmation() {
        connect()
        show { AccountScreen() }
        click(string(R.string.sign_out_from_all_devices))
        awaitText(string(R.string.sign_out_from_all_your_devices))
        click(string(R.string.cancel))
        assertTrue(model.isSignedIn)
        click(string(R.string.sign_out_from_all_devices))
        clickTag("dialog.confirm")
        await { !model.isSignedIn }
        assertEquals(1, fixtures.calls("POST", "auth/signout/all").size)
    }

    @Test
    fun deletingTheAccountIsConfirmedAndReportsFailures() {
        connect()
        fixtures.fail("DELETE", "account", 409, "Une session est en cours.")
        show { AccountScreen() }
        clickTag("account.delete")
        awaitText(string(R.string.your_profile_vehicles_photos_and_device_registra))
        clickTag("dialog.confirm")
        awaitText("Une session est en cours.")
        assertTrue(model.isSignedIn)
        fixtures.respond("DELETE", "account", FixtureServer.SUCCESS)
        clickTag("account.delete")
        clickTag("dialog.confirm")
        await { !model.isSignedIn }
    }

    @Test
    fun administratorsCannotDeleteTheirAccountFromTheApp() {
        fixtures.respond("POST", "auth/app/signin", FixtureServer.signIn(eulaAccepted = true).replace("\"DRIVER\"", "\"ADMIN\""))
        connect()
        showRoot(AppTab.Account)
        awaitText("DRIVER contrat")
        assertFalse(hasTag("account.delete"))
    }

    @Test
    fun theProfileIsLoadedEditedAndSaved() {
        connect()
        show { ProfileScreen() }
        awaitText("DRIVER contrat")
        awaitText(string(R.string.french))
        input(string(R.string.full_name), "Thomas Bernard")
        input(string(R.string.phone), "+33 6 12 34 56 78")
        click(string(R.string.french))
        click(string(R.string.spanish))
        clickTag("profile.save")
        awaitText(string(R.string.profile_saved))
        val body = fixtures.calls("PUT", "account").single().body
        assertTrue(body, body.contains("\"name\":\"Thomas Bernard\""))
        assertTrue(body, body.contains("\"phone\":\"+33 6 12 34 56 78\""))
        assertTrue(body, body.contains("\"locale\":\"es\""))
    }

    @Test
    fun aPasswordChangeLinkCanBeRequestedFromTheProfile() {
        connect()
        fixtures.fail("PUT", "account", 400, "Nom invalide")
        show { ProfileScreen() }
        awaitText("DRIVER contrat")
        clickTag("profile.save")
        awaitText("Nom invalide")
        click(string(R.string.change_my_password))
        awaitText(string(R.string.a_link_to_choose_a_new_password_has_been_sent_to))
        val body = fixtures.calls("POST", "auth/password/reset/request").single().body
        assertTrue(body.contains(FixtureServer.WORKSPACE))
    }

    @Test
    fun privateSitesAreListed() {
        connect()
        show { MySitesScreen() }
        awaitText("Gare")
    }

    @Test
    fun driversWithoutPrivateSitesAreTold() {
        fixtures.respond("GET", "account/sites", "[]")
        connect()
        show { MySitesScreen() }
        awaitText(string(R.string.no_private_sites))
    }

    @Test
    fun siteErrorsAreShown() {
        fixtures.fail("GET", "account/sites", 500, "Sites indisponibles")
        connect()
        show { MySitesScreen() }
        awaitText("Sites indisponibles")
    }

    @Test
    fun aboutShowsTheAttributionTheMapCreditsAndSupport() {
        connect(signedIn = false)
        show { AboutScreen() }
        awaitTag("about.attribution")
        assertTrue(hasText("EV Supervision Lite — built by Kevin Vavelin, Codetics"))
        assertTrue(hasText("Copyright © 2026 Kevin Vavelin, Codetics"))
        assertTrue(hasText("© OpenStreetMap contributors, OpenMapTiles, OpenFreeMap, MapLibre"))
        assertTrue(hasText("support@codetics.fr"))
        assertTrue(hasText(string(R.string.version_x, "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")))
        startedIntents()
        clickTag("about.link")
        assertTrue(startedIntents().any { it.data.toString() == "https://codetics.fr" })
        click(string(R.string.privacy_policy))
        assertTrue(startedIntents().any { it.data.toString() == "https://codetics.fr/privacy" })
    }
}
