//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import CoreLocation
import Foundation

struct StationFilter: Equatable {
    var onlyAvailable = false
    var connectorType: String?
    var minimumPowerKw: Double = 0

    var isActive: Bool {
        onlyAvailable || connectorType != nil || minimumPowerKw > 0
    }

    func apply(to stations: [CustomerStationResponse], search: String, from location: CLLocation?) -> [CustomerStationResponse] {
        let term = search.trimmingCharacters(in: .whitespacesAndNewlines)
        let matching = stations.filter { station in
            if onlyAvailable, !StationAvailability(station).isAvailable { return false }
            if let connectorType, !station.connectors.contains(where: { $0.type == connectorType }) { return false }
            if minimumPowerKw > 0, (station.highestPowerKw ?? 0) < minimumPowerKw { return false }
            if term.isEmpty { return true }
            return [station.name, station.id, station.siteName, station.city, station.address]
                .compactMap { $0 }
                .contains { $0.localizedStandardContains(term) }
        }
        return matching.sorted { first, second in
            switch (first.distance(from: location), second.distance(from: location)) {
            case let (left?, right?): left < right
            case (.some, nil): true
            case (nil, .some): false
            case (nil, nil): first.name.localizedStandardCompare(second.name) == .orderedAscending
            }
        }
    }
}
