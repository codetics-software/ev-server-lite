//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated struct StatisticsGroup: Decodable, Sendable, Identifiable {
    let key: String
    let label: String
    let months: [Double]
    let total: Double

    var id: String { key }
}
