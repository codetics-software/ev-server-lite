//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct VehicleRow: View {
    let car: RecordResponse<CarData>
    let isDefault: Bool

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "car.side.fill")
                .foregroundStyle(Color.accentColor)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(car.name).font(.headline)
                HStack(spacing: 8) {
                    if let plate = car.data.licensePlate {
                        Text(plate).font(.caption.monospaced())
                    }
                    if let battery = car.data.batteryKwh {
                        Text(Formatters.energy(battery)).font(.caption)
                    }
                    if isDefault {
                        Text("Default").font(.caption.bold()).foregroundStyle(Color.accentColor)
                    }
                }
                .foregroundStyle(.secondary)
            }
        }
        .accessibilityElement(children: .combine)
    }
}
