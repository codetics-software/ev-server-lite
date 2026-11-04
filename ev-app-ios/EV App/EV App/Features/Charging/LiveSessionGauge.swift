//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct LiveSessionGauge: View {
    let session: ChargingSessionResponse
    let station: CustomerStationResponse?

    private var progress: Double {
        if let soc = session.stateOfCharge { return Double(soc) / 100 }
        return 1
    }

    var body: some View {
        VStack(spacing: 10) {
            ZStack {
                Circle().stroke(.fill.tertiary, lineWidth: 16)
                Circle()
                    .trim(from: 0, to: progress)
                    .stroke(Color.accentColor, style: StrokeStyle(lineWidth: 16, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                VStack(spacing: 2) {
                    if session.isActive {
                        Text(Formatters.power(session.currentPowerKw ?? 0))
                            .font(.largeTitle.bold().monospacedDigit())
                            .contentTransition(.numericText())
                    } else {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.largeTitle)
                            .foregroundStyle(.green)
                    }
                    if let soc = session.stateOfCharge {
                        Text("Battery \(soc)%").font(.subheadline).foregroundStyle(.secondary)
                    }
                }
            }
            .frame(width: 210, height: 210)
            .accessibilityElement(children: .combine)
            Text(station?.name ?? session.stationId).font(.headline)
            if let reason = session.stopReason, !session.isActive {
                Text(reason).font(.caption).foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity)
    }
}
