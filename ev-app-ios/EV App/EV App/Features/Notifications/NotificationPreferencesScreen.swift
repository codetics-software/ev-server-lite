//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct NotificationPreferencesScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.openURL) private var openURL
    @State private var preferences: NotificationPreferences?
    @State private var types: [NotificationTypeInfo] = []
    @State private var errorMessage: String?

    var body: some View {
        Form {
            if let binding = Binding($preferences) {
                Section {
                    Toggle("Push notifications", isOn: binding.push)
                    Toggle("Emails", isOn: binding.email)
                } footer: {
                    if model.server?.pushEnabled == false {
                        Text("This server does not send push notifications yet.")
                    } else if model.push.authorization == .denied {
                        Text("Notifications are turned off for this app in iOS Settings.")
                    }
                }
                if model.push.authorization == .denied {
                    Button("Open iOS Settings") {
                        if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                    }
                }
                Section("Notify me about") {
                    ForEach(types.filter { $0.audience == "DRIVER" }) { type in
                        Toggle(type.label, isOn: Binding(
                            get: { preferences?.types[type.type] ?? true },
                            set: { preferences?.types[type.type] = $0 }
                        ))
                    }
                }
            } else if errorMessage == nil {
                ProgressView()
            }
            if let errorMessage {
                ErrorBanner(message: errorMessage)
            }
        }
        .navigationTitle("Preferences")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            do {
                async let current = model.api?.notificationPreferences()
                async let available = model.api?.notificationTypes()
                preferences = try await current
                types = try await available ?? []
            } catch {
                errorMessage = error.localizedDescription
            }
        }
        .onChange(of: preferences) { old, new in
            guard old != nil, let new else { return }
            Task {
                do {
                    _ = try await model.api?.updateNotificationPreferences(new)
                    errorMessage = nil
                } catch {
                    errorMessage = error.localizedDescription
                }
            }
        }
    }
}
