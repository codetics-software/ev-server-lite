//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation
import Testing
@testable import EV_App

@Suite(.enabled(if: ProcessInfo.processInfo.environment["EV_LIVE_API_URL"] != nil), .serialized)
struct LiveServerTests {
    private let environment = ProcessInfo.processInfo.environment

    @Test func completeDriverJourneyAgainstRealServer() async throws {
        let url = try #require(URL(string: environment["EV_LIVE_API_URL"] ?? ""))
        let email = try #require(environment["EV_LIVE_EMAIL"])
        let password = try #require(environment["EV_LIVE_PASSWORD"])
        let workspace = try #require(environment["EV_LIVE_WORKSPACE"])
        let badge = try #require(environment["EV_LIVE_BADGE"])
        let stationId = try #require(environment["EV_LIVE_STATION"])
        let api = EVServerAPI(client: APIClient(baseURL: url, configuration: .ephemeral))

        let configuration = try await api.configuration()
        #expect(configuration.server == "ev-supervision-lite")

        do {
            _ = try await api.signIn(LoginRequest(tenant: workspace, email: email, password: "wrong-password"))
            Issue.record("wrong password accepted")
        } catch let error as APIError {
            #expect(error.status == 401)
            #expect(error.errorDescription == "Identifiants incorrects.")
        }

        let session = try await api.signIn(LoginRequest(tenant: workspace, email: email, password: password))
        api.client.bearerToken = session.token
        #expect(session.expiresAt > .now.addingTimeInterval(25 * 86_400))
        #expect(try await api.me().email == email)
        #expect(try await api.workspace().slug == workspace)

        let tokens = try await api.tokens()
        #expect(BadgeMatcher.match(uid: badge, tokens: tokens) != .notRegistered(uid: BadgeMatcher.normalize(badge)))
        #expect(BadgeMatcher.match(uid: "00FF00FF", tokens: tokens) == .notRegistered(uid: "00FF00FF"))

        let stations = try await api.stations()
        let station = try #require(stations.first { $0.id == stationId })
        #expect(station.connected)
        #expect(station.coordinate != nil)
        #expect(try await api.publicStations(workspace: workspace).contains { $0.id == stationId })

        let device = String(repeating: "ab", count: 32)
        try await api.registerDevice(PushDeviceInput(token: device, platform: "IOS", environment: "sandbox", locale: "fr"))
        try await api.unregisterDevice(token: device)

        var preferences = try await api.notificationPreferences()
        preferences.push = false
        preferences.types["END_OF_CHARGE"] = false
        let saved = try await api.updateNotificationPreferences(preferences)
        #expect(saved.push == false)
        #expect(saved.types["END_OF_CHARGE"] == false)

        let saved1 = try await api.createCar(RecordInput(name: "Zoé", data: CarData(licensePlate: "AA-123-BB", make: "Renault", model: "Zoé", batteryKwh: 52, default: true), revision: nil))
        var cars = try await api.cars()
        var car = try #require(cars.first { $0.id == saved1.id })
        #expect(car.data.batteryKwh == 52)
        #expect(try await api.profile().defaultCarId == saved1.id)
        var edited = car.data
        edited.licensePlate = "CC-456-DD"
        _ = try await api.updateCar(id: car.id, RecordInput(name: "Zoé bleue", data: edited, revision: car.revision))
        cars = try await api.cars()
        car = try #require(cars.first { $0.id == saved1.id })
        #expect(car.name == "Zoé bleue")
        try await api.deleteCar(id: car.id)

        let jpeg = try #require(UIImageFixture.jpeg())
        let photo = try await api.uploadPhoto(stationId: stationId, jpeg: jpeg)
        #expect(try await api.photos(stationId: stationId).contains { $0.id == photo.id })
        #expect(try await api.photoData(photo) == jpeg)
        try await api.deletePhoto(photo)

        _ = try await api.startSession(SessionStartInput(stationId: stationId, connectorId: 1, tokenUid: badge))
        var active: ChargingSessionResponse?
        for _ in 0..<40 {
            try await Task.sleep(for: .seconds(1))
            active = try await api.sessions(status: "active", stationId: stationId, limit: 5).items.first
            if let active, (active.energyKwh ?? 0) > 0, active.currentPowerKw != nil { break }
        }
        let running = try #require(active)
        #expect(running.tokenUid == badge)
        #expect((running.energyKwh ?? 0) > 0)
        #expect((running.currentPowerKw ?? 0) > 0)
        #expect(!(try await api.consumptions(sessionId: running.id)).isEmpty)
        let liveStation = try await api.station(id: stationId)
        #expect(liveStation.connectors.first?.currentTransactionId == running.id)

        _ = try await api.stopSession(id: running.id)
        var finished: ChargingSessionResponse?
        for _ in 0..<30 {
            try await Task.sleep(for: .seconds(1))
            let latest = try await api.session(id: running.id)
            if !latest.isActive {
                finished = latest
                break
            }
        }
        let ended = try #require(finished)
        #expect(ended.stoppedAt != nil)
        #expect(try await api.statistics(metric: "consumption", year: Calendar.current.component(.year, from: .now)).total > 0)
        #expect(try await api.notifications().total >= 0)

        try await api.signOut()
        do {
            _ = try await api.me()
            Issue.record("session still valid after sign out")
        } catch let error as APIError {
            #expect(error == .unauthorized)
        }
    }
}
