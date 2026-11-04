//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct MySitesScreen: View {
    @Environment(AppModel.self) private var model
    @State private var sites: [UserSiteResponse] = []
    @State private var errorMessage: String?

    var body: some View {
        List {
            if let errorMessage {
                ErrorBanner(message: errorMessage)
            }
            ForEach(sites) { site in
                Label(site.name, systemImage: "building.2")
            }
        }
        .overlay {
            if sites.isEmpty, errorMessage == nil {
                ContentUnavailableView("No private sites", systemImage: "building.2", description: Text("You can use every station open to all drivers on this network."))
            }
        }
        .navigationTitle("My sites")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            do {
                sites = try await model.api?.sites() ?? []
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }
}
