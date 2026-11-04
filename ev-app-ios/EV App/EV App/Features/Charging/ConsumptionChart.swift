//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Charts
import SwiftUI

struct ConsumptionChart: View {
    let points: [ConsumptionPoint]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Charging power").font(.subheadline.weight(.semibold))
            Chart(points) { point in
                if let power = point.powerKw {
                    AreaMark(x: .value("Time", point.at), y: .value("kW", power))
                        .foregroundStyle(.linearGradient(colors: [.accentColor.opacity(0.35), .accentColor.opacity(0.03)], startPoint: .top, endPoint: .bottom))
                        .interpolationMethod(.monotone)
                    LineMark(x: .value("Time", point.at), y: .value("kW", power))
                        .foregroundStyle(Color.accentColor)
                        .interpolationMethod(.monotone)
                }
            }
            .chartYAxisLabel("kW")
            .frame(height: 170)
        }
    }
}
