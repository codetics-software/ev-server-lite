//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct EulaScreen: View {
    @Environment(AppModel.self) private var model
    @State private var eula: EulaResponse?
    @State private var errorMessage: String?
    @State private var isWorking = false

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text("Before you continue, please accept the terms of use of this charging service.")
                }
                if let eula {
                    Section("Terms") {
                        LabeledContent("Version", value: eula.version)
                        Text(eula.attribution)
                        if let url = URL(string: eula.url) {
                            Link(eula.url, destination: url)
                        }
                    }
                }
                if let errorMessage {
                    Section { ErrorBanner(message: errorMessage) }
                }
                Section {
                    Button("Accept and continue") { Task { await accept() } }
                        .bold()
                        .disabled(isWorking)
                    Button("Sign out", role: .destructive) { Task { await model.signOut() } }
                }
            }
            .navigationTitle("Terms of use")
            .task { eula = try? await model.api?.eula() }
        }
    }

    private func accept() async {
        isWorking = true
        defer { isWorking = false }
        do {
            try await model.acceptEula()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
