//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated struct ConnectorResponse: Decodable, Sendable, Identifiable, Hashable {
    let connectorId: Int
    let status: String
    let errorCode: String
    let statusAt: Date
    let type: String?
    let maximumPowerKw: Double?
    let phases: Int?
    let currentPowerKw: Double?
    let currentTransactionId: Int?
    let currentEnergyKwh: Double?
    let currentSoc: Int?
    let currentUserName: String?
    let inactiveSince: Date?

    var id: Int { connectorId }
}
