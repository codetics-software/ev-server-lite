//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct GuestCheckoutScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let portal: PortalEvseResponse
    let onOrder: (GuestOrder) -> Void

    @State private var creditEuros = 20.0
    @State private var attempt: CheckoutRequest?
    @State private var checkoutURL: IdentifiableURL?
    @State private var pendingOrder: GuestOrder?
    @State private var isWorking = false
    @State private var errorMessage: String?

    private var bounds: ClosedRange<Double> {
        Double(portal.minimumCreditMinor) / 100...Double(portal.maximumCreditMinor) / 100
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Charging point") {
                    Text(portal.name)
                    PortalTariffRows(tariff: portal.tariff)
                }
                Section {
                    LabeledContent("Credit", value: Formatters.minor(Int(creditEuros * 100), currency: portal.tariff.currency))
                    Slider(value: $creditEuros, in: bounds, step: 5) {
                        Text("Credit")
                    }
                    .accessibilityIdentifier("checkout.credit")
                } footer: {
                    Text("Charging stops when the credit runs out. The balance is refunded automatically, and in full if charging does not start within five minutes.")
                }
                Section {
                    Label("Plug in your vehicle before you pay.", systemImage: "powerplug.portrait")
                    Label("Payment is handled by Stripe. Your card details are not shared with this app.", systemImage: "lock.shield")
                }
                if let errorMessage {
                    Section { ErrorBanner(message: errorMessage) }
                }
                Section {
                    Button {
                        Task { await pay() }
                    } label: {
                        Text("Pay \(Formatters.minor(Int(creditEuros * 100), currency: portal.tariff.currency))")
                            .bold()
                            .frame(maxWidth: .infinity)
                    }
                    .disabled(isWorking)
                    .accessibilityIdentifier("checkout.pay")
                }
            }
            .navigationTitle("Pay and charge")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
            .sheet(item: $checkoutURL, onDismiss: finish) { item in
                SafariView(url: item.url).ignoresSafeArea()
            }
            .task(id: pendingOrder?.id) { await watchPayment() }
        }
    }

    private func pay() async {
        isWorking = true
        errorMessage = nil
        defer { isWorking = false }
        let minor = Int(creditEuros * 100)
        let request = attempt?.creditMinor == minor
            ? attempt!
            : CheckoutRequest(requestId: UUID(), accessToken: GuestAccessToken.generate(), creditMinor: minor)
        attempt = request
        do {
            guard let api = model.api else { return }
            let response = try await api.checkout(workspace: portal.workspaceSlug, evseId: portal.id, request: request)
            let order = GuestOrder(id: response.orderId, accessToken: request.accessToken, evseName: portal.name, createdAt: .now)
            model.guestOrders.add(order)
            pendingOrder = order
            #if DEBUG
            if model.isDemo {
                onOrder(order)
                return
            }
            #endif
            guard let url = URL(string: response.checkoutUrl), url.scheme == "https" else {
                throw APIError.invalidResponse
            }
            checkoutURL = IdentifiableURL(url: url)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func watchPayment() async {
        guard let order = pendingOrder, let api = model.api else { return }
        while !Task.isCancelled {
            try? await Task.sleep(for: .seconds(3))
            if let payment = try? await api.guestSession(id: order.id, accessToken: order.accessToken), payment.state != .pendingPayment {
                checkoutURL = nil
                return
            }
        }
    }

    private func finish() {
        if let pendingOrder { onOrder(pendingOrder) }
    }
}
