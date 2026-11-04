// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp.features.map

import androidx.compose.ui.res.stringResource
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import fr.codetics.evapp.R
import fr.codetics.evapp.app.ConnectorType
import fr.codetics.evapp.app.Coordinate
import fr.codetics.evapp.app.Route
import fr.codetics.evapp.app.StationAvailability
import fr.codetics.evapp.app.StationFilter
import fr.codetics.evapp.app.addressLine
import fr.codetics.evapp.app.coordinate
import fr.codetics.evapp.app.distance
import fr.codetics.evapp.app.highestPowerKw
import fr.codetics.evapp.core.Formatters
import fr.codetics.evapp.features.LocalModel
import fr.codetics.evapp.models.CustomerStationResponse
import fr.codetics.evapp.ui.EmptyState
import fr.codetics.evapp.ui.EvList
import fr.codetics.evapp.ui.EvScaffold
import fr.codetics.evapp.ui.StatusDot
import fr.codetics.evapp.ui.Sym
import fr.codetics.evapp.ui.item
import fr.codetics.evapp.ui.section
import fr.codetics.evapp.ui.theme.StatusBlue
import fr.codetics.evapp.ui.theme.StatusGray
import fr.codetics.evapp.ui.theme.StatusGreen
import fr.codetics.evapp.ui.theme.StatusRed
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

@Composable
fun availabilityLabel(availability: StationAvailability): String =
    when (availability) {
        is StationAvailability.Free -> stringResource(R.string.x_available, availability.count.toString())
        StationAvailability.Busy -> stringResource(R.string.in_use)
        StationAvailability.OutOfOrder -> stringResource(R.string.out_of_order)
        StationAvailability.Offline -> stringResource(R.string.offline)
    }

@Composable
fun StationMapScreen() {
    val model = LocalModel.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var search by rememberSaveable { mutableStateOf("") }
    var filter by remember { mutableStateOf(StationFilter()) }
    var showsList by rememberSaveable { mutableStateOf(false) }
    val location = model.userLocation
    val filtered = filter.apply(model.stations.stations, search, location)
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        model.location.start()
    }
    LaunchedEffect(Unit) {
        if (model.location.hasPermission) {
            model.location.start()
        } else {
            permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
        model.stations.reload()
    }
    val open: (CustomerStationResponse) -> Unit = { model.open(Route.StationDetail(it.id)) }
    EvScaffold(
        title = stringResource(R.string.stations),
        actions = {
            StationFilterMenu(filter) { filter = it }
            IconButton(onClick = { showsList = !showsList }, modifier = Modifier.testTag("map.toggle")) {
                Sym(
                    if (showsList) R.drawable.sym_map else R.drawable.sym_list,
                    tint = MaterialTheme.colorScheme.onSurface,
                    description = if (showsList) stringResource(R.string.show_map) else stringResource(R.string.show_list),
                )
            }
        },
    ) { padding ->
        if (showsList) {
            Column(Modifier.padding(padding).fillMaxSize()) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.name_site_or_city)) },
                    leadingIcon = { Sym(R.drawable.sym_search) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(28.dp),
                )
                EvList(PaddingValues(), onRefresh = { model.stations.reload() }) {
                    if (filtered.isEmpty()) {
                        item { EmptyState(R.drawable.sym_ev_station, stringResource(R.string.no_stations_found), stringResource(R.string.try_removing_a_filter_or_searching_for_another_p)) }
                    } else {
                        section {
                            filtered.forEachIndexed { index, station ->
                                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                StationRow(station, location) { open(station) }
                            }
                        }
                    }
                }
            }
        } else {
            Box(Modifier.padding(padding).fillMaxSize()) {
                val style = model.configuration?.mapStyleUrl.orEmpty()
                val select: (String) -> Unit = { id -> model.stations.station(id)?.let(open) }
                if (usesGoogleMaps(context, model.configuration?.googleMapsApiKey)) {
                    GoogleStationMap(filtered, location, model.location.hasPermission, select, Modifier.fillMaxSize())
                } else {
                    StationMap(style, filtered, location, onSelect = select, modifier = Modifier.fillMaxSize())
                }
                if (model.stations.stations.isEmpty() && model.stations.isLoading) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    if (filtered.isNotEmpty()) {
                        val strip = androidx.compose.foundation.lazy.rememberLazyListState()
                        LaunchedEffect(filtered.first().id) { strip.scrollToItem(0) }
                        LazyRow(
                            state = strip,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.testTag("map.nearby"),
                        ) {
                            items(filtered.take(10), key = { it.id }) { station ->
                                StationCard(station, location) { open(station) }
                            }
                        }
                    }
                }
                val hasLocation = location != null
                if (!hasLocation) {
                    FloatingActionButton(
                        onClick = {
                            if (model.location.hasPermission) {
                                model.location.start()
                            } else {
                                permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            }
                            scope.launch { model.stations.reload() }
                        },
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                    ) {
                        Sym(R.drawable.sym_my_location, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
    }
}

@Composable
private fun StationFilterMenu(filter: StationFilter, onChange: (StationFilter) -> Unit) {
    val model = LocalModel.current
    var expanded by remember { mutableStateOf(false) }
    val connectorTypes = model.stations.stations.flatMap { it.connectors.mapNotNull { connector -> connector.type } }.toSortedSet().toList()
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.testTag("map.filter")) {
            Sym(
                R.drawable.sym_filter_list,
                tint = if (filter.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                description = stringResource(R.string.filter),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.available_now)) },
                leadingIcon = { Checkbox(checked = filter.onlyAvailable, onCheckedChange = null) },
                onClick = { onChange(filter.copy(onlyAvailable = !filter.onlyAvailable)) },
            )
            HorizontalDivider()
            FilterOption(stringResource(R.string.all_connectors), filter.connectorType == null) { onChange(filter.copy(connectorType = null)) }
            connectorTypes.forEach { type ->
                FilterOption(connectorLabel(type), filter.connectorType == type) { onChange(filter.copy(connectorType = type)) }
            }
            HorizontalDivider()
            FilterOption(stringResource(R.string.any_power), filter.minimumPowerKw == 0.0) { onChange(filter.copy(minimumPowerKw = 0.0)) }
            FilterOption(stringResource(R.string.s_11_kw_or_more), filter.minimumPowerKw == 11.0) { onChange(filter.copy(minimumPowerKw = 11.0)) }
            FilterOption(stringResource(R.string.s_22_kw_or_more), filter.minimumPowerKw == 22.0) { onChange(filter.copy(minimumPowerKw = 22.0)) }
            FilterOption(stringResource(R.string.s_50_kw_or_more), filter.minimumPowerKw == 50.0) { onChange(filter.copy(minimumPowerKw = 50.0)) }
            FilterOption(stringResource(R.string.s_150_kw_or_more), filter.minimumPowerKw == 150.0) { onChange(filter.copy(minimumPowerKw = 150.0)) }
        }
    }
}

