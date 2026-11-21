// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithText
import fr.codetics.evapp.app.AppTab
import fr.codetics.evapp.app.Launch
import fr.codetics.evapp.app.Route
import fr.codetics.evapp.features.auth.EulaScreen
import fr.codetics.evapp.features.auth.PasswordResetScreen
import fr.codetics.evapp.features.auth.SignInEntryScreen
import fr.codetics.evapp.features.auth.SignUpScreen
import fr.codetics.evapp.support.FixtureServer
import fr.codetics.evapp.support.ScreenTest
import fr.codetics.evapp.support.configuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthScreensTests : ScreenTest() {
    @Test
    fun signingInFromTheAccountTabOpensTheAccount() {
        connect(signedIn = false)
        showRoot(AppTab.Account)
        awaitText(string(R.string.sign_in_to_see_your_badges_sessions_and_vehicles))
        assertTrue(hasText("Réseau Test"))
        assertFalse(hasTag("tab.sessions"))
        compose.onNodeWithText(string(R.string.sign_in)).assertIsNotEnabled()
        inputTag("signin.email", "driver@codetics.test")
        inputTag("signin.password", "a-long-enough-password")
        clickTag("signin.submit")
        await { model.isSignedIn }
        awaitText("DRIVER contrat")
        awaitTag("tab.sessions")
        assertTrue(fixtures.calls("POST", "auth/app/signin").single().body.contains("\"password\":\"a-long-enough-password\""))
    }

    @Test
    fun anUnverifiedAddressCanReceiveTheEmailAgain() {
        connect(signedIn = false)
        fixtures.fail("POST", "auth/app/signin", 401, "Vérifiez votre adresse e-mail.", problem = "email_not_verified")
        show { SignInEntryScreen() }
        inputTag("signin.email", "driver@codetics.test")
        inputTag("signin.password", "a-long-enough-password")
        clickTag("signin.submit")
        awaitText("Vérifiez votre adresse e-mail.")
        click(string(R.string.send_the_verification_email_again))
        awaitText(string(R.string.if_this_address_needs_verifying_an_email_has_bee))
        val resend = awaitCall("POST", "auth/mail/resend")
        assertTrue(resend.body.contains("\"tenant\":\"${FixtureServer.WORKSPACE}\""))
        assertFalse(model.isSignedIn)
    }

    @Test
    fun wrongCredentialsShowTheServerMessage() {
        connect(signedIn = false)
        fixtures.fail("POST", "auth/app/signin", 401, "Identifiants incorrects.")
        show { SignInEntryScreen() }
        inputTag("signin.email", "driver@codetics.test")
        inputTag("signin.password", "wrong")
        clickTag("signin.submit")
        awaitText("Identifiants incorrects.")
        assertFalse(hasText(string(R.string.send_the_verification_email_again)))
        click(string(R.string.forgot_password))
        assertEquals(Route.PasswordReset(FixtureServer.WORKSPACE, "driver@codetics.test"), top())
        click(string(R.string.about_this_app))
        assertEquals(Route.About, top())
    }

    @Test
    fun signUpIsOfferedWhenTheServerAllowsIt() {
        fixtures.respond("GET", "public/mobile/configuration", FixtureServer.fixture("configuration").replace("\"signup_enabled\":false", "\"signup_enabled\":true"))
        connect(signedIn = false)
        show { SignInEntryScreen() }
        clickTag("signin.signup")
        assertEquals(Route.SignUp, top())
    }

    @Test
    fun signUpNeedsALongPasswordAndTheTerms() {
        connect(signedIn = false)
        show { SignUpScreen() }
        input(string(R.string.full_name), "Ann Driver")
        input(string(R.string.email), "ann@codetics.test")
        input(string(R.string.password), "short")
        compose.onNodeWithText(string(R.string.create)).assertIsNotEnabled()
        input(string(R.string.password), "a sentence that is long enough")
        compose.onNodeWithText(string(R.string.create)).assertIsNotEnabled()
        click(string(R.string.i_accept_the_terms_of_use_and_the_privacy_policy))
        compose.onNodeWithText(string(R.string.create)).assertIsNotEnabled()
        compose.onNode(androidx.compose.ui.test.isToggleable()).performClickSafely()
        click(string(R.string.create))
        awaitText(string(R.string.if_sign_up_is_available_for_this_address_a_verif))
        val body = fixtures.calls("POST", "auth/signon").single().body
        assertTrue(body.contains("\"email\":\"ann@codetics.test\""))
        assertTrue(body.contains("\"name\":\"Ann Driver\""))
    }

    @Test
    fun signUpFailuresAreShown() {
        connect(signedIn = false)
        fixtures.fail("POST", "auth/signon", 400, "Mot de passe trop courant.")
        show { SignUpScreen() }
        input(string(R.string.full_name), "Ann Driver")
        input(string(R.string.email), "ann@codetics.test")
        input(string(R.string.password), "a sentence that is long enough")
        compose.onNode(androidx.compose.ui.test.isToggleable()).performClickSafely()
        click(string(R.string.create))
        awaitText("Mot de passe trop courant.")
    }

    @Test
    fun passwordResetConfirmsWithoutRevealingTheAccount() {
        connect(signedIn = false)
        show { PasswordResetScreen(FixtureServer.WORKSPACE, "driver@codetics.test") }
        click(string(R.string.send))
        awaitText(string(R.string.if_this_account_exists_a_link_to_choose_a_new_pa))
        assertTrue(fixtures.calls("POST", "auth/password/reset/request").single().body.contains("\"email\":\"driver@codetics.test\""))
    }

    @Test
    fun passwordResetErrorsAreShown() {
        connect(signedIn = false)
        fixtures.fail("POST", "auth/password/reset/request", 429, "Trop de demandes.")
        show { PasswordResetScreen(FixtureServer.WORKSPACE, "") }
        input(string(R.string.email), "driver@codetics.test")
        click(string(R.string.send))
        awaitText("Trop de demandes.")
    }

    @Test
    fun newDriversAcceptTheTermsBeforeUsingTheApp() {
        fixtures.respond("POST", "auth/app/signin", FixtureServer.signIn(eulaAccepted = false))
        connect()
        showRoot(AppTab.Account)
        awaitText(string(R.string.before_you_continue_please_accept_the_terms_of_u))
        awaitText("Supervision de recharge Lite — Kevin Vavelin, Codetics")
        assertTrue(hasText("1.0"))
        click(string(R.string.accept_and_continue))
        await { model.principal?.eulaAccepted == true }
        awaitTag("tab.sessions")
    }

    @Test
    fun theTermsScreenCanSignOut() {
        fixtures.respond("POST", "auth/app/signin", FixtureServer.signIn(eulaAccepted = false))
        fixtures.fail("POST", "auth/eula/accept", 500, "Conditions indisponibles")
        connect()
        show { EulaScreen() }
        click(string(R.string.accept_and_continue))
        awaitText("Conditions indisponibles")
        click(string(R.string.sign_out))
        await { !model.isSignedIn }
        assertEquals(1, fixtures.calls("POST", "auth/signout").size)
    }

    @Test
    fun theTemplateConfigurationListsEveryIssueAndOffersTheDemo() {
        model.bootstrap()
        showRoot(AppTab.Account)
        awaitText(string(R.string.app_not_configured))
        assertTrue(hasText(string(R.string.x_still_contains_a_placeholder, "API_URL")))
        clickTag("demo.guest")
        await { model.launch == Launch.Ready }
        assertTrue(model.isDemo)
        assertFalse(model.isSignedIn)
    }

    @Test
    fun invalidAndInsecureValuesAreExplained() {
        val values = configuration(fixtures) + mapOf("WORKSPACE_SLUG" to "Not A Slug", "PORTAL_URL" to "ftp://portal.codetics.test")
        model.bootstrap(overrides = values)
        showRoot(AppTab.Map)
        awaitText(string(R.string.x_has_an_invalid_value, "WORKSPACE_SLUG"))
        assertTrue(hasText(string(R.string.x_must_use_https, "PORTAL_URL")))
    }

    @Test
    fun anUnreachableServerOffersARetry() {
        fixtures.fail("GET", "public/mobile/configuration", 503, "Maintenance en cours")
        model.bootstrap(overrides = configuration(fixtures))
        showRoot(AppTab.Account)
        awaitText(string(R.string.server_unavailable))
        assertTrue(hasText("Maintenance en cours"))
        click(string(R.string.try_again))
        await { model.launch is Launch.Misconfigured }
        awaitText(string(R.string.app_not_configured))
    }

    @Test
    fun anIncompatibleServerIsExplained() {
        fixtures.respond("GET", "public/mobile/configuration", FixtureServer.fixture("configuration").replace("ev-supervision-lite", "other"))
        model.bootstrap(overrides = configuration(fixtures))
        showRoot(AppTab.Account)
        awaitText(string(R.string.this_server_is_not_compatible_with_this_version))
    }

    @Test
    fun theLaunchScreenWaitsForTheServer() {
        showRoot(AppTab.Account)
        awaitText(string(R.string.connecting_to_the_server))
    }

    private fun SemanticsNodeInteraction.performClickSafely() {
        runCatching { performScrollTo() }
        performClick()
        compose.waitForIdle()
    }
}
