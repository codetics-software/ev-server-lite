//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct BadgesScreen: View {
    @Environment(AppModel.self) private var model
    @State private var outcome: BadgeMatcher.Outcome?
    @State private var errorMessage: String?
    @State private var isScanning = false
    @State private var showsSimulator = false
    @State private var showsActivation = false
    @State private var offer: CardOfferResponse?
    @State private var orders: [CardOrderResponse] = []

    private var tokens: [ChargingTokenResponse] {
        model.charging.tokens.filter { !$0.uid.uppercased().hasPrefix("VID:") }
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Button {
                        Task { await scan() }
                    } label: {
                        ChargeActionLabel(
                            title: "Check a card with NFC",
                            detail: "Hold your card near the top of the iPhone to see whether it is on your account.",
                            systemImage: "wave.3.right.circle.fill"
                        )
                    }
                    .disabled(isScanning)
                    .accessibilityIdentifier("badges.scan")
                    if let errorMessage {
                        ErrorBanner(message: errorMessage)
                    }
                }
                if let outcome {
                    NFCResultSection(outcome: outcome)
                }
                CardPurchaseSection(offer: offer, orders: orders) { showsActivation = true }
                Section("My badges") {
                    if tokens.isEmpty {
                        Text("No badge is registered on your account yet. Ask your charging operator to assign one.")
                            .foregroundStyle(.secondary)
                    }
                    ForEach(tokens) { token in
                        NavigationLink(value: token) {
                            BadgeRow(token: token)
                        }
                    }
                }
            }
            .navigationTitle("Badges")
            .navigationDestination(for: ChargingTokenResponse.self) { BadgeDetailScreen(token: $0) }
            .task { await load() }
            .refreshable { await load() }
            .sheet(isPresented: $showsActivation) {
                ActivateCardScreen { await load() }
            }
            .sheet(isPresented: $showsSimulator) {
                NFCUnavailableScreen { uid in
                    showsSimulator = false
                    evaluate(uid)
                }
            }
        }
    }

    private func load() async {
        await model.charging.refresh()
        offer = try? await model.api?.cardOffer()
        orders = (try? await model.api?.cardOrders()) ?? []
    }

    private func scan() async {
        errorMessage = nil
        guard NFCBadgeReader.isAvailable else {
            showsSimulator = true
            return
        }
        isScanning = true
        defer { isScanning = false }
        do {
            let reader = NFCBadgeReader(
                alert: String(localized: "Hold your charging card near the top of the iPhone."),
                success: String(localized: "Card read.")
            )
            if let uid = try await reader.scan() {
                await model.charging.refresh()
                evaluate(uid)
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func evaluate(_ uid: String) {
        withAnimation { outcome = BadgeMatcher.match(uid: uid, tokens: model.charging.tokens) }
    }
}
