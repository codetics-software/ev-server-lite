//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated struct ConsumptionPoint: Decodable, Sendable, Identifiable, Hashable {
    let at: Date
    let energyKwh: Double?
    let powerKw: Double?
    let soc: Int?
    let amperageA: Double?
    let voltageV: Double?
    let cumulatedPrice: Decimal?

    var id: Date { at }
}
