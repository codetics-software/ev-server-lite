//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

enum StatisticsMetric: String, CaseIterable, Identifiable {
    case consumption
    case usage
    case inactivity
    case sessions
    case pricing

    var id: String { rawValue }

    var label: LocalizedStringResource {
        switch self {
        case .consumption: "Energy"
        case .usage: "Charging time"
        case .inactivity: "Idle time"
        case .sessions: "Sessions"
        case .pricing: "Spending"
        }
    }

    func format(_ value: Double, currency: String?) -> String {
        switch self {
        case .consumption: Formatters.energy(value)
        case .usage, .inactivity: Formatters.duration(seconds: Int(value * 3600))
        case .sessions: value.formatted(.number.precision(.fractionLength(0)))
        case .pricing: Decimal(value).formatted(.currency(code: currency ?? "EUR"))
        }
    }
}
