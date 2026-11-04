//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated struct AutoChargeCandidate: Decodable, Sendable, Identifiable {
    let transactionId: Int
    let stationId: String
    let connectorId: Int
    let startedAt: Date
    let vid: String
    let vehicleId: UUID?

    var id: Int { transactionId }
}
