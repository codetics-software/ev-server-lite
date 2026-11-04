//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct NotificationsScreen: View {
    @Environment(AppModel.self) private var model
    @State private var items: [NotificationResponse] = []
    @State private var isLoading = true
    @State private var errorMessage: String?

    var body: some View {
        List {
            Section {
                NavigationLink {
                    NotificationPreferencesScreen()
                } label: {
                    Label("Preferences", systemImage: "slider.horizontal.3")
                }
                .accessibilityIdentifier("notifications.preferences")
            }
            if let errorMessage {
                ErrorBanner(message: errorMessage)
            }
            Section("History") {
                if items.isEmpty, !isLoading {
                    Text("No notifications yet.").foregroundStyle(.secondary)
                }
                ForEach(items) { item in
                    VStack(alignment: .leading, spacing: 2) {
                        Text(item.subject).font(.subheadline.weight(.medium))
                        Text(item.createdAt, format: .dateTime.day().month().hour().minute())
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    .accessibilityElement(children: .combine)
                }
            }
        }
        .navigationTitle("Notifications")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
        .refreshable { await load() }
    }

    private func load() async {
        defer { isLoading = false }
        do {
            items = try await model.api?.notifications().items ?? []
            errorMessage = nil
        } catch is CancellationError {
            return
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
