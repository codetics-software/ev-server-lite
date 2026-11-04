//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import CoreLocation
import Foundation

extension CustomerStationResponse {
    var coordinate: CLLocationCoordinate2D? {
        guard let latitude, let longitude else { return nil }
        return CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }

    var addressLine: String? {
        let cityLine = [postalCode, city].compactMap { $0 }.joined(separator: " ")
        let line = [address, cityLine.isEmpty ? nil : cityLine].compactMap { $0 }.joined(separator: ", ")
        return line.isEmpty ? nil : line
    }

    func distance(from location: CLLocation?) -> Double? {
        guard let location, let latitude, let longitude else { return nil }
        return location.distance(from: CLLocation(latitude: latitude, longitude: longitude))
    }

    var highestPowerKw: Double? {
        connectors.compactMap(\.maximumPowerKw).max() ?? maximumPowerKw
    }
}
