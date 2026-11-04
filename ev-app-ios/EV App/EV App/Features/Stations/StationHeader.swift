//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import CoreLocation
import SwiftUI

struct StationHeader: View {
    let station: CustomerStationResponse
    let location: CLLocation?

    var body: some View {
        let availability = StationAvailability(station)
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                Circle().fill(availability.color).frame(width: 10, height: 10)
                Text(availability.label).font(.subheadline.weight(.semibold))
                if let distance = station.distance(from: location) {
                    Text(Formatters.distance(meters: distance)).font(.subheadline).foregroundStyle(.secondary)
                }
            }
            if let site = station.siteName {
                Text(site).font(.headline)
            }
            if let address = station.addressLine {
                Text(address).font(.subheadline).foregroundStyle(.secondary)
            }
            if let vendor = [station.vendor, station.model].compactMap({ $0 }).joined(separator: " ").nilIfEmpty {
                Text(vendor).font(.caption).foregroundStyle(.tertiary)
            }
        }
        .accessibilityElement(children: .combine)
    }
}
