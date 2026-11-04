//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct ConfigurationIssuesView: View {
    @Environment(AppModel.self) private var model
    let issues: [ConfigurationIssue]

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text("This copy of the app has not been configured for a deployed server yet. Edit AppConfiguration.plist, replace every value below, then rebuild.")
                }
                Section("To fix") {
                    ForEach(issues) { issue in
                        Label(issue.message, systemImage: "xmark.octagon.fill")
                            .foregroundStyle(.red)
                    }
                }
                #if DEBUG
                Section {
                    Button("Continue with the demo account", systemImage: "play.circle.fill") {
                        Task { await model.startDemo(signIn: true) }
                    }
                    .accessibilityIdentifier("demo.start")
                    Button("Continue as a demo guest", systemImage: "person.fill.questionmark") {
                        Task { await model.startDemo(signIn: false) }
                    }
                    .accessibilityIdentifier("demo.guest")
                } header: {
                    Text("Debug only")
                } footer: {
                    Text("The demo uses simulated data and is not included in release builds.")
                }
                #endif
            }
            .navigationTitle("App not configured")
        }
    }
}
