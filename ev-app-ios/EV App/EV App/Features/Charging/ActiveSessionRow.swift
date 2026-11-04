//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct ActiveSessionRow: View {
    let session: ChargingSessionResponse
    let station: CustomerStationResponse?

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "bolt.car.fill")
                .foregroundStyle(Color.accentColor)
                .symbolEffect(.pulse)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(station?.name ?? session.stationId).font(.headline)
                Text("\(Formatters.power(session.currentPowerKw ?? 0)) · \(Formatters.energy(session.energyKwh))")
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(.secondary)
            }
        }
    }
}
