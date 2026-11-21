//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation
import Testing
@testable import EV_App

struct EVServerAPITests {
    private static let carId = UUID(uuidString: "17E97D10-B40A-4F65-B976-840049B5FC56")!
    private static let evseId = UUID(uuidString: "AC5107D1-F3AD-4644-AC98-D57810F8034F")!
    private static let orderId = UUID(uuidString: "7084987B-6653-4D4A-99B7-C1E925397EC2")!
    private static let photoBytes = Data([0xFF, 0xD8, 0x42])

    private static func first(_ fixture: String) -> StubReply {
        let data = (try? TestData.fixture(fixture)) ?? Data()
        let array = (try? JSONSerialization.jsonObject(with: data) as? [Any]) ?? []
        return StubReply(status: 200, body: (try? JSONSerialization.data(withJSONObject: array.first ?? [:])) ?? Data())
    }

    private static func route(_ request: RecordedRequest) -> StubReply? {
        let path = request.path.replacingOccurrences(of: "/api/v1/", with: "")
        let parts = path.split(separator: "/").map(String.init)
        switch (request.method, path) {
        case ("GET", "public/mobile/configuration"): return .fixture("configuration")
        case ("POST", "auth/app/signin"): return .fixture("signin")
        case ("POST", "auth/signon"): return .json(#"{"status":"PENDING"}"#)
        case ("GET", "auth/me"): return .json(#"{"id":"0720d0ed-c8e8-419c-8e01-0c1d0ea9dc6d","tenantId":"bb9bcbe4-1d14-430a-8130-ac67d4127d16","email":"a@b.fr","name":"Driver","role":"DRIVER","platformAdmin":false,"eulaAccepted":true}"#)
        case ("GET", "auth/eula"): return .fixture("eula")
        case ("GET", "workspace"): return .fixture("workspace")
        case ("GET", "account"), ("PUT", "account"): return .fixture("account")
        case ("GET", "account/sites"): return .fixture("sites")
        case ("GET", "account/stations"): return .fixture("stations")
        case ("GET", "account/tokens"): return .fixture("tokens")
        case ("GET", "account/card-offer"): return .fixture("card_offer")
        case ("GET", "account/card-orders"): return .fixture("card_orders")
        case ("POST", "account/card-orders/activate"): return .fixture("card_activated")
        case ("GET", "account/cars"): return .fixture("cars")
        case ("POST", "account/cars"): return .json(#"{"id":"17e97d10-b40a-4f65-b976-840049b5fc56","revision":0}"#)
        case ("GET", "account/car-catalog"): return .fixture("catalog")
        case ("GET", "account/autocharge/candidates"): return .json(#"[{"transaction_id":7,"station_id":"PAR-02","connector_id":1,"started_at":"2026-11-04T09:17:07Z","vid":"VID:ABC","vehicle_id":null}]"#)
        case ("GET", "account/notifications"), ("PUT", "account/notifications"): return .fixture("preferences")
        case ("GET", "notifications"): return .fixture("notifications")
        case ("GET", "notifications/types"): return .fixture("types")
        case ("GET", "invoices"): return .fixture("invoices")
        case ("GET", "account/payment-methods"): return .json(#"[{"id":"pm_1","brand":"visa","last4":"4242","exp_month":8,"exp_year":2029,"default":true,"created_at":"2026-01-10T10:00:00Z"}]"#)
        case ("POST", "account/payment-methods/checkout"): return .json(#"{"url":"https://checkout.stripe.com/c/1"}"#)
        case ("GET", "transactions"): return .fixture("sessions")
        case ("POST", "transactions/start"): return .fixture("receipt")
        case ("GET", "statistics/years"): return .json("[2026,2025]")
        default: break
        }
        if request.method == "DELETE" || path.hasSuffix("/default") || ["auth/signout", "auth/signout/all", "auth/password/reset/request", "auth/mail/resend", "auth/eula/accept", "notifications/report-error", "account/devices"].contains(path) {
            return .success
        }
        if parts.starts(with: ["account", "stations"]) || parts.starts(with: ["public", "workspaces"]) {
            if parts.last == "photos" { return request.method == "GET" ? .fixture("photos") : first("photos") }
            if parts.count == 5, parts[3] == "photos" { return StubReply(status: 200, body: photoBytes) }
            if parts.last == "stations" { return .fixture("public_stations") }
            return .fixture("station")
        }
        if parts.starts(with: ["account", "cars"]) {
            if parts.last == "autocharge" {
                let vehicle = #"{"uid":"VID:ABC","vehicle_id":"17e97d10-b40a-4f65-b976-840049b5fc56","user_id":null,"enabled":true,"last_used_at":null}"#
                return request.method == "GET" ? .json("[\(vehicle)]") : .json(vehicle)
            }
            return .json(#"{"id":"17e97d10-b40a-4f65-b976-840049b5fc56","revision":3}"#)
        }
        if parts.starts(with: ["account", "tokens"]), parts.last == "wallet-pass" { return StubReply(status: 200, body: Data("pkpass".utf8)) }
        if parts.first == "transactions" {
            switch parts.last {
            case "consumptions": return .fixture("consumptions")
            case "signed-data": return .json(#"{"session_id":1,"station_id":"CONTRAT-1","format":"OCMF","start":"OCMF|{}","stop":null}"#)
            case "stop": return .fixture("receipt")
            default: return .fixture("session")
            }
        }
        if parts.first == "statistics" { return .fixture("statistics") }
        if parts.first == "invoices", parts.last == "download" { return .json(#"{"url":"https://invoice.stripe.com/i/1"}"#) }
        if parts.starts(with: ["public", "evses"]) {
            return parts.last == "checkout" ? .json(#"{"orderId":"7084987b-6653-4d4a-99b7-c1e925397ec2","checkoutUrl":"https://checkout.stripe.com/c/2"}"#) : .fixture("portal")
        }
        if parts.starts(with: ["public", "sessions"]) { return .fixture("guest") }
        return .json(#"{"status":404,"detail":"route \#(path)"}"#, status: 404)
    }

    private func server() -> StubServer {
        StubServer { Self.route($0) }
    }

    @Test func authenticationEndpoints() async throws {
        let server = server()
        let api = server.api()
        let configuration = try await api.configuration()
        #expect(configuration.server == "ev-supervision-lite")
        #expect(configuration.apiVersion == 1)
        let session = try await api.signIn(LoginRequest(tenant: "contrat", email: "a@b.fr", password: "secret"))
        #expect(session.token.hasPrefix("fixture-token"))
        #expect(session.user.role == .driver)
        try await api.signUp(SignupRequest(email: "a@b.fr", name: "Driver", password: "secret"))
        try await api.requestPasswordReset(ChallengeRequest(tenant: "contrat", email: "a@b.fr"))
        try await api.resendVerification(ChallengeRequest(tenant: "contrat", email: "a@b.fr"))
        #expect(try await api.me().eulaAccepted)
        #expect(try await api.eula().version == "1.0")
        try await api.acceptEula()
        try await api.signOut()
        try await api.signOutEverywhere()
        #expect(server.calls == [
            "GET /api/v1/public/mobile/configuration", "POST /api/v1/auth/app/signin", "POST /api/v1/auth/signon",
            "POST /api/v1/auth/password/reset/request", "POST /api/v1/auth/mail/resend", "GET /api/v1/auth/me", "GET /api/v1/auth/eula",
            "POST /api/v1/auth/eula/accept", "POST /api/v1/auth/signout", "POST /api/v1/auth/signout/all",
        ])
        let signIn = try #require(server.last("/api/v1/auth/app/signin")?.json)
        #expect(signIn as NSDictionary == ["tenant": "contrat", "email": "a@b.fr", "password": "secret"] as NSDictionary)
        #expect(server.last("/api/v1/auth/signon")?.json?["name"] as? String == "Driver")
    }

    @Test func accountAndStationEndpoints() async throws {
        let server = server()
        let api = server.api()
        #expect(try await api.workspace().components.cars)
        #expect(try await api.profile().locale == "fr")
        let updated = try await api.updateProfile(ProfileInput(name: "Thomas", phone: nil, locale: "en"))
        #expect(updated.displayName == "DRIVER contrat")
        try await api.deleteAccount()
        #expect(try await api.sites().first?.name == "Gare")
        #expect(try await api.stations().first?.id == "CONTRAT-1")
        _ = try await api.stations(search: "Gare")
        #expect(try await api.station(id: "CONTRAT-1").name == "Borne Contrat")
        _ = try await api.publicStations(workspace: "contrat")
        _ = try await api.publicStation(workspace: "contrat", id: "CONTRAT-1")
        let photos = try await api.photos(stationId: "CONTRAT-1")
        let photo = try #require(photos.first)
        #expect(try await api.photoData(photo) == Self.photoBytes)
        #expect(try await api.uploadPhoto(stationId: "CONTRAT-1", jpeg: Self.photoBytes).id == photo.id)
        try await api.deletePhoto(photo)
        #expect(server.calls == [
            "GET /api/v1/workspace", "GET /api/v1/account", "PUT /api/v1/account", "DELETE /api/v1/account", "GET /api/v1/account/sites",
            "GET /api/v1/account/stations", "GET /api/v1/account/stations", "GET /api/v1/account/stations/CONTRAT-1",
            "GET /api/v1/public/workspaces/contrat/stations", "GET /api/v1/public/workspaces/contrat/stations/CONTRAT-1",
            "GET /api/v1/account/stations/CONTRAT-1/photos", "GET \(photo.path)", "POST /api/v1/account/stations/CONTRAT-1/photos",
            "DELETE \(photo.path)",
        ])
        let searches = server.requests.filter { $0.path == "/api/v1/account/stations" }.map(\.query)
        #expect(searches == [[:], ["search": "Gare"]])
        let profile = try #require(server.requests.first { $0.method == "PUT" }?.json)
        #expect(profile["name"] as? String == "Thomas")
        #expect(profile["locale"] as? String == "en")
        #expect(server.requests.first { $0.method == "POST" && $0.path.hasSuffix("/photos") }?.header("Content-Type") == "image/jpeg")
    }

    @Test func sessionEndpoints() async throws {
        let server = server()
        let api = server.api()
        let page = try await api.sessions(status: "active", stationId: "CONTRAT-1", offset: 30, limit: 10)
        #expect(page.total >= page.items.count)
        _ = try await api.sessions()
        #expect(try await api.session(id: 1).isActive)
        #expect(!(try await api.consumptions(sessionId: 1)).isEmpty)
        #expect(try await api.signedData(sessionId: 1).format == "OCMF")
        #expect(try await api.startSession(SessionStartInput(stationId: "CONTRAT-1", connectorId: 1, tokenUid: "04A1")).state == "QUEUED")
        _ = try await api.stopSession(id: 1)
        #expect(try await api.statisticsYears() == [2026, 2025])
        #expect(try await api.statistics(metric: "consumption", year: 2026).total == 8)
        let queries = server.requests.filter { $0.path == "/api/v1/transactions" }.map(\.query)
        #expect(queries == [["status": "active", "stationId": "CONTRAT-1", "offset": "30", "limit": "10"], ["offset": "0", "limit": "30"]])
        #expect(server.last("/api/v1/statistics/consumption")?.query == ["year": "2026"])
        #expect(server.last("/api/v1/transactions/start")?.json?["tokenUid"] as? String == "04A1")
        #expect(server.calls.contains("POST /api/v1/transactions/1/stop"))
        #expect(server.calls.contains("GET /api/v1/transactions/1/signed-data"))
    }

    @Test func badgeAndVehicleEndpoints() async throws {
        let server = server()
        let api = server.api()
        #expect(try await api.tokens().count == 2)
        #expect(try await api.cardOffer().priceMinor == 1500)
        #expect(try await api.cardOrders().first?.state == .pendingPayment)
        #expect(try await api.activateCard(number: "CARTE-2").visualId == "CARTE-2")
        #expect(try await api.walletPass(uid: "04A1") == Data("pkpass".utf8))
        let cars = try await api.cars()
        let car = try #require(cars.first)
        #expect(car.data.userId != nil)
        _ = try await api.createCar(RecordInput(name: car.name, data: car.data, revision: nil))
        #expect(try await api.updateCar(id: Self.carId, RecordInput(name: "Zoé bleue", data: car.data, revision: 0)).revision == 3)
        try await api.deleteCar(id: Self.carId)
        #expect(try await api.carCatalog(search: "zoe").items.first?.data.version == "R135")
        #expect(try await api.autoChargeCandidates().first?.id == 7)
        #expect(try await api.vehicleIds(carId: Self.carId).first?.id == "VID:ABC")
        #expect(try await api.enrollAutoCharge(carId: Self.carId, transactionId: 7).enabled)
        try await api.revokeAutoCharge(carId: Self.carId, uid: "VID:ABC")

        let carPath = "/api/v1/account/cars/17e97d10-b40a-4f65-b976-840049b5fc56"
        #expect(server.calls.contains("PUT \(carPath)"))
        #expect(server.calls.contains("DELETE \(carPath)"))
        #expect(server.calls.contains("DELETE \(carPath)/autocharge/VID:ABC"))
        #expect(server.calls.contains("GET /api/v1/account/tokens/04A1/wallet-pass"))
        let created = try #require(server.requests.first { $0.method == "POST" && $0.path == "/api/v1/account/cars" }?.json)
        let createdData = try #require(created["data"] as? [String: Any])
        #expect(createdData["userId"] == nil)
        #expect(createdData["licensePlate"] as? String == "AA-123-BB")
        let updated = try #require(server.requests.first { $0.method == "PUT" }?.json)
        #expect(updated["revision"] as? Int == 0)
        #expect((updated["data"] as? [String: Any])?["userId"] == nil)
        #expect(server.last("/api/v1/account/car-catalog")?.query == ["search": "zoe", "limit": "50"])
        #expect(server.last("/api/v1/account/card-orders/activate")?.json?["number"] as? String == "CARTE-2")
        #expect(server.last("\(carPath)/autocharge")?.json?["transactionId"] as? Int == 7)
    }

    @Test func notificationAndBillingEndpoints() async throws {
        let server = server()
        let api = server.api()
        _ = try await api.notifications(offset: 50, limit: 25)
        #expect(!(try await api.notificationTypes()).isEmpty)
        var preferences = try await api.notificationPreferences()
        preferences.push = false
        _ = try await api.updateNotificationPreferences(preferences)
        try await api.reportProblem(ErrorReportInput(subject: "Borne HS", description: "Écran noir", stationId: "CONTRAT-1", connectorId: 1, phone: nil))
        try await api.registerDevice(PushDeviceInput(token: "abcd", platform: "IOS", environment: "sandbox", locale: "fr"))
        try await api.unregisterDevice(token: "abcd")
        _ = try await api.invoices()
        let invoice = UUID(uuidString: "11111111-2222-4333-8444-555555555555")!
        #expect(try await api.invoiceDownload(id: invoice).url == "https://invoice.stripe.com/i/1")
        #expect(try await api.paymentMethods().first?.last4 == "4242")
        #expect(try await api.paymentMethodCheckout().url.hasPrefix("https://checkout.stripe.com"))
        try await api.setDefaultPaymentMethod(id: "pm_1")
        try await api.deletePaymentMethod(id: "pm_1")
        #expect(server.last("/api/v1/notifications")?.query == ["offset": "50", "limit": "25"])
        #expect(server.last("/api/v1/invoices")?.query == ["offset": "0", "limit": "50"])
        #expect(server.last("/api/v1/account/notifications")?.json?["push"] as? Bool == false)
        let report = try #require(server.last("/api/v1/notifications/report-error")?.json)
        #expect(report["stationId"] as? String == "CONTRAT-1")
        #expect(report["connectorId"] as? Int == 1)
        #expect(server.last("/api/v1/account/devices")?.json?["platform"] as? String == "IOS")
        #expect(server.calls.contains("DELETE /api/v1/account/devices/abcd"))
        #expect(server.calls.contains("GET /api/v1/invoices/11111111-2222-4333-8444-555555555555/download"))
        #expect(server.calls.contains("POST /api/v1/account/payment-methods/pm_1/default"))
        #expect(server.calls.contains("DELETE /api/v1/account/payment-methods/pm_1"))
    }

    @Test func guestPortalEndpoints() async throws {
        let server = server()
        let api = server.api()
        let portal = try await api.portal(workspace: "contrat", evseId: Self.evseId)
        #expect(portal.minimumCreditMinor == 500)
        #expect(portal.tariff.energyPrice == Decimal(string: "0.39"))
        let request = CheckoutRequest(requestId: UUID(), accessToken: "secret-token", creditMinor: 2_000)
        let checkout = try await api.checkout(workspace: "contrat", evseId: Self.evseId, request: request)
        #expect(checkout.orderId == Self.orderId)
        #expect(try await api.guestSession(id: Self.orderId, accessToken: "secret-token").state == .pendingPayment)
        _ = try await api.stopGuestSession(id: Self.orderId, accessToken: "secret-token")
        let evsePath = "/api/v1/public/evses/contrat/ac5107d1-f3ad-4644-ac98-d57810f8034f"
        let sessionPath = "/api/v1/public/sessions/7084987b-6653-4d4a-99b7-c1e925397ec2"
        #expect(server.calls == ["GET \(evsePath)", "POST \(evsePath)/checkout", "GET \(sessionPath)", "POST \(sessionPath)/stop"])
        #expect(server.last("\(evsePath)/checkout")?.json?["creditMinor"] as? Int == 2_000)
        #expect(server.last(sessionPath)?.header("X-Charge-Token") == "secret-token")
        #expect(server.last("\(sessionPath)/stop")?.header("X-Charge-Token") == "secret-token")
    }
}
