//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct SignedMeterSection: View {
    let data: SignedMeterData

    var body: some View {
        Section {
            if let format = data.format {
                LabeledContent("Format", value: format)
            }
            if let start = data.start {
                DisclosureGroup("Start reading") {
                    Text(start).font(.caption2.monospaced()).textSelection(.enabled)
                }
            }
            if let stop = data.stop {
                DisclosureGroup("End reading") {
                    Text(stop).font(.caption2.monospaced()).textSelection(.enabled)
                }
            }
        } header: {
            Text("Signed meter data")
        } footer: {
            Text("These signed readings let you check the metered energy with a transparency tool.")
        }
    }
}
