//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import CoreLocation
import Foundation

nonisolated struct CustomerStationResponse: Decodable, Sendable, Identifiable, Hashable {
    let id: String
    let name: String
    let vendor: String?
    let model: String?
    let connected: Bool
    let lastSeen: Date?
    let connectors: [ConnectorResponse]
    let `public`: Bool
    let latitude: Double?
    let longitude: Double?
    let maximumPowerKw: Double?
    let autoCharge: Bool
    let siteId: UUID?
    let siteAreaId: UUID?
    let siteName: String?
    let address: String?
    let postalCode: String?
    let city: String?
    let country: String?
    let publicEvses: [CustomerEvseLink]
    let photoCount: Int?
    let tariff: CustomerTariff?
}
