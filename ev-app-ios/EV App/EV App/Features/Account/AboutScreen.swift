//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct AboutScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.openURL) private var openURL

    private var version: String {
        let info = Bundle.main.infoDictionary
        return "\(info?["CFBundleShortVersionString"] as? String ?? "") (\(info?["CFBundleVersion"] as? String ?? ""))"
    }

    var body: some View {
        List {
            Section {
                VStack(spacing: 10) {
                    Image(.appLogo)
                        .resizable()
                        .frame(width: 76, height: 76)
                        .clipShape(.rect(cornerRadius: 17))
                        .accessibilityHidden(true)
                    Text(model.brandName).font(.title3.bold())
                    Text("Version \(version)").font(.caption).foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity)
                .listRowBackground(Color.clear)
            }
            Section("Credits") {
                Text(verbatim: "EV Supervision Lite — built by Kevin Vavelin, Codetics")
                Link(destination: URL(string: "https://codetics.fr")!) {
                    Text(verbatim: "https://codetics.fr")
                }
                Text(verbatim: "Copyright © 2026 Kevin Vavelin, Codetics")
                    .font(.footnote)
                Text(verbatim: "Licence d’attribution Codetics 1.0 (LicenseRef-Codetics-Attribution-1.0)")
                    .font(.footnote)
            }
            if let configuration = model.configuration {
                Section("Support") {
                    if let mail = URL(string: "mailto:\(configuration.supportEmail)") {
                        Link(configuration.supportEmail, destination: mail)
                    }
                    Link("Privacy policy", destination: configuration.privacyPolicyURL)
                }
            }
        }
        .navigationTitle("About")
        .navigationBarTitleDisplayMode(.inline)
    }
}
