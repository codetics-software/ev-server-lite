//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated struct CarCatalogData: Decodable, Sendable, Hashable {
    let make: String
    let model: String
    let version: String?
    let batteryKwh: Double?
    let maxAcPowerKw: Double?
    let maxDcPowerKw: Double?
    let rangeKm: Int?
    let imageUrl: String?
}
