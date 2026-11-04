// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp

import android.os.Looper
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.PlaceListMapTemplate
import androidx.car.app.model.Row
import androidx.car.app.testing.TestCarContext
import androidx.test.core.app.ApplicationProvider
import fr.codetics.evapp.car.SessionCarScreen
import fr.codetics.evapp.car.StationCarScreen
import fr.codetics.evapp.car.StationsCarScreen
import fr.codetics.evapp.models.SessionStartInput
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = EvApp::class)
class CarScreensTests {
    private val app get() = ApplicationProvider.getApplicationContext<EvApp>()
    private val context get() = TestCarContext.createCarContext(app)

    @Test
    fun stationsScreenUsesTheMapTemplate() {
        val template = StationsCarScreen(context, app.model).onGetTemplate()
        assertTrue(template is PlaceListMapTemplate)
    }

    @Test
    fun sessionScreenAsksToSignInOnThePhone() {
        val template = SessionCarScreen(context, app.model).onGetTemplate() as MessageTemplate
        assertEquals("Sign in on your phone to follow your charging sessions.", template.message.toString().substringAfter('\n'))
    }

    private fun settle(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (!condition() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        assertTrue(condition())
    }

    @Test
    fun demoDataFillsTheTemplatesAndTheStopAction() {
        val model = app.model
        model.startDemo(signIn = true)
        settle { model.isSignedIn && model.stations.stations.isNotEmpty() }
        val places = StationsCarScreen(context, model).onGetTemplate() as PlaceListMapTemplate
        val rows = places.itemList!!.items
        assertEquals(6, rows.size)
        assertTrue(rows.all { (it as Row).metadata?.place != null })
        val station = StationCarScreen(context, model, "PAR-01").onGetTemplate() as PaneTemplate
        assertEquals("Gare de Lyon — Borne A", station.title.toString())
        assertEquals(1, station.pane.actions.size)
        val job = model.scope.launch { model.api?.startSession(SessionStartInput("PAR-01", 1, "04A1B2C3D4E580")); model.charging.refresh() }
        settle { job.isCompleted && model.charging.current != null }
        val pane = (SessionCarScreen(context, model).onGetTemplate() as PaneTemplate).pane
        assertTrue(pane.rows.size in 1..4)
        assertEquals(1, pane.actions.size)
    }

    @Test
    fun stationScreenWaitsForItsData() {
        val template = StationCarScreen(context, app.model, "UNKNOWN").onGetTemplate() as PaneTemplate
        assertTrue(template.pane.isLoading)
    }
}
