//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct RootView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        @Bindable var model = model
        Group {
            switch model.launch {
            case .checking:
                LaunchView()
            case .misconfigured(let issues):
                ConfigurationIssuesView(issues: issues)
            case .unreachable(let message):
                ServerUnavailableView(message: message)
            case .incompatible:
                ServerUnavailableView(message: String(localized: "This server is not compatible with this version of the app."))
            case .ready:
                if let principal = model.principal, !principal.eulaAccepted {
                    EulaScreen()
                } else {
                    MainTabView()
                }
            }
        }
        .alert(
            "Something went wrong",
            isPresented: Binding(get: { model.alertMessage != nil }, set: { if !$0 { model.alertMessage = nil } })
        ) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(model.alertMessage ?? "")
        }
    }
}
