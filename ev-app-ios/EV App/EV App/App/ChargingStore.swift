//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation
import Observation

@Observable
final class ChargingStore {
    private(set) var active: [ChargingSessionResponse] = []
    private(set) var tokens: [ChargingTokenResponse] = []
    @ObservationIgnored private var generation = 0

    var current: ChargingSessionResponse? { active.first }

    var usableBadges: [ChargingTokenResponse] {
        tokens
            .filter { $0.enabled && $0.vehicleId == nil && !$0.uid.uppercased().hasPrefix("VID:") }
            .filter { ($0.expiresAt ?? .distantFuture) > .now }
            .sorted { $0.default && !$1.default }
    }

    func reset() {
        generation += 1
        active = []
        tokens = []
    }

    func refresh() async {
        let model = AppModel.shared
        guard let api = model.api, model.isSignedIn else { return }
        let started = generation
        do {
            async let sessions = api.sessions(status: "active", limit: 10)
            async let owned = api.tokens()
            let (page, badges) = try await (sessions, owned)
            guard started == generation else { return }
            active = page.items
            tokens = badges
        } catch is CancellationError {
            return
        } catch APIError.unauthorized {
            guard started == generation else { return }
            model.sessionExpired()
        } catch {
            return
        }
    }

    func monitor() async {
        while !Task.isCancelled {
            await refresh()
            let delay: Duration = active.isEmpty ? .seconds(30) : .seconds(5)
            try? await Task.sleep(for: delay)
        }
    }

    func start(station: String, connector: Int, badge: String) async throws -> ChargingSessionResponse {
        guard let api = AppModel.shared.api else { throw APIError.invalidResponse }
        _ = try await api.startSession(SessionStartInput(stationId: station, connectorId: connector, tokenUid: badge))
        let deadline = ContinuousClock.now + .seconds(90)
        while ContinuousClock.now < deadline {
            try await Task.sleep(for: .seconds(2))
            let sessions = try await api.sessions(status: "active", stationId: station, limit: 10).items
            if let session = sessions.first(where: { $0.connectorId == connector }) {
                await refresh()
                return session
            }
        }
        throw ChargeStartError.notStarted
    }
}
