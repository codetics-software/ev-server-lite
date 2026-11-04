//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct GuestSessionScreen: View {
    @Environment(AppModel.self) private var model
    let order: GuestOrder

    @State private var payment: PortalPaymentResponse?
    @State private var errorMessage: String?
    @State private var isStopping = false
    @State private var confirmsStop = false

    var body: some View {
        List {
            if let payment {
                Section {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(order.evseName).font(.subheadline).foregroundStyle(.secondary)
                        Text(payment.state.label).font(.title3.bold())
                        if let explanation = payment.state.explanation {
                            Text(explanation).font(.footnote).foregroundStyle(.secondary)
                        }
                    }
                    .accessibilityElement(children: .combine)
                }
                Section {
                    LabeledContent("Credit paid", value: Formatters.minor(payment.prepaidMinor, currency: payment.currency))
                    LabeledContent("Charging amount", value: Formatters.minor(payment.billedMinor, currency: payment.currency))
                    LabeledContent("Refunded", value: Formatters.minor(payment.refundedMinor, currency: payment.currency))
                }
                if let message = payment.message {
                    Section("Operator information") { Text(message) }
                }
                if payment.state == .charging {
                    Section {
                        Button("Stop charging", systemImage: "stop.circle.fill", role: .destructive) { confirmsStop = true }
                            .disabled(isStopping)
                            .accessibilityIdentifier("guest.stop")
                            .confirmationDialog("Stop this charging session?", isPresented: $confirmsStop, titleVisibility: .visible) {
                                Button("Stop charging", role: .destructive) { Task { await stop() } }
                            }
                    }
                }
                if payment.refundedMinor > 0 {
                    Section {
                        Text("The refund has been sent to Stripe. How quickly it appears on your account depends on your bank.")
                            .font(.footnote)
                    }
                }
                Section {
                    Text("Reference: \(payment.id.uuidString.lowercased())")
                        .font(.caption2)
                        .textSelection(.enabled)
                    if payment.state.isFinished {
                        Button("Remove from this device", role: .destructive) {
                            model.guestOrders.remove(order.id)
                        }
                    }
                }
            } else if errorMessage == nil {
                ProgressView()
            }
            if let errorMessage {
                ErrorBanner(message: errorMessage)
            }
        }
        .navigationTitle("Your session")
        .navigationBarTitleDisplayMode(.inline)
        .task(id: order.id) { await poll() }
    }

    private func poll() async {
        while !Task.isCancelled {
            do {
                let latest = try await model.api?.guestSession(id: order.id, accessToken: order.accessToken)
                withAnimation { payment = latest }
                errorMessage = nil
                if latest?.state.isFinished == true { return }
            } catch is CancellationError {
                return
            } catch {
                errorMessage = error.localizedDescription
            }
            try? await Task.sleep(for: .seconds(5))
        }
    }

    private func stop() async {
        isStopping = true
        defer { isStopping = false }
        do {
            payment = try await model.api?.stopGuestSession(id: order.id, accessToken: order.accessToken)
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
