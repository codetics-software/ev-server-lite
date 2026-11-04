//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct SessionRow: View {
    let session: ChargingSessionResponse
    let stationName: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(stationName ?? session.stationId).font(.headline).lineLimit(1)
                Spacer()
                if session.isActive {
                    Text("In progress").font(.caption.bold()).foregroundStyle(Color.accentColor)
                } else {
                    Text(Formatters.price(session.price, currency: session.currency)).font(.subheadline.monospacedDigit())
                }
            }
            Text(session.startedAt, format: .dateTime.day().month().year().hour().minute())
                .font(.caption)
                .foregroundStyle(.secondary)
            Text("\(Formatters.energy(session.energyKwh)) · \(Formatters.duration(seconds: session.durationSeconds))")
                .font(.caption.monospacedDigit())
                .foregroundStyle(.secondary)
        }
        .accessibilityElement(children: .combine)
    }
}
