//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated struct EVServerAPI: Sendable {
    let client: APIClient

    func configuration() async throws -> MobileConfigurationResponse {
        try await client.get("api/v1/public/mobile/configuration")
    }

    func signIn(_ request: LoginRequest) async throws -> AppSessionResponse {
        try await client.post("api/v1/auth/app/signin", body: request)
    }

    func signUp(_ request: SignupRequest) async throws {
        let _: [String: String] = try await client.post("api/v1/auth/signon", body: request)
    }

    func signOut() async throws {
        let _: [String: Bool] = try await client.post("api/v1/auth/signout")
    }

    func signOutEverywhere() async throws {
        let _: [String: Bool] = try await client.post("api/v1/auth/signout/all")
    }

    func requestPasswordReset(_ request: ChallengeRequest) async throws {
        let _: [String: Bool] = try await client.post("api/v1/auth/password/reset/request", body: request)
    }

    func resendVerification(_ request: ChallengeRequest) async throws {
        let _: [String: Bool] = try await client.post("api/v1/auth/mail/resend", body: request)
    }

    func me() async throws -> AppPrincipal {
        try await client.get("api/v1/auth/me")
    }

    func eula() async throws -> EulaResponse {
        try await client.get("api/v1/auth/eula")
    }

    func acceptEula() async throws {
        let _: [String: Bool] = try await client.post("api/v1/auth/eula/accept")
    }

    func workspace() async throws -> WorkspaceResponse {
        try await client.get("api/v1/workspace")
    }

    func profile() async throws -> UserResponse {
        try await client.get("api/v1/account")
    }

    func updateProfile(_ input: ProfileInput) async throws -> UserResponse {
        try await client.put("api/v1/account", body: input)
    }

    func deleteAccount() async throws {
        let _: MutationResponse = try await client.delete("api/v1/account")
    }

    func sites() async throws -> [UserSiteResponse] {
        try await client.get("api/v1/account/sites")
    }

    func stations(search: String = "") async throws -> [CustomerStationResponse] {
        try await client.get("api/v1/account/stations", query: ["search": search.isEmpty ? nil : search])
    }

    func station(id: String) async throws -> CustomerStationResponse {
        try await client.get("api/v1/account/stations/\(id)")
    }

    func publicStations(workspace: String, search: String = "") async throws -> [CustomerStationResponse] {
        try await client.get("api/v1/public/workspaces/\(workspace)/stations", query: ["search": search.isEmpty ? nil : search])
    }

    func publicStation(workspace: String, id: String) async throws -> CustomerStationResponse {
        try await client.get("api/v1/public/workspaces/\(workspace)/stations/\(id)")
    }

    func photos(stationId: String) async throws -> [StationPhotoResponse] {
        try await client.get("api/v1/account/stations/\(stationId)/photos")
    }

    func uploadPhoto(stationId: String, jpeg: Data) async throws -> StationPhotoResponse {
        try await client.upload("api/v1/account/stations/\(stationId)/photos", data: jpeg, contentType: "image/jpeg")
    }

    func photoData(_ photo: StationPhotoResponse) async throws -> Data {
        try await client.data(photo.path)
    }

    func deletePhoto(_ photo: StationPhotoResponse) async throws {
        let _: MutationResponse = try await client.delete(photo.path)
    }

    func sessions(status: String? = nil, stationId: String? = nil, offset: Int = 0, limit: Int = 30) async throws -> PageResponse<ChargingSessionResponse> {
        try await client.get("api/v1/transactions", query: ["status": status, "stationId": stationId, "offset": String(offset), "limit": String(limit)])
    }

    func session(id: Int) async throws -> ChargingSessionResponse {
        try await client.get("api/v1/transactions/\(id)")
    }

    func consumptions(sessionId: Int) async throws -> [ConsumptionPoint] {
        try await client.get("api/v1/transactions/\(sessionId)/consumptions")
    }

    func signedData(sessionId: Int) async throws -> SignedMeterData {
        try await client.get("api/v1/transactions/\(sessionId)/signed-data")
    }

    func startSession(_ input: SessionStartInput) async throws -> CommandReceipt {
        try await client.post("api/v1/transactions/start", body: input)
    }

    func stopSession(id: Int) async throws -> CommandReceipt {
        try await client.post("api/v1/transactions/\(id)/stop")
    }

    func statisticsYears() async throws -> [Int] {
        try await client.get("api/v1/statistics/years")
    }

    func statistics(metric: String, year: Int) async throws -> StatisticsResponse {
        try await client.get("api/v1/statistics/\(metric)", query: ["year": String(year)])
    }

    func tokens() async throws -> [ChargingTokenResponse] {
        try await client.get("api/v1/account/tokens")
    }

    func cardOffer() async throws -> CardOfferResponse {
        try await client.get("api/v1/account/card-offer")
    }

    func cardOrders() async throws -> [CardOrderResponse] {
        try await client.get("api/v1/account/card-orders")
    }

    func activateCard(number: String) async throws -> ChargingTokenResponse {
        try await client.post("api/v1/account/card-orders/activate", body: CardActivationInput(number: number))
    }

    func walletPass(uid: String) async throws -> Data {
        try await client.data("api/v1/account/tokens/\(uid)/wallet-pass")
    }

    func cars() async throws -> [RecordResponse<CarData>] {
        try await client.get("api/v1/account/cars")
    }

    func createCar(_ input: RecordInput<CarData>) async throws -> SavedRecord {
        try await client.post("api/v1/account/cars", body: ownedCar(input))
    }

    func updateCar(id: UUID, _ input: RecordInput<CarData>) async throws -> SavedRecord {
        try await client.put("api/v1/account/cars/\(id.uuidString.lowercased())", body: ownedCar(input))
    }

    private func ownedCar(_ input: RecordInput<CarData>) -> RecordInput<CarData> {
        var data = input.data
        data.userId = nil
        return RecordInput(name: input.name, data: data, revision: input.revision)
    }

    func deleteCar(id: UUID) async throws {
        let _: MutationResponse = try await client.delete("api/v1/account/cars/\(id.uuidString.lowercased())")
    }

    func carCatalog(search: String) async throws -> PageResponse<RecordResponse<CarCatalogData>> {
        try await client.get("api/v1/account/car-catalog", query: ["search": search, "limit": "50"])
    }

    func autoChargeCandidates() async throws -> [AutoChargeCandidate] {
        try await client.get("api/v1/account/autocharge/candidates")
    }

    func vehicleIds(carId: UUID) async throws -> [VehicleIdResponse] {
        try await client.get("api/v1/account/cars/\(carId.uuidString.lowercased())/autocharge")
    }

    func enrollAutoCharge(carId: UUID, transactionId: Int) async throws -> VehicleIdResponse {
        try await client.post("api/v1/account/cars/\(carId.uuidString.lowercased())/autocharge", body: AutoChargeInput(transactionId: transactionId))
    }

    func revokeAutoCharge(carId: UUID, uid: String) async throws {
        let _: MutationResponse = try await client.delete("api/v1/account/cars/\(carId.uuidString.lowercased())/autocharge/\(uid)")
    }

    func notifications(offset: Int = 0, limit: Int = 50) async throws -> PageResponse<NotificationResponse> {
        try await client.get("api/v1/notifications", query: ["offset": String(offset), "limit": String(limit)])
    }

    func notificationTypes() async throws -> [NotificationTypeInfo] {
        try await client.get("api/v1/notifications/types")
    }

    func notificationPreferences() async throws -> NotificationPreferences {
        try await client.get("api/v1/account/notifications")
    }

    func updateNotificationPreferences(_ preferences: NotificationPreferences) async throws -> NotificationPreferences {
        try await client.put("api/v1/account/notifications", body: preferences)
    }

    func reportProblem(_ input: ErrorReportInput) async throws {
        let _: MutationResponse = try await client.post("api/v1/notifications/report-error", body: input)
    }

    func registerDevice(_ input: PushDeviceInput) async throws {
        let _: MutationResponse = try await client.post("api/v1/account/devices", body: input)
    }

    func unregisterDevice(token: String) async throws {
        let _: MutationResponse = try await client.delete("api/v1/account/devices/\(token)")
    }

    func invoices(offset: Int = 0, limit: Int = 50) async throws -> PageResponse<InvoiceResponse> {
        try await client.get("api/v1/invoices", query: ["offset": String(offset), "limit": String(limit)])
    }

    func invoiceDownload(id: UUID) async throws -> RedirectResponse {
        try await client.get("api/v1/invoices/\(id.uuidString.lowercased())/download")
    }

    func paymentMethods() async throws -> [PaymentMethodResponse] {
        try await client.get("api/v1/account/payment-methods")
    }

    func paymentMethodCheckout() async throws -> RedirectResponse {
        try await client.post("api/v1/account/payment-methods/checkout")
    }

    func setDefaultPaymentMethod(id: String) async throws {
        let _: MutationResponse = try await client.post("api/v1/account/payment-methods/\(id)/default")
    }

    func deletePaymentMethod(id: String) async throws {
        let _: MutationResponse = try await client.delete("api/v1/account/payment-methods/\(id)")
    }

    func portal(workspace: String, evseId: UUID) async throws -> PortalEvseResponse {
        try await client.get("api/v1/public/evses/\(workspace)/\(evseId.uuidString.lowercased())")
    }

    func checkout(workspace: String, evseId: UUID, request: CheckoutRequest) async throws -> CheckoutResponse {
        try await client.post("api/v1/public/evses/\(workspace)/\(evseId.uuidString.lowercased())/checkout", body: request)
    }

    func guestSession(id: UUID, accessToken: String) async throws -> PortalPaymentResponse {
        try await client.get("api/v1/public/sessions/\(id.uuidString.lowercased())", headers: ["X-Charge-Token": accessToken])
    }

    func stopGuestSession(id: UUID, accessToken: String) async throws -> PortalPaymentResponse {
        try await client.post("api/v1/public/sessions/\(id.uuidString.lowercased())/stop", headers: ["X-Charge-Token": accessToken])
    }
}
