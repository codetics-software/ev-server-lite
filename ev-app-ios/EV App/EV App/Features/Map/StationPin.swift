//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct StationPin: View {
    let availability: StationAvailability

    var body: some View {
        Image(systemName: "bolt.fill")
            .font(.caption.bold())
            .foregroundStyle(.white)
            .frame(width: 30, height: 30)
            .background(availability.color, in: .circle)
            .overlay { Circle().stroke(.white, lineWidth: 2) }
            .shadow(radius: 2)
            .accessibilityLabel(Text(availability.label))
    }
}
