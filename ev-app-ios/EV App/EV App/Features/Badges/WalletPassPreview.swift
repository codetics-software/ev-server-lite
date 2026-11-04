//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import CoreImage.CIFilterBuiltins
import SwiftUI

struct WalletPassPreview: View {
    let token: ChargingTokenResponse
    let holder: String
    let network: String

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Image(.appLogo)
                    .resizable()
                    .frame(width: 32, height: 32)
                    .clipShape(.rect(cornerRadius: 7))
                    .accessibilityHidden(true)
                Text(network).font(.headline)
                Spacer()
                Image(systemName: "wave.3.right").font(.title3)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text("Driver").font(.caption2).textCase(.uppercase).opacity(0.75)
                Text(holder).font(.title2.weight(.semibold))
            }
            VStack(alignment: .leading, spacing: 2) {
                Text("Badge").font(.caption2).textCase(.uppercase).opacity(0.75)
                Text(token.visualId ?? token.uid).font(.body.monospaced())
            }
            if let code = QRImage.make(from: token.uid) {
                Image(uiImage: code)
                    .interpolation(.none)
                    .resizable()
                    .frame(width: 104, height: 104)
                    .padding(8)
                    .background(.white, in: .rect(cornerRadius: 8))
                    .frame(maxWidth: .infinity)
                    .accessibilityLabel("Badge QR code")
            }
        }
        .foregroundStyle(.white)
        .padding(18)
        .background(Color(red: 0, green: 0.376, blue: 0.22), in: .rect(cornerRadius: 16))
    }
}
