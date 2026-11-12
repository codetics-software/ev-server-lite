// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp.app

import fr.codetics.evapp.models.CarData
import fr.codetics.evapp.models.ChargingSessionResponse
import fr.codetics.evapp.models.ChargingTokenResponse
import fr.codetics.evapp.models.PortalEvseResponse
import fr.codetics.evapp.models.RecordResponse

sealed interface Route {
    data class Root(val tab: AppTab) : Route

    data class StationDetail(val stationId: String) : Route

    data class StartCharge(val request: StartRequest) : Route

    data class Report(val stationId: String? = null, val connectorId: Int? = null, val subject: String = "") : Route

    data class LiveSession(val id: Int) : Route

    data class Portal(val link: PortalLink) : Route

    data class Checkout(val portal: PortalEvseResponse) : Route

    data class GuestSession(val order: GuestOrder) : Route

    data object Scanner : Route

    data class ChargingPointLookup(val prefill: String? = null, val operatorUrl: String? = null) : Route

    data class BadgeDetail(val token: ChargingTokenResponse) : Route

    data object ActivateCard : Route

    data class SessionDetail(val session: ChargingSessionResponse) : Route

    data object Vehicles : Route

    data class VehicleDetail(val car: RecordResponse<CarData>) : Route

    data class VehicleEditor(val car: RecordResponse<CarData>?) : Route

    data object PaymentMethods : Route

    data object Invoices : Route

    data object Notifications : Route

    data object NotificationPreferences : Route

    data object Sites : Route

    data object Profile : Route

    data object About : Route

    data object SignUp : Route

    data class PasswordReset(val workspace: String, val email: String) : Route
}
