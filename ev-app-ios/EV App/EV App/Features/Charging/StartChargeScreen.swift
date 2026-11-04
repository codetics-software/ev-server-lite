//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct StartChargeScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let request: StartRequest
    let onStarted: (ChargingSessionResponse) -> Void

    @State private var badge: String?
    @State private var isStarting = false
    @State private var errorMessage: String?

    private var badges: [ChargingTokenResponse] { model.charging.usableBadges }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    LabeledContent("Station", value: request.stationName)
                    LabeledContent("Connector", value: String(request.connectorId))
                }
                if badges.isEmpty {
                    Section {
                        Label("You have no active badge. Ask your charging operator to assign one to your account, or scan the QR code on the station to pay by card.", systemImage: "wave.3.right.circle")
                    }
                } else {
                    Section("Badge") {
                        Picker("Badge", selection: $badge) {
                            ForEach(badges) { token in
                                Text(token.visualId ?? token.description ?? token.uid).tag(String?.some(token.uid))
                            }
                        }
                        .pickerStyle(.inline)
                        .labelsHidden()
                    }
                }
                Section {
                    Label("Plug in your vehicle before you start.", systemImage: "powerplug.portrait")
                }
                if isStarting {
                    Section {
                        HStack(spacing: 12) {
                            ProgressView()
                            Text("Waiting for the station to confirm…")
                        }
                    }
                }
                if let errorMessage {
                    Section { ErrorBanner(message: errorMessage) }
                }
            }
            .navigationTitle("Charge")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Start") { Task { await start() } }
                        .disabled(isStarting || badge == nil)
                        .accessibilityIdentifier("start.confirm")
                }
            }
            .task {
                await model.charging.refresh()
                badge = badge ?? badges.first?.uid
            }
        }
        .interactiveDismissDisabled(isStarting)
    }

    private func start() async {
        guard let badge else { return }
        isStarting = true
        errorMessage = nil
        defer { isStarting = false }
        do {
            onStarted(try await model.charging.start(station: request.stationId, connector: request.connectorId, badge: badge))
        } catch is CancellationError {
            return
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
