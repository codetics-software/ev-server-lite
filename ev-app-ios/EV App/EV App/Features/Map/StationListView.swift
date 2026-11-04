//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import CoreLocation
import SwiftUI

struct StationListView: View {
    let stations: [CustomerStationResponse]
    let location: CLLocation?
    let onSelect: (CustomerStationResponse) -> Void

    var body: some View {
        List(stations) { station in
            Button {
                onSelect(station)
            } label: {
                StationRow(station: station, location: location)
            }
            .buttonStyle(.plain)
        }
        .overlay {
            if stations.isEmpty {
                ContentUnavailableView("No stations found", systemImage: "bolt.slash", description: Text("Try removing a filter or searching for another place."))
            }
        }
    }
}
