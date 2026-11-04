//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct LaunchView: View {
    var body: some View {
        VStack(spacing: 20) {
            Image(.appLogo)
                .resizable()
                .scaledToFit()
                .frame(width: 112, height: 112)
                .clipShape(.rect(cornerRadius: 24))
                .accessibilityHidden(true)
            ProgressView("Connecting to the server…")
        }
    }
}
