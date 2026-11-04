//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

enum ConnectorStatus {
    case available
    case pluggedIn
    case charging
    case paused
    case finishing
    case reserved
    case unavailable
    case faulted
    case unknown

    init(_ value: String) {
        switch value {
        case "Available": self = .available
        case "Preparing": self = .pluggedIn
        case "Charging": self = .charging
        case "SuspendedEV", "SuspendedEVSE": self = .paused
        case "Finishing": self = .finishing
        case "Reserved": self = .reserved
        case "Unavailable": self = .unavailable
        case "Faulted": self = .faulted
        default: self = .unknown
        }
    }

    var label: LocalizedStringResource {
        switch self {
        case .available: "Available"
        case .pluggedIn: "Plugged in"
        case .charging: "Charging"
        case .paused: "Paused"
        case .finishing: "Finishing"
        case .reserved: "Reserved"
        case .unavailable: "Unavailable"
        case .faulted: "Out of order"
        case .unknown: "Unknown"
        }
    }

    var color: Color {
        switch self {
        case .available: .green
        case .pluggedIn, .charging, .paused, .finishing: .blue
        case .reserved: .orange
        case .unavailable, .unknown: .gray
        case .faulted: .red
        }
    }

    var isStartable: Bool {
        self == .available || self == .pluggedIn
    }
}
