//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct ActivateCardScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let onActivated: () async -> Void

    @State private var number = ""
    @State private var activated: ChargingTokenResponse?
    @State private var isWorking = false
    @State private var errorMessage: String?
    @State private var showsSimulator = false

    private var nfcAvailable: Bool {
        if NFCBadgeReader.isAvailable { return true }
        #if DEBUG
        return model.isDemo
        #else
        return false
        #endif
    }

    var body: some View {
        NavigationStack {
            Form {
                if let activated {
                    Section {
                        Label {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Your card is active.").font(.headline)
                                Text("Card \(activated.visualId ?? activated.uid) can now start charging on this network.")
                                    .font(.footnote)
                                    .foregroundStyle(.secondary)
                            }
                        } icon: {
                            Image(systemName: "checkmark.seal.fill").foregroundStyle(.green)
                        }
                        .accessibilityIdentifier("activation.success")
                    }
                } else {
                    if nfcAvailable {
                        Section {
                            Button {
                                Task { await scan() }
                            } label: {
                                ChargeActionLabel(
                                    title: "Scan my card",
                                    detail: "Hold the card near the top of the iPhone.",
                                    systemImage: "wave.3.right.circle.fill"
                                )
                            }
                            .disabled(isWorking)
                            .accessibilityIdentifier("activation.scan")
                        }
                    }
                    Section {
                        TextField("Card number", text: $number)
                            .textInputAutocapitalization(.characters)
                            .autocorrectionDisabled()
                            .submitLabel(.done)
                            .onSubmit { Task { await activate(number) } }
                            .accessibilityIdentifier("activation.number")
                        Button("Activate") { Task { await activate(number) } }
                            .bold()
                            .disabled(isWorking || number.trimmingCharacters(in: .whitespaces).isEmpty)
                            .accessibilityIdentifier("activation.submit")
                    } header: {
                        Text("Or enter its number")
                    } footer: {
                        Text("Use the number printed on the card or given by your operator. Only a card you ordered on this account can be activated.")
                    }
                    if isWorking {
                        Section { ProgressView() }
                    }
                    if let errorMessage {
                        Section { ErrorBanner(message: errorMessage) }
                    }
                }
            }
            .navigationTitle("Activate a card")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(activated == nil ? "Cancel" : "Close") { dismiss() }
                        .accessibilityIdentifier("activation.close")
                }
            }
            .sheet(isPresented: $showsSimulator) {
                NFCUnavailableScreen { uid in
                    showsSimulator = false
                    Task { await activate(uid) }
                }
            }
        }
    }

    private func scan() async {
        errorMessage = nil
        guard NFCBadgeReader.isAvailable else {
            showsSimulator = true
            return
        }
        do {
            let reader = NFCBadgeReader(
                alert: String(localized: "Hold your charging card near the top of the iPhone."),
                success: String(localized: "Card read.")
            )
            if let uid = try await reader.scan() {
                await activate(uid)
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func activate(_ value: String) async {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, !isWorking else { return }
        isWorking = true
        errorMessage = nil
        defer { isWorking = false }
        do {
            activated = try await model.api?.activateCard(number: trimmed)
            await onActivated()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
