// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp.support

import android.os.Looper
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import fr.codetics.evapp.EvApp
import fr.codetics.evapp.app.AppModel
import fr.codetics.evapp.app.AppTab
import fr.codetics.evapp.app.Launch
import fr.codetics.evapp.app.Route
import fr.codetics.evapp.features.LocalModel
import fr.codetics.evapp.features.RootView
import fr.codetics.evapp.ui.theme.EVAppTheme
import java.time.Duration
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

fun configuration(server: FixtureServer): Map<String, String> =
    mapOf(
        "API_URL" to server.url,
        "PORTAL_URL" to "https://portal.codetics.test",
        "WORKSPACE_SLUG" to FixtureServer.WORKSPACE,
        "BRAND_NAME" to "Réseau Test",
        "SUPPORT_EMAIL" to "support@codetics.fr",
        "PRIVACY_POLICY_URL" to "https://codetics.fr/privacy",
        "MAP_STYLE_URL" to "https://tiles.openfreemap.org/styles/liberty",
        "GOOGLE_MAPS_API_KEY" to "",
        "FIREBASE_PROJECT_ID" to "",
        "FIREBASE_APPLICATION_ID" to "",
        "FIREBASE_API_KEY" to "",
        "FIREBASE_SENDER_ID" to "",
    )

fun settle(timeoutMillis: Long = 10_000, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (!condition()) {
        if (System.currentTimeMillis() > deadline) throw AssertionError("Condition not reached in $timeoutMillis ms")
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(5)
    }
}

fun advanceMainLooper(duration: Duration) {
    shadowOf(Looper.getMainLooper()).idleFor(duration)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = EvApp::class)
abstract class ScreenTest {
    @get:Rule val compose = createComposeRule()

    protected val fixtures = FixtureServer()
    protected val app: EvApp
        get() = ApplicationProvider.getApplicationContext()
    protected val model: AppModel
        get() = app.model

    @Before
    fun startFixtures() {
        Locale.setDefault(Locale.ENGLISH)
        FakeAndroidKeyStore.install()
        fixtures.start()
    }

    @After
    fun stopFixtures() {
        fixtures.shutdown()
    }

    protected fun string(@StringRes id: Int, vararg args: Any): String = app.getString(id, *args)

    protected fun connect(signedIn: Boolean = true) {
        model.bootstrap(overrides = configuration(fixtures))
        await { model.launch == Launch.Ready }
        if (signedIn) {
            val job = model.scope.launch { model.signIn(FixtureServer.WORKSPACE, "driver@codetics.test", "a-long-enough-password") }
            await { job.isCompleted }
        }
        await { model.stations.stations.isNotEmpty() }
    }

    private var hasContent = false

    protected fun idle() {
        Snapshot.sendApplyNotifications()
        if (hasContent) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        } else {
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    protected fun show(content: @Composable () -> Unit) {
        hasContent = true
        compose.setContent { EVAppTheme { CompositionLocalProvider(LocalModel provides model) { content() } } }
        compose.waitForIdle()
    }

    protected fun showRoot(tab: AppTab = AppTab.Account, vararg routes: Route) {
        model.selectedTab = tab
        routes.forEach { model.open(it, tab) }
        hasContent = true
        compose.setContent { EVAppTheme { RootView(model) } }
        compose.waitForIdle()
    }

    protected fun await(timeoutMillis: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (true) {
            idle()
            if (condition()) return
            if (System.currentTimeMillis() > deadline) throw AssertionError("Condition not reached in $timeoutMillis ms; visible texts: ${visibleTexts()}")
            Thread.sleep(5)
        }
    }

    protected fun visibleTexts(): List<String> =
        if (!hasContent) {
            emptyList()
        } else {
            compose.onAllNodes(SemanticsMatcher("any") { true }, useUnmergedTree = true).fetchSemanticsNodes().flatMap { node ->
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                    node.config.getOrNull(SemanticsProperties.EditableText)?.text.let { listOfNotNull(it) }
            }
        }

    protected fun clickToggle(index: Int = 0) {
        val node = compose.onAllNodes(androidx.compose.ui.test.isToggleable())[index]
        runCatching { node.performScrollTo() }
        node.performClick()
        compose.waitForIdle()
    }

    protected fun awaitText(text: String, substring: Boolean = false): SemanticsNodeInteraction {
        await { compose.onAllNodesWithText(text, substring).fetchSemanticsNodes().isNotEmpty() }
        return compose.onAllNodesWithText(text, substring)[0]
    }

    protected fun awaitTag(tag: String): SemanticsNodeInteraction {
        await { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        return compose.onAllNodesWithTag(tag)[0]
    }

    protected fun hasText(text: String, substring: Boolean = false): Boolean =
        compose.onAllNodesWithText(text, substring).fetchSemanticsNodes().isNotEmpty()

    protected fun hasTag(tag: String): Boolean = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    protected fun text(text: String): SemanticsNodeInteraction = compose.onNodeWithText(text).performScrollTo()

    protected fun tag(tag: String): SemanticsNodeInteraction = compose.onNodeWithTag(tag).performScrollTo()

    protected fun click(text: String, substring: Boolean = false) {
        val node = awaitText(text, substring)
        runCatching { node.performScrollTo() }
        node.performClick()
        compose.waitForIdle()
    }

    protected fun clickTag(tag: String) {
        val node = awaitTag(tag)
        runCatching { node.performScrollTo() }
        node.performClick()
        compose.waitForIdle()
    }

    protected fun input(label: String, value: String) {
        await { compose.onAllNodes(hasSetTextAction() and androidx.compose.ui.test.hasText(label)).fetchSemanticsNodes().isNotEmpty() }
        val node = compose.onAllNodes(hasSetTextAction() and androidx.compose.ui.test.hasText(label))[0]
        runCatching { node.performScrollTo() }
        node.performTextReplacement(value)
        compose.waitForIdle()
    }

    protected fun inputTag(tag: String, value: String) {
        val node = awaitTag(tag)
        runCatching { node.performScrollTo() }
        node.performTextReplacement(value)
        compose.waitForIdle()
    }

    protected fun awaitCall(method: String, path: String): FixtureServer.Call {
        await { fixtures.calls(method, path).isNotEmpty() }
        return fixtures.calls(method, path).last()
    }

    protected fun startedIntents(): List<android.content.Intent> =
        generateSequence { shadowOf(app).nextStartedActivity }.toList()

    protected fun top(tab: AppTab = model.selectedTab): Route = model.stacks.getValue(tab).last()

    protected fun runOnModel(block: suspend () -> Unit): Job {
        val job = model.scope.launch { block() }
        await { job.isCompleted }
        return job
    }

    protected fun awaitWithLooper(timeoutMillis: Long = 20_000, step: Duration = Duration.ofSeconds(2), condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("Condition not reached in $timeoutMillis ms")
            idle()
            Thread.sleep(30)
            advanceMainLooper(step)
            if (hasContent) compose.mainClock.advanceTimeBy(step.toMillis())
        }
        idle()
    }
}
