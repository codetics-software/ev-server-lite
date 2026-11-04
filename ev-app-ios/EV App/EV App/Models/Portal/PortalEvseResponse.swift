//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated struct PortalEvseResponse: Decodable, Sendable, Identifiable, Hashable {
    let id: UUID
    let workspace: String
    let workspaceSlug: String
    let name: String
    let stationId: String
    let connectorId: Int
    let connected: Bool
    let status: String
    let available: Bool
    let tariff: ChargingTariff
    let minimumCreditMinor: Int
    let maximumCreditMinor: Int
}
