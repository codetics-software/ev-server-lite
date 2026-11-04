// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp.features.map

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import fr.codetics.evapp.app.Coordinate
import fr.codetics.evapp.app.StationAvailability
import fr.codetics.evapp.app.coordinate
import fr.codetics.evapp.models.CustomerStationResponse

fun usesGoogleMaps(context: Context, apiKey: String?): Boolean =
    !apiKey.isNullOrBlank() &&
        runCatching { GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS }
            .getOrDefault(false)

@Composable
fun GoogleStationMap(
    stations: List<CustomerStationResponse>,
    user: Coordinate?,
    hasLocationPermission: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val camera = rememberCameraPositionState()
    var loaded by remember { mutableStateOf(false) }
    var framedStations by remember { mutableStateOf(false) }
    var framedUser by remember { mutableStateOf(false) }
    LaunchedEffect(loaded, stations, user) {
        if (!loaded || framedUser || (user == null && framedStations)) return@LaunchedEffect
        val points = framingPoints(stations, user)
        if (points.isEmpty()) return@LaunchedEffect
        if (user != null) framedUser = true
        framedStations = true
        val update =
            if (points.size == 1) {
                CameraUpdateFactory.newLatLngZoom(LatLng(points[0].latitude, points[0].longitude), 13f)
            } else {
                val bounds = LatLngBounds.builder().apply { points.forEach { include(LatLng(it.latitude, it.longitude)) } }.build()
                CameraUpdateFactory.newLatLngBounds(bounds, 160)
            }
        camera.move(update)
    }
    GoogleMap(
        modifier = modifier.testTag("map.view.google"),
        cameraPositionState = camera,
        properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
        uiSettings = MapUiSettings(rotationGesturesEnabled = false, mapToolbarEnabled = false),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 110.dp),
        onMapLoaded = { loaded = true },
    ) {
        stations.forEach { station ->
            val coordinate = station.coordinate ?: return@forEach
            val availability = StationAvailability.of(station)
            val color = availability.color.toArgb()
            val icon = remember(color) { BitmapDescriptorFactory.fromBitmap(pinImage(context, color)) }
            androidx.compose.runtime.key(station.id) {
                Marker(
                    state = remember { MarkerState(LatLng(coordinate.latitude, coordinate.longitude)) },
                    title = station.name,
                    icon = icon,
                    onClick = {
                        onSelect(station.id)
                        true
                    },
                )
            }
        }
    }
}
