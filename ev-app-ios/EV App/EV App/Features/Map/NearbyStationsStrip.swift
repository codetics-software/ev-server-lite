//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import CoreLocation
import SwiftUI

struct NearbyStationsStrip: View {
    let stations: [CustomerStationResponse]
    let location: CLLocation?
    let onSelect: (CustomerStationResponse) -> Void

    var body: some View {
        if !stations.isEmpty {
            ScrollView(.horizontal) {
                HStack(spacing: 10) {
                    ForEach(stations) { station in
                        Button {
                            onSelect(station)
                        } label: {
                            StationCard(station: station, location: location)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .scrollTargetLayout()
                .padding(.horizontal)
                .padding(.vertical, 8)
            }
            .scrollIndicators(.hidden)
            .fixedSize(horizontal: false, vertical: true)
            .scrollTargetBehavior(.viewAligned)
        }
    }
}
