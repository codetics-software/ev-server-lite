// EV Supervision Lite — built by Kevin Vavelin, Codetics
// https://codetics.fr
// Copyright (c) 2026 Kevin Vavelin, Codetics
// SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0

package fr.codetics.evapp.core

import fr.codetics.evapp.models.*
import java.util.UUID

class EvServerApi(val client: ApiClient) {
    suspend fun configuration(): MobileConfigurationResponse = client.get("api/v1/public/mobile/configuration")

    suspend fun signIn(request: LoginRequest): AppSessionResponse = client.post("api/v1/auth/app/signin", request)

    suspend fun signUp(request: SignupRequest) {
        client.post<SignupRequest, Map<String, String>>("api/v1/auth/signon", request)
    }

    suspend fun signOut() {
        client.post<Map<String, Boolean>>("api/v1/auth/signout")
    }

    suspend fun signOutEverywhere() {
        client.post<Map<String, Boolean>>("api/v1/auth/signout/all")
    }

    suspend fun requestPasswordReset(request: ChallengeRequest) {
        client.post<ChallengeRequest, Map<String, Boolean>>("api/v1/auth/password/reset/request", request)
    }

    suspend fun resendVerification(request: ChallengeRequest) {
        client.post<ChallengeRequest, Map<String, Boolean>>("api/v1/auth/mail/resend", request)
    }

    suspend fun me(): AppPrincipal = client.get("api/v1/auth/me")

    suspend fun eula(): EulaResponse = client.get("api/v1/auth/eula")

    suspend fun acceptEula() {
        client.post<Map<String, Boolean>>("api/v1/auth/eula/accept")
    }

    suspend fun workspace(): WorkspaceResponse = client.get("api/v1/workspace")

    suspend fun profile(): UserResponse = client.get("api/v1/account")

    suspend fun updateProfile(input: ProfileInput): UserResponse = client.put("api/v1/account", input)

    suspend fun deleteAccount() {
        client.delete<MutationResponse>("api/v1/account")
    }

    suspend fun sites(): List<UserSiteResponse> = client.get("api/v1/account/sites")

    suspend fun stations(search: String = ""): List<CustomerStationResponse> =
        client.get("api/v1/account/stations", mapOf("search" to search.ifEmpty { null }))

    suspend fun station(id: String): CustomerStationResponse = client.get("api/v1/account/stations/$id")

    suspend fun publicStations(workspace: String, search: String = ""): List<CustomerStationResponse> =
        client.get("api/v1/public/workspaces/$workspace/stations", mapOf("search" to search.ifEmpty { null }))

    suspend fun publicStation(workspace: String, id: String): CustomerStationResponse =
        client.get("api/v1/public/workspaces/$workspace/stations/$id")

    suspend fun photos(stationId: String): List<StationPhotoResponse> =
        client.get("api/v1/account/stations/$stationId/photos")

    suspend fun uploadPhoto(stationId: String, jpeg: ByteArray): StationPhotoResponse =
        client.upload("api/v1/account/stations/$stationId/photos", jpeg, "image/jpeg")

    suspend fun photoData(photo: StationPhotoResponse): ByteArray = client.data(photo.path)

    suspend fun deletePhoto(photo: StationPhotoResponse) {
        client.delete<MutationResponse>(photo.path)
    }

    suspend fun sessions(
        status: String? = null,
        stationId: String? = null,
        offset: Int = 0,
        limit: Int = 30,
    ): PageResponse<ChargingSessionResponse> =
        client.get(
            "api/v1/transactions",
            mapOf("status" to status, "stationId" to stationId, "offset" to offset.toString(), "limit" to limit.toString()),
        )

    suspend fun session(id: Int): ChargingSessionResponse = client.get("api/v1/transactions/$id")

    suspend fun consumptions(sessionId: Int): List<ConsumptionPoint> = client.get("api/v1/transactions/$sessionId/consumptions")

    suspend fun signedData(sessionId: Int): SignedMeterData = client.get("api/v1/transactions/$sessionId/signed-data")

    suspend fun startSession(input: SessionStartInput): CommandReceipt = client.post("api/v1/transactions/start", input)

    suspend fun stopSession(id: Int): CommandReceipt = client.post("api/v1/transactions/$id/stop")

    suspend fun statisticsYears(): List<Int> = client.get("api/v1/statistics/years")

    suspend fun statistics(metric: String, year: Int): StatisticsResponse =
        client.get("api/v1/statistics/$metric", mapOf("year" to year.toString()))

    suspend fun tokens(): List<ChargingTokenResponse> = client.get("api/v1/account/tokens")

