//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct BadgeRow: View {
    let token: ChargingTokenResponse

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "creditcard.fill")
                .foregroundStyle(token.enabled ? Color.accentColor : .secondary)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(token.visualId ?? token.description ?? token.uid).font(.headline)
                HStack(spacing: 6) {
                    Text(token.enabled ? "Active" : "Disabled")
                        .foregroundStyle(token.enabled ? .green : .secondary)
                    if token.default {
                        Text("Default")
                    }
                }
                .font(.caption)
            }
        }
        .accessibilityElement(children: .combine)
    }
}
