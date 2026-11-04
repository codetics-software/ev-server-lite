//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated struct VehicleIdResponse: Decodable, Sendable, Identifiable {
    let uid: String
    let vehicleId: UUID
    let userId: UUID?
    let enabled: Bool
    let lastUsedAt: Date?

    var id: String { uid }
}
