//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct ChargingPointScreen: View {
    @Environment(AppModel.self) private var model
    let link: PortalLink
    let onRoute: (ChargeRoute) -> Void

    @State private var portal: PortalEvseResponse?
    @State private var errorMessage: String?
    @State private var showsStart = false
    @State private var showsCheckout = false

    private var canUseBadge: Bool {
        model.isSignedIn && model.workspace?.slug == link.workspace
    }

    var body: some View {
        List {
            if let portal {
                let status = ConnectorStatus(portal.status)
                Section {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(portal.name).font(.title3.bold())
                        Text(portal.workspace).foregroundStyle(.secondary)
                        Label {
                            Text(status.label)
                        } icon: {
                            Image(systemName: "circle.fill").foregroundStyle(status.color)
                        }
                        .font(.subheadline)
                    }
                }
                Section("Price") {
                    PortalTariffRows(tariff: portal.tariff)
                }
                if canUseBadge {
                    Section {
                        Button("Start with my badge", systemImage: "wave.3.right.circle.fill") { showsStart = true }
                            .bold()
                            .disabled(!status.isStartable || !portal.connected)
                            .accessibilityIdentifier("portal.badge")
                    } footer: {
                        Text("The session is billed to your account according to your operator's terms.")
                    }
                }
                Section {
                    Button("Pay by card without an account", systemImage: "creditcard.fill") { showsCheckout = true }
                        .disabled(!portal.available)
                        .accessibilityIdentifier("portal.guest")
                } footer: {
                    if portal.available {
                        Text("You pay a credit in advance. Whatever you do not use is refunded automatically.")
                    } else {
                        Text("Card payment is not available on this charging point right now.")
                    }
                }
            } else if let errorMessage {
                ErrorBanner(message: errorMessage)
            } else {
                ProgressView()
            }
        }
        .navigationTitle("Charging point")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showsStart) {
            if let portal {
                StartChargeScreen(
                    request: StartRequest(stationId: portal.stationId, stationName: portal.name, connectorId: portal.connectorId)
                ) { session in
                    showsStart = false
                    onRoute(.session(session.id))
                }
            }
        }
        .sheet(isPresented: $showsCheckout) {
            if let portal {
                GuestCheckoutScreen(portal: portal) { order in
                    showsCheckout = false
                    onRoute(.guest(order))
                }
            }
        }
        .task(id: link) { await load() }
        .refreshable { await load() }
    }

    private func load() async {
        do {
            portal = try await model.api?.portal(workspace: link.workspace, evseId: link.evseId)
            errorMessage = nil
        } catch is CancellationError {
            return
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
