//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated struct StatisticsResponse: Decodable, Sendable {
    let metric: String
    let year: Int
    let unit: String
    let currency: String?
    let groups: [StatisticsGroup]
    let months: [Double]
    let total: Double
}