    suspend fun cardOffer(): CardOfferResponse = client.get("api/v1/account/card-offer")

    suspend fun cardOrders(): List<CardOrderResponse> = client.get("api/v1/account/card-orders")

    suspend fun activateCard(number: String): ChargingTokenResponse =
        client.post("api/v1/account/card-orders/activate", CardActivationInput(number))

    suspend fun googleWalletPass(uid: String): GoogleWalletPassResponse =
        client.get("api/v1/account/tokens/$uid/google-wallet-pass")

    suspend fun cars(): List<RecordResponse<CarData>> = client.get("api/v1/account/cars")

    suspend fun createCar(input: RecordInput<CarData>): SavedRecord = client.post("api/v1/account/cars", owned(input))

    suspend fun updateCar(id: UUID, input: RecordInput<CarData>): SavedRecord =
        client.put("api/v1/account/cars/$id", owned(input))

    private fun owned(input: RecordInput<CarData>) = input.copy(data = input.data.copy(userId = null))

    suspend fun deleteCar(id: UUID) {
        client.delete<MutationResponse>("api/v1/account/cars/$id")
    }

    suspend fun carCatalog(search: String): PageResponse<RecordResponse<CarCatalogData>> =
        client.get("api/v1/account/car-catalog", mapOf("search" to search, "limit" to "50"))

    suspend fun autoChargeCandidates(): List<AutoChargeCandidate> = client.get("api/v1/account/autocharge/candidates")

    suspend fun vehicleIds(carId: UUID): List<VehicleIdResponse> = client.get("api/v1/account/cars/$carId/autocharge")

    suspend fun enrollAutoCharge(carId: UUID, transactionId: Int): VehicleIdResponse =
        client.post("api/v1/account/cars/$carId/autocharge", AutoChargeInput(transactionId))

    suspend fun revokeAutoCharge(carId: UUID, uid: String) {
        client.delete<MutationResponse>("api/v1/account/cars/$carId/autocharge/$uid")
    }

    suspend fun notifications(offset: Int = 0, limit: Int = 50): PageResponse<NotificationResponse> =
        client.get("api/v1/notifications", mapOf("offset" to offset.toString(), "limit" to limit.toString()))

    suspend fun notificationTypes(): List<NotificationTypeInfo> = client.get("api/v1/notifications/types")

    suspend fun notificationPreferences(): NotificationPreferences = client.get("api/v1/account/notifications")

    suspend fun updateNotificationPreferences(preferences: NotificationPreferences): NotificationPreferences =
        client.put("api/v1/account/notifications", preferences)

    suspend fun reportProblem(input: ErrorReportInput) {
        client.post<ErrorReportInput, MutationResponse>("api/v1/notifications/report-error", input)
    }

    suspend fun registerDevice(input: PushDeviceInput) {
        client.post<PushDeviceInput, MutationResponse>("api/v1/account/devices", input)
    }

    suspend fun unregisterDevice(token: String) {
        client.delete<MutationResponse>("api/v1/account/devices/$token")
    }

    suspend fun invoices(offset: Int = 0, limit: Int = 50): PageResponse<InvoiceResponse> =
        client.get("api/v1/invoices", mapOf("offset" to offset.toString(), "limit" to limit.toString()))

    suspend fun invoiceDownload(id: UUID): RedirectResponse = client.get("api/v1/invoices/$id/download")

    suspend fun paymentMethods(): List<PaymentMethodResponse> = client.get("api/v1/account/payment-methods")

    suspend fun paymentMethodCheckout(): RedirectResponse = client.post("api/v1/account/payment-methods/checkout")

    suspend fun setDefaultPaymentMethod(id: String) {
        client.post<MutationResponse>("api/v1/account/payment-methods/$id/default")
    }

    suspend fun deletePaymentMethod(id: String) {
        client.delete<MutationResponse>("api/v1/account/payment-methods/$id")
    }

    suspend fun portal(workspace: String, evseId: UUID): PortalEvseResponse =
        client.get("api/v1/public/evses/$workspace/$evseId")

    suspend fun checkout(workspace: String, evseId: UUID, request: CheckoutRequest): CheckoutResponse =
        client.post("api/v1/public/evses/$workspace/$evseId/checkout", request)

    suspend fun guestSession(id: UUID, accessToken: String): PortalPaymentResponse =
        client.get("api/v1/public/sessions/$id", headers = mapOf("X-Charge-Token" to accessToken))

    suspend fun stopGuestSession(id: UUID, accessToken: String): PortalPaymentResponse =
        client.post("api/v1/public/sessions/$id/stop", headers = mapOf("X-Charge-Token" to accessToken))
}
