//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

enum StationAvailability {
    case available(Int)
    case busy
    case outOfOrder
    case offline

    init(_ station: CustomerStationResponse) {
        guard station.connected else {
            self = .offline
            return
        }
        let statuses = station.connectors.map { ConnectorStatus($0.status) }
        let free = statuses.filter(\.isStartable).count
        if free > 0 {
            self = .available(free)
        } else if !statuses.isEmpty, statuses.allSatisfy({ $0 == .faulted || $0 == .unavailable }) {
            self = .outOfOrder
        } else {
            self = .busy
        }
    }

    var label: LocalizedStringResource {
        switch self {
        case .available(let count): "\(count) available"
        case .busy: "In use"
        case .outOfOrder: "Out of order"
        case .offline: "Offline"
        }
    }

    var color: Color {
        switch self {
        case .available: .green
        case .busy: .blue
        case .outOfOrder: .red
        case .offline: .gray
        }
    }

    var isAvailable: Bool {
        if case .available = self { return true }
        return false
    }
}
