//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated struct CarData: Codable, Sendable, Hashable {
    var userId: UUID?
    var carCatalogId: UUID?
    var vin: String?
    var licensePlate: String?
    var make: String?
    var model: String?
    var batteryKwh: Double?
    var maxAcPowerKw: Double?
    var maxDcPowerKw: Double?
    var `default`: Bool?
}