@Composable
private fun FilterOption(label: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { RadioButton(selected = selected, onClick = null) },
        onClick = onClick,
    )
}

@Composable
fun connectorLabel(type: String?): String =
    when (val known = ConnectorType.known(type)) {
        null -> stringResource(R.string.connector)
        "Domestic" -> stringResource(R.string.domestic)
        else -> known
    }

@Composable
private fun StationCard(station: CustomerStationResponse, location: Coordinate?, onClick: () -> Unit) {
    val availability = StationAvailability.of(station)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 3.dp,
        modifier = Modifier.width(186.dp).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(station.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusDot(availability.color, 8.dp)
                Text(availabilityLabel(availability), style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                station.highestPowerKw?.let {
                    Text(Formatters.power(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                station.distance(location)?.let {
                    Text(Formatters.distance(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StationPin(color: Color) {
    Box(Modifier.size(32.dp).background(color, CircleShape).border(2.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
        Sym(R.drawable.sym_bolt_fill, tint = Color.White, size = 18.dp)
    }
}

@Composable
private fun StationRow(station: CustomerStationResponse, location: Coordinate?, onClick: () -> Unit) {
    val availability = StationAvailability.of(station)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StationPin(availability.color)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(station.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            station.addressLine?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(availabilityLabel(availability), style = MaterialTheme.typography.bodySmall, color = availability.color)
                station.highestPowerKw?.let { Text(Formatters.power(it), style = MaterialTheme.typography.bodySmall) }
                station.distance(location)?.let { Text(Formatters.distance(it), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

internal fun framingPoints(stations: List<CustomerStationResponse>, user: Coordinate?): List<Coordinate> =
    StationFilter().apply(stations, "", user).take(6).mapNotNull { it.coordinate } + listOfNotNull(user)

private const val STATION_SOURCE = "stations"
private const val STATION_LAYER = "stations-layer"
private const val USER_SOURCE = "user"

internal fun pinImage(context: Context, color: Int): Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (34 * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    paint.color = color
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2 * density, paint)
    ContextCompat.getDrawable(context, R.drawable.sym_bolt_fill)?.mutate()?.let { bolt ->
        val inset = (8 * density).toInt()
        bolt.setTint(android.graphics.Color.WHITE)
        bolt.setBounds(inset, inset, size - inset, size - inset)
        bolt.draw(canvas)
    }
    return bitmap
}

private fun iconName(station: CustomerStationResponse) =
    when (StationAvailability.of(station)) {
        is StationAvailability.Free -> "pin-free"
        StationAvailability.Busy -> "pin-busy"
        StationAvailability.OutOfOrder -> "pin-fault"
        StationAvailability.Offline -> "pin-offline"
    }

private class MapController(private val context: Context, private val onSelect: (String) -> Unit) {
    var map: MapLibreMap? = null
    var style: Style? = null
    var stations: List<CustomerStationResponse> = emptyList()
    var user: Coordinate? = null
    private var framedStations = false
    private var framedUser = false

    private var view: MapView? = null

    fun attach(view: MapView, styleUrl: String) {
        this.view = view
        view.getMapAsync { map ->
            this.map = map
            map.uiSettings.isRotateGesturesEnabled = false
            map.uiSettings.attributionGravity = android.view.Gravity.TOP or android.view.Gravity.START
            map.uiSettings.logoGravity = android.view.Gravity.TOP or android.view.Gravity.START
            map.uiSettings.setAttributionMargins(120, 16, 0, 0)
            map.uiSettings.setLogoMargins(16, 16, 0, 0)
            map.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
                this.style = style
                val language = java.util.Locale.getDefault().language
                style.layers.filterIsInstance<SymbolLayer>()
                    .filter { it.textField.expression?.toString()?.contains("name") == true }
                    .forEach {
                        it.setProperties(
                            PropertyFactory.textField(
                                Expression.coalesce(Expression.get("name:$language"), Expression.get("name"))
                            )
                        )
                    }
                style.addImage("pin-free", pinImage(context, StatusGreen.toArgb()))
                style.addImage("pin-busy", pinImage(context, StatusBlue.toArgb()))
                style.addImage("pin-fault", pinImage(context, StatusRed.toArgb()))
                style.addImage("pin-offline", pinImage(context, StatusGray.toArgb()))
                style.addSource(GeoJsonSource(USER_SOURCE))
                style.addLayer(
                    CircleLayer("user-halo", USER_SOURCE).withProperties(
                        PropertyFactory.circleRadius(14f),
                        PropertyFactory.circleColor(StatusBlue.toArgb()),
                        PropertyFactory.circleOpacity(0.2f),
                    )
                )
                style.addLayer(
                    CircleLayer("user-dot", USER_SOURCE).withProperties(
                        PropertyFactory.circleRadius(6f),
                        PropertyFactory.circleColor(StatusBlue.toArgb()),
                        PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
                        PropertyFactory.circleStrokeWidth(2f),
                    )
                )
                style.addSource(GeoJsonSource(STATION_SOURCE))
                style.addLayer(
                    SymbolLayer(STATION_LAYER, STATION_SOURCE).withProperties(
                        PropertyFactory.iconImage(Expression.get("icon")),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true),
                    )
                )
                render()
            }
            map.addOnMapClickListener { point ->
                val hit = map.queryRenderedFeatures(map.projection.toScreenLocation(point), STATION_LAYER).firstOrNull()
                hit?.getStringProperty("id")?.let(onSelect)
                hit != null
            }
        }
    }

    fun update(stations: List<CustomerStationResponse>, user: Coordinate?) {
        this.stations = stations
        this.user = user
        render()
    }

    private fun render() {
        val style = style ?: return
        val features =
            stations.mapNotNull { station ->
                station.coordinate?.let { coordinate ->
                    Feature.fromGeometry(Point.fromLngLat(coordinate.longitude, coordinate.latitude)).apply {
                        addStringProperty("id", station.id)
                        addStringProperty("icon", iconName(station))
                    }
                }
            }
        style.getSourceAs<GeoJsonSource>(STATION_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(features))
        val userFeatures =
            listOfNotNull(user?.let { Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude)) })
        style.getSourceAs<GeoJsonSource>(USER_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(userFeatures))
        frame()
    }

    private fun frame() {
        val map = map ?: return
        if (framedUser) return
        val user = user
        if (user == null && framedStations) return
        val points = framingPoints(stations, user)
        if (points.isEmpty()) return
        if (user != null) framedUser = true
        framedStations = true
        if (points.size == 1) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(points[0].latitude, points[0].longitude), 13.0))
            return
        }
        val bounds = LatLngBounds.Builder().apply { points.forEach { include(LatLng(it.latitude, it.longitude)) } }.build()
        val view = view ?: return
        view.post { map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100, 100, 100, 340)) }
    }
}

@SuppressLint("ClickableViewAccessibility")
@Composable
private fun StationMap(
    styleUrl: String,
    stations: List<CustomerStationResponse>,
    user: Coordinate?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val controller = remember { MapController(context, onSelect) }
    val view = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(null)
            controller.attach(this, styleUrl)
        }
    }
    DisposableEffect(lifecycle, view) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> view.onStart()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_STOP -> view.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            view.onPause()
            view.onStop()
            view.onDestroy()
        }
    }
    LaunchedEffect(stations, user) { controller.update(stations, user) }
    AndroidView(factory = { view }, modifier = modifier.testTag("map.view"))
}
