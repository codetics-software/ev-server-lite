//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct NFCUnavailableScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let onSimulate: (String) -> Void

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Label("This device cannot read NFC cards. Use an iPhone 7 or later.", systemImage: "wave.3.right.circle")
                }
                #if DEBUG
                if model.isDemo {
                    Section("Debug only") {
                        Button("Simulate my registered card") { onSimulate(DemoData.ownCard) }
                            .accessibilityIdentifier("nfc.simulate.own")
                        Button("Simulate my ordered card") { onSimulate(DemoData.orderedCard) }
                            .accessibilityIdentifier("nfc.simulate.ordered")
                        Button("Simulate an unknown card") { onSimulate("04FFEEDDCCBB80") }
                            .accessibilityIdentifier("nfc.simulate.unknown")
                    }
                }
                #endif
            }
            .navigationTitle("NFC")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium])
    }
}
