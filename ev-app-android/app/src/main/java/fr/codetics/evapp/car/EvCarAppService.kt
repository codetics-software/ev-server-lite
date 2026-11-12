// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp.car

import android.content.Intent
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.CarLocation
import androidx.car.app.model.DistanceSpan
import androidx.car.app.model.ItemList
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Metadata
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceListMapTemplate
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import fr.codetics.evapp.BuildConfig
import fr.codetics.evapp.EvApp
import fr.codetics.evapp.R
import fr.codetics.evapp.app.AppModel
import fr.codetics.evapp.app.ConnectorStatus
import fr.codetics.evapp.app.ConnectorType
import fr.codetics.evapp.app.Launch
import fr.codetics.evapp.app.StationAvailability
import fr.codetics.evapp.app.StationFilter
import fr.codetics.evapp.app.addressLine
import fr.codetics.evapp.app.coordinate
import fr.codetics.evapp.app.distance
import fr.codetics.evapp.app.highestPowerKw
import fr.codetics.evapp.core.Formatters
import fr.codetics.evapp.models.ChargingSessionResponse
import fr.codetics.evapp.models.CustomerStationResponse
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class EvCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator =
        if (BuildConfig.DEBUG) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }

    override fun onCreateSession(sessionInfo: SessionInfo): Session = EvCarSession()
}

class EvCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        val model = (carContext.applicationContext as EvApp).model
        model.bootstrap()
        return StationsCarScreen(carContext, model)
    }
}

private fun CarContext.icon(id: Int) = CarIcon.Builder(IconCompat.createWithResource(this, id)).build()

private fun CarContext.availabilityLabel(availability: StationAvailability): String =
    when (availability) {
        is StationAvailability.Free -> getString(R.string.x_available, availability.count.toString())
        StationAvailability.Busy -> getString(R.string.in_use)
        StationAvailability.OutOfOrder -> getString(R.string.out_of_order)
        StationAvailability.Offline -> getString(R.string.offline)
    }

internal fun CarContext.connectorSummary(station: CustomerStationResponse): List<String> =
    station.connectors
        .groupBy { ConnectorType.known(it.type) ?: getString(R.string.connector) }
        .map { (label, connectors) ->
            val free = if (station.connected) connectors.count { ConnectorStatus.of(it.status).isStartable } else 0
            val power = connectors.mapNotNull { it.maximumPowerKw }.maxOrNull()?.let { " ${Formatters.power(it)}" }.orEmpty()
            "$label$power · $free/${connectors.size}"
        }

abstract class RefreshingCarScreen(carContext: CarContext, protected val model: AppModel) : Screen(carContext) {
    private var job: Job? = null

    init {
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    job = lifecycleScope.launch {
                        while (true) {
                            refresh()
                            invalidate()
                            delay(if (model.charging.current == null) 30.seconds else 5.seconds)
                        }
                    }
                }

                override fun onStop(owner: LifecycleOwner) {
                    job?.cancel()
                }
            }
        )
    }

    protected open suspend fun refresh() {
        model.location.start()
        model.stations.reload()
        model.charging.refresh()
    }
}

class StationsCarScreen(carContext: CarContext, model: AppModel) : RefreshingCarScreen(carContext, model) {
    private var onlyAvailable = false
    private var lastSessionId: Int? = null

    override suspend fun refresh() {
        super.refresh()
        val current = model.charging.current?.id
        if (current != null && current != lastSessionId && screenManager.top === this) {
            screenManager.push(SessionCarScreen(carContext, model))
        }
        lastSessionId = current
    }

    override fun onGetTemplate(): Template {
        val location = model.userLocation
        val builder = ItemList.Builder()
        val stations = StationFilter(onlyAvailable = onlyAvailable).apply(model.stations.stations, "", location).filter { it.coordinate != null }.take(6)
        stations.forEach { builder.addItem(row(it)) }
        val template =
            PlaceListMapTemplate.Builder()
                .setTitle(carContext.getString(R.string.stations))
                .setHeaderAction(Action.APP_ICON)
                .setActionStrip(
                    ActionStrip.Builder()
                        .addAction(
                            Action.Builder()
                                .setTitle(carContext.getString(if (onlyAvailable) R.string.available else R.string.all))
                                .setOnClickListener {
                                    onlyAvailable = !onlyAvailable
                                    invalidate()
                                }
                                .build()
                        )
                        .addAction(
                            Action.Builder()
                                .setTitle(
                                    model.charging.current?.let { "${carContext.getString(R.string.charging)} · ${Formatters.power(it.currentPowerKw ?: 0.0)}" }
                                        ?: carContext.getString(R.string.charging)
                                )
                                .setOnClickListener { screenManager.push(SessionCarScreen(carContext, model)) }
                                .build()
                        )
                        .build()
                )
                .setCurrentLocationEnabled(model.location.hasPermission)
        if (stations.isEmpty() && (model.launch == Launch.Checking || model.stations.isLoading)) {
            template.setLoading(true)
        } else {
            if (stations.isEmpty()) builder.setNoItemsMessage(carContext.getString(R.string.no_stations_found))
            template.setItemList(builder.build())
        }
        return template.build()
    }

