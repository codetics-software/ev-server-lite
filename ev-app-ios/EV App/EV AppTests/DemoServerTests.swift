//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation
import Testing
@testable import EV_App

extension DemoBackedTests {
    @MainActor
    struct DemoServerTests {
        @Test func advertisesACompatibleServer() async throws {
            let configuration = try await DemoEnvironment.api().configuration()
            #expect(configuration.server == "ev-supervision-lite")
            #expect(configuration.apiVersion == 1)
            #expect(configuration.signupEnabled)
            #expect(configuration.publicTenant == "demo")
        }

        @Test func refusesWrongCredentials() async throws {
            let api = DemoEnvironment.api()
            await #expect(throws: APIError.server(status: 401, message: "Identifiants incorrects.", code: nil)) {
                _ = try await api.signIn(LoginRequest(tenant: "demo", email: DemoData.email, password: "wrong"))
            }
            let session = try await api.signIn(LoginRequest(tenant: "demo", email: DemoData.email, password: DemoData.password))
            #expect(session.user.email == DemoData.email)
            #expect(session.expiresAt > .now.addingTimeInterval(25 * 86_400))
        }

        @Test func publishesStationsAndTheirDetails() async throws {
            let api = DemoEnvironment.api()
            let stations = try await api.publicStations(workspace: "demo")
            #expect(stations.map(\.id) == ["PAR-01", "PAR-02", "PAR-03", "PAR-04", "PAR-05", "PAR-06", "PAR-07"])
            let gare = try #require(stations.first)
            #expect(gare.publicEvses.first?.id == UUID(uuidString: DemoData.evseId))
            #expect(gare.tariff == nil)
            #expect(stations.first { $0.id == "PAR-05" }?.connected == false)
            #expect(stations.first { $0.id == "PAR-02" }?.autoCharge == true)
            let detail = try await api.publicStation(workspace: "demo", id: "PAR-06")
            #expect(detail.tariff.map { NSDecimalNumber(decimal: $0.energyPrice ?? 0).doubleValue } == 0.39)
            #expect(detail.connectors.map(\.type) == ["CCS", "CHADEMO"])
            #expect(detail.highestPowerKw == 50)
            await #expect(throws: APIError.server(status: 404, message: "Borne introuvable.", code: nil)) {
                _ = try await api.publicStation(workspace: "demo", id: "NOPE")
            }
        }

        @Test func describesTheDriverAccount() async throws {
            let api = try await DemoEnvironment.signedInAPI()
            #expect(try await api.me().name == "Thomas Bernard")
            #expect(try await api.workspace().components.statistics)
            let profile = try await api.profile()
            #expect(profile.displayName == "Thomas Bernard")
            #expect(try await api.sites().count == 1)
            #expect(try await api.eula().attribution.contains("Codetics"))
            let tokens = try await api.tokens()
            #expect(tokens.contains { $0.uid == DemoData.ownCard && $0.default })
            #expect(tokens.contains { !$0.enabled })
            #expect(try await api.cardOffer().priceMinor == 1500)
            let methods = try await api.paymentMethods()
            #expect(methods.filter(\.default).map(\.last4) == ["4242"])
            #expect(try await api.paymentMethodCheckout().url == "https://stripe.com")
            #expect(try await api.invoices().total == 3)
            #expect(try await api.invoiceDownload(id: UUID()).url == "https://stripe.com")
            #expect(try await api.notifications().items.count == 4)
            #expect(try await api.autoChargeCandidates().isEmpty)
            await #expect(throws: APIError.self) { _ = try await api.walletPass(uid: DemoData.ownCard) }
        }

        @Test func savesTheProfileWithoutBlockingTheServer() async throws {
            let api = try await DemoEnvironment.signedInAPI()
            let saved = try await api.updateProfile(ProfileInput(name: "Thomas Bernard", phone: "+33 6 12 34 56 78", locale: "fr"))
            #expect(saved.email == DemoData.email)
            #expect(try await api.me().name == "Thomas Bernard")
        }

        @Test func activatesTheOrderedCardOnlyOnce() async throws {
            let api = try await DemoEnvironment.signedInAPI()
            await #expect(throws: APIError.self) { _ = try await api.activateCard(number: "UNKNOWN-1") }
            if try await api.cardOrders().first?.state == .shipped {
                let token = try await api.activateCard(number: "carte 2210")
                #expect(token.uid == DemoData.orderedCard)
                #expect(token.visualId == DemoData.orderedCardNumber)
            }
            #expect(try await api.cardOrders().first?.state == .activated)
            #expect(try await api.tokens().contains { $0.uid == DemoData.orderedCard })
            await #expect(throws: APIError.self) { _ = try await api.activateCard(number: DemoData.orderedCardNumber) }
        }

        @Test func runsAChargingSessionFromStartToStop() async throws {
            let api = try await DemoEnvironment.signedInAPI()
            try await DemoEnvironment.stopLiveSessions(api)
            let receipt = try await api.startSession(SessionStartInput(stationId: "PAR-07", connectorId: 1, tokenUid: DemoData.ownCard))
            #expect(receipt.state == "QUEUED")
            await #expect(throws: APIError.server(status: 409, message: "Une session est déjà en cours sur ce connecteur.", code: nil)) {
                _ = try await api.startSession(SessionStartInput(stationId: "PAR-07", connectorId: 1, tokenUid: DemoData.ownCard))
            }
            let active = try #require(try await api.sessions(status: "active", stationId: "PAR-07").items.first)
            #expect(active.isActive)
            #expect(active.tokenUid == DemoData.ownCard)
            #expect((active.currentPowerKw ?? 0) > 9)
            #expect(try await api.sessions(status: "active", stationId: "PAR-01").items.isEmpty)
            let station = try await api.station(id: "PAR-07")
            #expect(station.connectors.first?.status == "Charging")
            #expect(station.connectors.first?.currentTransactionId == active.id)
            #expect(try await api.session(id: active.id).id == active.id)
            #expect(try await api.consumptions(sessionId: active.id).count == 30)

            _ = try await api.stopSession(id: active.id)
            #expect(try await api.sessions(status: "active").items.isEmpty)
            let finished = try await api.session(id: active.id)
            #expect(!finished.isActive)
            #expect(finished.stopReason == "Remote")
            #expect(finished.billingStatus == "PENDING")
            #expect(try await api.sessions(status: "completed", limit: 1).items.first?.id == active.id)
        }

        @Test func pagesTheHistory() async throws {
            let api = try await DemoEnvironment.signedInAPI()
            try await DemoEnvironment.stopLiveSessions(api)
            let first = try await api.sessions(offset: 0, limit: 4)
            let second = try await api.sessions(offset: 4, limit: 4)
            #expect(first.items.count == 4)
            #expect(first.total >= 9)
            #expect(Set(first.items.map(\.id)).isDisjoint(with: second.items.map(\.id)))
            let old = try await api.session(id: 2140)
            #expect(old.errors == [])
            #expect(old.signedMeterData)
            #expect(try await api.signedData(sessionId: 2140).format == "OCMF")
            await #expect(throws: APIError.self) { _ = try await api.session(id: 1) }
        }

        @Test func managesVehicles() async throws {
            let api = try await DemoEnvironment.signedInAPI()
            let saved = try await api.createCar(RecordInput(name: "Peugeot e-208", data: CarData(licensePlate: "AB-123-CD", make: "Peugeot", model: "e-208", batteryKwh: 51), revision: nil))
            var cars = try await api.cars()
            let created = try #require(cars.first { $0.id == saved.id })
            #expect(created.data.userId == UUID(uuidString: DemoData.userId))
            #expect(created.data.batteryKwh == 51)
            _ = try await api.updateCar(id: saved.id, RecordInput(name: "Peugeot bleue", data: created.data, revision: created.revision))
            cars = try await api.cars()
            #expect(cars.first { $0.id == saved.id }?.name == "Peugeot bleue")
            #expect(try await api.vehicleIds(carId: saved.id).isEmpty)
            try await api.deleteCar(id: saved.id)
            #expect(try await api.cars().contains { $0.id == saved.id } == false)
            let catalog = try await api.carCatalog(search: "tesla")
            #expect(catalog.items.map(\.data.model) == ["Model 3"])
            #expect(try await api.carCatalog(search: "").total == 6)
        }

        @Test func storesNotificationPreferences() async throws {
            let api = try await DemoEnvironment.signedInAPI()
            let types = try await api.notificationTypes()
            #expect(types.count == 9)
            var preferences = try await api.notificationPreferences()
            #expect(Set(preferences.types.keys) == Set(types.map(\.type)))
            preferences.push = false
            preferences.types["END_OF_CHARGE"] = false
            let saved = try await api.updateNotificationPreferences(preferences)
            #expect(saved.push == false)
            #expect(saved.types["END_OF_CHARGE"] == false)
            #expect(saved.types["SESSION_STARTED"] == true)
            preferences.push = true
            preferences.types["END_OF_CHARGE"] = true
            _ = try await api.updateNotificationPreferences(preferences)
            try await api.registerDevice(PushDeviceInput(token: "abcd", platform: "IOS", environment: "sandbox", locale: "fr"))
            try await api.unregisterDevice(token: "abcd")
            try await api.reportProblem(ErrorReportInput(subject: "x", description: "y", stationId: nil, connectorId: nil, phone: nil))
        }

        @Test func computesStatistics() async throws {
            let api = try await DemoEnvironment.signedInAPI()
            #expect(try await api.statisticsYears() == [2026, 2025])
            let energy = try await api.statistics(metric: "consumption", year: 2026)
            #expect(energy.unit == "kWh")
            #expect(energy.months.count == 12)
            #expect(energy.groups.count == 2)
            #expect(abs(energy.months.reduce(0, +) - energy.total) < 0.01)
            let sessions = try await api.statistics(metric: "sessions", year: 2025)
            #expect(sessions.months.allSatisfy { $0 == $0.rounded() })
            #expect(try await api.statistics(metric: "pricing", year: 2026).currency == "EUR")
        }

        @Test func keepsStationPhotos() async throws {
            let api = try await DemoEnvironment.signedInAPI()
            let jpeg = try #require(UIImageFixture.jpeg())
            let photo = try await api.uploadPhoto(stationId: "PAR-03", jpeg: jpeg)
            #expect(photo.mine)
            #expect(try await api.photos(stationId: "PAR-03").first?.id == photo.id)
            #expect(try await api.station(id: "PAR-03").photoCount ?? 0 >= 1)
            #expect(try await api.photoData(photo) == jpeg)
            try await api.deletePhoto(photo)
            #expect(try await api.photos(stationId: "PAR-03").contains { $0.id == photo.id } == false)
        }

        @Test func settlesAGuestPayment() async throws {
            let api = DemoEnvironment.api()
            let evse = try #require(UUID(uuidString: DemoData.evseId))
            let portal = try await api.portal(workspace: "demo", evseId: evse)
            #expect(portal.stationId == "PAR-01")
            #expect(portal.maximumCreditMinor == 10_000)
            let request = CheckoutRequest(requestId: UUID(), accessToken: "token", creditMinor: 2_000)
            let checkout = try await api.checkout(workspace: "demo", evseId: evse, request: request)
            let started = try await api.guestSession(id: checkout.orderId, accessToken: "token")
            #expect(started.state == .startRequested)
            #expect(started.transactionId == nil)
            let stopped = try await api.stopGuestSession(id: checkout.orderId, accessToken: "token")
            #expect(stopped.state == .completed)
            #expect(stopped.billedMinor == 742)
            #expect(stopped.refundedMinor == 2_000 - 742)
            await #expect(throws: APIError.server(status: 404, message: "Session introuvable.", code: nil)) {
                _ = try await api.guestSession(id: UUID(), accessToken: "token")
            }
        }

        @Test func unknownRoutesAreReported() async throws {
            let api = try await DemoEnvironment.signedInAPI()
            do {
                let _: MutationResponse = try await api.client.get("api/v1/nothing/here")
                Issue.record("unknown route answered")
            } catch let error as APIError {
                #expect(error.status == 404)
                #expect(error.errorDescription?.contains("nothing/here") == true)
            }
        }
    }
}
