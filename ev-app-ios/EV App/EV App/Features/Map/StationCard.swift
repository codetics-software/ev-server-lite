//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import CoreLocation
import SwiftUI

struct StationCard: View {
    let station: CustomerStationResponse
    let location: CLLocation?

    var body: some View {
        let availability = StationAvailability(station)
        VStack(alignment: .leading, spacing: 6) {
            Text(station.name)
                .font(.subheadline.weight(.semibold))
                .lineLimit(1)
            HStack(spacing: 6) {
                Circle().fill(availability.color).frame(width: 8, height: 8)
                Text(availability.label).font(.caption)
            }
            HStack(spacing: 8) {
                if let power = station.highestPowerKw {
                    Text(Formatters.power(power))
                }
                if let distance = station.distance(from: location) {
                    Text(Formatters.distance(meters: distance))
                }
            }
            .font(.caption)
            .foregroundStyle(.secondary)
        }
        .frame(width: 170, alignment: .leading)
        .padding(12)
        .background(.regularMaterial, in: .rect(cornerRadius: 16))
        .accessibilityElement(children: .combine)
    }
}