    private fun row(station: CustomerStationResponse): Row {
        val coordinate = requireNotNull(station.coordinate)
        val availability = StationAvailability.of(station)
        val details = listOfNotNull(carContext.availabilityLabel(availability), station.highestPowerKw?.let(Formatters::power)).joinToString(" · ")
        val distance = station.distance(model.userLocation)
        val second =
            if (distance != null) {
                val measured = androidx.car.app.model.Distance.create(distance / 1000.0, androidx.car.app.model.Distance.UNIT_KILOMETERS)
                SpannableString("  · ").apply {
                    setSpan(DistanceSpan.create(measured), 0, 1, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
                }
            } else {
                null
            }
        return Row.Builder()
            .setTitle(station.name)
            .addText(if (second != null) android.text.TextUtils.concat(second, details) else details)
            .setBrowsable(true)
            .setMetadata(
                Metadata.Builder()
                    .setPlace(
                        Place.Builder(CarLocation.create(coordinate.latitude, coordinate.longitude))
                            .setMarker(
                                PlaceMarker.Builder()
                                    .setColor(
                                        when (availability) {
                                            is StationAvailability.Free -> CarColor.GREEN
                                            StationAvailability.Busy -> CarColor.BLUE
                                            StationAvailability.OutOfOrder -> CarColor.RED
                                            StationAvailability.Offline -> CarColor.DEFAULT
                                        }
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .setOnClickListener { screenManager.push(StationCarScreen(carContext, model, station.id)) }
            .build()
    }
}

class StationCarScreen(carContext: CarContext, model: AppModel, private val stationId: String) : RefreshingCarScreen(carContext, model) {
    override fun onGetTemplate(): Template {
        val station = model.stations.station(stationId)
        val pane = Pane.Builder()
        if (station == null) {
            pane.setLoading(true)
        } else {
            pane.addRow(Row.Builder().setTitle(carContext.availabilityLabel(StationAvailability.of(station))).apply { station.addressLine?.let(::addText) }.build())
            val connectors = carContext.connectorSummary(station)
            if (connectors.isNotEmpty()) {
                pane.addRow(
                    Row.Builder()
                        .setTitle(carContext.getString(R.string.connectors))
                        .apply { connectors.take(2).forEach(::addText) }
                        .build()
                )
            } else {
                station.highestPowerKw?.let {
                    pane.addRow(Row.Builder().setTitle(carContext.getString(R.string.power)).addText(Formatters.power(it)).build())
                }
            }
            station.coordinate?.let { coordinate ->
                pane.addAction(
                    Action.Builder()
                        .setTitle(carContext.getString(R.string.directions))
                        .setBackgroundColor(CarColor.PRIMARY)
                        .setOnClickListener {
                            runCatching {
                                carContext.startCarApp(
                                    Intent(CarContext.ACTION_NAVIGATE, Uri.parse("geo:${coordinate.latitude},${coordinate.longitude}?q=${coordinate.latitude},${coordinate.longitude}"))
                                )
                            }.onFailure {
                                CarToast.makeText(carContext, carContext.getString(R.string.no_app_can_open_this_location), CarToast.LENGTH_LONG).show()
                            }
                        }
                        .build()
                )
            }
        }
        return PaneTemplate.Builder(pane.build())
            .setHeaderAction(Action.BACK)
            .setTitle(station?.name ?: carContext.getString(R.string.station))
            .build()
    }
}

class SessionCarScreen(carContext: CarContext, model: AppModel) : RefreshingCarScreen(carContext, model) {
    private var confirmsStop = false

    override suspend fun refresh() {
        model.stations.reload()
        model.charging.refresh()
    }

    override fun onGetTemplate(): Template {
        val title = carContext.getString(R.string.charging)
        if (!model.isSignedIn) {
            return message(title, carContext.getString(R.string.not_signed_in), carContext.getString(R.string.sign_in_on_your_phone_to_follow_your_charging_se))
        }
        val session = model.charging.current
            ?: return message(title, carContext.getString(R.string.no_charging_in_progress), carContext.getString(R.string.your_session_appears_here_once_it_starts))
        if (confirmsStop) return confirmation(session)
        return PaneTemplate.Builder(pane(session))
            .setHeaderAction(Action.BACK)
            .setTitle(title)
            .build()
    }

    private fun message(title: String, heading: String, detail: String): Template =
        MessageTemplate.Builder("$heading\n$detail")
            .setTitle(title)
            .setHeaderAction(Action.BACK)
            .setIcon(carContext.icon(R.drawable.sym_ev_station))
            .build()

    private fun confirmation(session: ChargingSessionResponse): Template =
        MessageTemplate.Builder(carContext.getString(R.string.stop_this_charging_session))
            .setTitle(carContext.getString(R.string.stop_charging))
            .setHeaderAction(Action.BACK)
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.stop_charging))
                    .setBackgroundColor(CarColor.RED)
                    .setOnClickListener {
                        confirmsStop = false
                        lifecycleScope.launch {
                            runCatching { model.api?.stopSession(session.id) }
                            model.charging.refresh()
                            invalidate()
                        }
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.cancel))
                    .setOnClickListener {
                        confirmsStop = false
                        invalidate()
                    }
                    .build()
            )
            .build()

    private fun pane(session: ChargingSessionResponse): Pane {
        val rows =
            buildList {
                add(R.string.station to (model.stations.station(session.stationId)?.name ?: session.stationId))
                add(R.string.power to Formatters.power(session.currentPowerKw ?: 0.0))
                add(R.string.energy to Formatters.energy(session.energyKwh))
                add(R.string.duration to Formatters.duration(session.durationSeconds))
                session.stateOfCharge?.let { add(R.string.battery to "$it%") }
                if (session.price != null) add(R.string.estimated_price to Formatters.price(session.price, session.currency))
            }
        val pane = Pane.Builder()
        rows.take(4).forEach { (label, value) -> pane.addRow(Row.Builder().setTitle(carContext.getString(label)).addText(value).build()) }
        pane.addAction(
            Action.Builder()
                .setTitle(carContext.getString(R.string.stop_charging))
                .setBackgroundColor(CarColor.RED)
                .setOnClickListener {
                    confirmsStop = true
                    invalidate()
                }
                .build()
        )
        return pane.build()
    }
}
