// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp.app

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import fr.codetics.evapp.R
import fr.codetics.evapp.models.ChargingTokenResponse
import fr.codetics.evapp.models.CustomerStationResponse
import fr.codetics.evapp.ui.theme.StatusBlue
import fr.codetics.evapp.ui.theme.StatusGray
import fr.codetics.evapp.ui.theme.StatusGreen
import fr.codetics.evapp.ui.theme.StatusOrange
import fr.codetics.evapp.ui.theme.StatusRed
import java.net.URI
import java.time.Instant
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class Coordinate(val latitude: Double, val longitude: Double)

data class PortalLink(val workspace: String, val evseId: UUID)

data class StartRequest(val stationId: String, val stationName: String, val connectorId: Int)

enum class ConnectorStatus(@StringRes val label: Int, val color: Color) {
    Available(R.string.available, StatusGreen),
    PluggedIn(R.string.plugged_in, StatusBlue),
    Charging(R.string.charging, StatusBlue),
    Paused(R.string.paused, StatusBlue),
    Finishing(R.string.finishing, StatusBlue),
    Reserved(R.string.reserved, StatusOrange),
    Unavailable(R.string.unavailable, StatusGray),
    Faulted(R.string.out_of_order, StatusRed),
    Unknown(R.string.unknown, StatusGray);

    val isStartable: Boolean
        get() = this == Available || this == PluggedIn

    companion object {
        fun of(value: String) =
            when (value) {
                "Available" -> Available
                "Preparing" -> PluggedIn
                "Charging" -> Charging
                "SuspendedEV", "SuspendedEVSE" -> Paused
                "Finishing" -> Finishing
                "Reserved" -> Reserved
                "Unavailable" -> Unavailable
                "Faulted" -> Faulted
                else -> Unknown
            }
    }
}

sealed class StationAvailability(val color: Color) {
    class Free(val count: Int) : StationAvailability(StatusGreen)

    data object Busy : StationAvailability(StatusBlue)

    data object OutOfOrder : StationAvailability(StatusRed)

    data object Offline : StationAvailability(StatusGray)

    val isAvailable: Boolean
        get() = this is Free

    companion object {
        fun of(station: CustomerStationResponse): StationAvailability {
            if (!station.connected) return Offline
            val statuses = station.connectors.map { ConnectorStatus.of(it.status) }
            val free = statuses.count { it.isStartable }
            return when {
                free > 0 -> Free(free)
                statuses.isNotEmpty() && statuses.all { it == ConnectorStatus.Faulted || it == ConnectorStatus.Unavailable } -> OutOfOrder
                else -> Busy
            }
        }
    }
}

object ConnectorType {
    fun known(type: String?): String? =
        when (type) {
            "T2", "Type2", "TYPE2", "IEC_62196_T2" -> "Type 2"
            "CCS", "CCS2", "IEC_62196_T2_COMBO" -> "CCS"
            "CHADEMO", "CHAdeMO" -> "CHAdeMO"
            "T3", "IEC_62196_T3" -> "Type 3"
            "DOMESTIC", "Domestic", "E/F" -> "Domestic"
            else -> type
        }
}

val CustomerStationResponse.coordinate: Coordinate?
    get() = if (latitude != null && longitude != null) Coordinate(latitude, longitude) else null

val CustomerStationResponse.addressLine: String?
    get() {
        val cityLine = listOfNotNull(postalCode, city).joinToString(" ").ifEmpty { null }
        return listOfNotNull(address, cityLine).joinToString(", ").ifEmpty { null }
    }

val CustomerStationResponse.highestPowerKw: Double?
    get() = connectors.mapNotNull { it.maximumPowerKw }.maxOrNull() ?: maximumPowerKw

fun distanceMeters(from: Coordinate, to: Coordinate): Double {
    val radius = 6_371_000.0
    val latitude = Math.toRadians(to.latitude - from.latitude)
    val longitude = Math.toRadians(to.longitude - from.longitude)
    val a =
        sin(latitude / 2).pow(2) +
            cos(Math.toRadians(from.latitude)) * cos(Math.toRadians(to.latitude)) * sin(longitude / 2).pow(2)
    return radius * 2 * atan2(sqrt(a), sqrt(1 - a))
}

fun CustomerStationResponse.distance(from: Coordinate?): Double? {
    val target = coordinate
    return if (from != null && target != null) distanceMeters(from, target) else null
}

data class StationFilter(
    val onlyAvailable: Boolean = false,
    val connectorType: String? = null,
    val minimumPowerKw: Double = 0.0,
) {
    val isActive: Boolean
        get() = onlyAvailable || connectorType != null || minimumPowerKw > 0

    fun apply(stations: List<CustomerStationResponse>, search: String, from: Coordinate?): List<CustomerStationResponse> {
        val term = search.trim()
        return stations
            .filter { station ->
                if (onlyAvailable && !StationAvailability.of(station).isAvailable) return@filter false
                if (connectorType != null && station.connectors.none { it.type == connectorType }) return@filter false
                if (minimumPowerKw > 0 && (station.highestPowerKw ?: 0.0) < minimumPowerKw) return@filter false
                term.isEmpty() ||
                    listOfNotNull(station.name, station.id, station.siteName, station.city, station.address)
                        .any { it.contains(term, ignoreCase = true) }
            }
            .sortedWith(
                compareBy<CustomerStationResponse> { it.distance(from) == null }
                    .thenBy { it.distance(from) ?: 0.0 }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )
    }
}

