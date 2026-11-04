//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated struct ChargingTokenResponse: Decodable, Sendable, Identifiable, Hashable {
    let uid: String
    let userId: UUID?
    let type: String
    let enabled: Bool
    let expiresAt: Date?
    let visualId: String?
    let description: String?
    let userName: String?
    let `default`: Bool
    let lastUsedAt: Date?
    let vehicleId: UUID?
    let vehicleName: String?

    var id: String { uid }
}
