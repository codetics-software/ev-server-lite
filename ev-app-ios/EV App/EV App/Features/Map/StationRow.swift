//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import CoreLocation
import SwiftUI

struct StationRow: View {
    let station: CustomerStationResponse
    let location: CLLocation?

    var body: some View {
        let availability = StationAvailability(station)
        HStack(spacing: 12) {
            StationPin(availability: availability)
            VStack(alignment: .leading, spacing: 2) {
                Text(station.name).font(.headline)
                if let address = station.addressLine {
                    Text(address).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                }
                HStack(spacing: 8) {
                    Text(availability.label).foregroundStyle(availability.color)
                    if let power = station.highestPowerKw {
                        Text(Formatters.power(power))
                    }
                    if let distance = station.distance(from: location) {
                        Text(Formatters.distance(meters: distance))
                    }
                }
                .font(.caption)
            }
            Spacer(minLength: 0)
        }
        .contentShape(.rect)
        .accessibilityElement(children: .combine)
    }
}