object ChargingPointId {
    data class Match(val station: CustomerStationResponse, val connectorId: Int?)

    private val valid = Regex("[A-Z]{2}\\*?[A-Z0-9]{3}\\*?E[A-Z0-9*]{1,40}")
    private val embedded = Regex("(?:^|[^A-Z0-9])([A-Z]{2}\\*[A-Z0-9]{3}\\*E[A-Z0-9*]{1,40})", RegexOption.IGNORE_CASE)

    fun normalize(value: String): String = value.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }

    fun isValid(value: String): Boolean = valid.matches(value.uppercase().filterNot(Char::isWhitespace))

    fun find(text: String): String? {
        val decoded = runCatching { java.net.URLDecoder.decode(text.replace("+", "%2B"), Charsets.UTF_8) }.getOrDefault(text)
        return embedded.find(decoded)?.groupValues?.get(1)?.uppercase()
    }

    fun match(value: String, stations: List<CustomerStationResponse>): Match? {
        val wanted = normalize(value)
        if (wanted.isEmpty()) return null
        stations.forEach { station ->
            station.connectors.firstOrNull { it.evseId?.let(::normalize) == wanted }?.let { return Match(station, it.connectorId) }
        }
        return stations.firstOrNull { normalize(it.id) == wanted }?.let { Match(it, it.connectors.singleOrNull()?.connectorId) }
    }
}

object QrCodeParser {
    enum class Failure(@StringRes val message: Int) {
        NotALink(R.string.this_qr_code_is_not_a_charging_point_link),
        ForeignNetwork(R.string.this_qr_code_does_not_belong_to_this_charging_ne),
        NotAChargingPoint(R.string.this_link_does_not_point_to_a_charging_point),
    }

    sealed interface Outcome {
        data class Success(val link: PortalLink) : Outcome

        data class Rejected(val failure: Failure) : Outcome
    }

    sealed interface Scan {
        data class Portal(val link: PortalLink) : Scan

        data class ChargingPoint(val id: String) : Scan

        data class OperatorPortal(val url: String, val chargingPoint: String?) : Scan

        data class Rejected(val failure: Failure) : Scan
    }

    fun read(value: String, portalUrl: String): Scan {
        val trimmed = value.trim()
        if (ChargingPointId.isValid(trimmed)) return Scan.ChargingPoint(trimmed.uppercase().filterNot(Char::isWhitespace))
        return when (val outcome = parse(trimmed, portalUrl)) {
            is Outcome.Success -> Scan.Portal(outcome.link)
            is Outcome.Rejected ->
                if (outcome.failure == Failure.ForeignNetwork) {
                    val scheme = runCatching { URI(trimmed).scheme?.lowercase() }.getOrNull()
                    if (scheme == "https" || scheme == "http") Scan.OperatorPortal(trimmed, ChargingPointId.find(trimmed))
                    else Scan.Rejected(Failure.NotALink)
                } else {
                    Scan.Rejected(outcome.failure)
                }
        }
    }

    private val workspace = Regex("[a-z0-9][a-z0-9-]{0,62}")
    private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

    fun parse(value: String, portalUrl: String): Outcome {
        val url = runCatching { URI(value.trim()) }.getOrNull()
        val scheme = url?.scheme?.lowercase()
        val host = url?.host?.lowercase()
        if (scheme == null || host == null) return Outcome.Rejected(Failure.NotALink)
        val portal = URI(portalUrl)
        if (scheme != portal.scheme?.lowercase() || host != portal.host?.lowercase() || url.port != portal.port) {
            return Outcome.Rejected(Failure.ForeignNetwork)
        }
        val parts = url.path.orEmpty().split('/').filter(String::isNotEmpty)
        val index = parts.indexOf("charge")
        if (index < 0 || parts.size <= index + 2 || !workspace.matches(parts[index + 1]) || !uuid.matches(parts[index + 2])) {
            return Outcome.Rejected(Failure.NotAChargingPoint)
        }
        return Outcome.Success(PortalLink(parts[index + 1], UUID.fromString(parts[index + 2])))
    }
}

object BadgeMatcher {
    sealed interface Outcome {
        data class Registered(val token: ChargingTokenResponse) : Outcome

        data class Disabled(val token: ChargingTokenResponse) : Outcome

        data class Expired(val token: ChargingTokenResponse) : Outcome

        data class NotRegistered(val uid: String) : Outcome
    }

    fun normalize(uid: String): String = uid.uppercase().filter(Char::isLetterOrDigit)

    fun reversedBytes(uid: String): String {
        val normalized = normalize(uid)
        if (normalized.length % 2 != 0) return normalized
        return normalized.chunked(2).reversed().joinToString("")
    }

    fun match(uid: String, tokens: List<ChargingTokenResponse>, now: Instant = Instant.now()): Outcome {
        val direct = normalize(uid)
        val reversed = reversedBytes(uid)
        val token =
            tokens.firstOrNull { normalize(it.uid) == direct }
                ?: tokens.firstOrNull { normalize(it.uid) == reversed }
                ?: return Outcome.NotRegistered(direct)
        if (!token.enabled) return Outcome.Disabled(token)
        if (token.expiresAt?.let { it <= now } == true) return Outcome.Expired(token)
        return Outcome.Registered(token)
    }
}
