//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct PaymentMethodsScreen: View {
    @Environment(AppModel.self) private var model
    @State private var methods: [PaymentMethodResponse] = []
    @State private var checkoutURL: IdentifiableURL?
    @State private var isLoading = true
    @State private var errorMessage: String?

    var body: some View {
        List {
            if let errorMessage {
                ErrorBanner(message: errorMessage)
            }
            ForEach(methods) { method in
                HStack {
                    Image(systemName: "creditcard.fill")
                        .foregroundStyle(Color.accentColor)
                        .accessibilityHidden(true)
                    VStack(alignment: .leading) {
                        Text("\((method.brand ?? "").capitalized) •••• \(method.last4 ?? "")").font(.headline)
                        if let month = method.expMonth, let year = method.expYear {
                            Text("Expires \(month)/\(String(year))").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    Spacer()
                    if method.default {
                        Text("Default").font(.caption.bold()).foregroundStyle(Color.accentColor)
                    } else {
                        Button("Set as default") { Task { await setDefault(method) } }
                            .buttonStyle(.borderless)
                            .font(.caption)
                    }
                }
                .swipeActions {
                    Button("Delete", role: .destructive) { Task { await delete(method) } }
                }
            }
            Section {
                Button("Add a payment method", systemImage: "plus.circle") { Task { await add() } }
            } footer: {
                Text("Your card is entered on Stripe's secure page and is never shared with this app.")
            }
        }
        .navigationTitle("Payment methods")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $checkoutURL, onDismiss: { Task { await load() } }) { item in
            SafariView(url: item.url).ignoresSafeArea()
        }
        .task { await load() }
        .refreshable { await load() }
    }

    private func perform(_ action: () async throws -> Void) async {
        do {
            try await action()
            errorMessage = nil
        } catch is CancellationError {
            return
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func load() async {
        await perform { methods = try await model.api?.paymentMethods() ?? [] }
        isLoading = false
    }

    private func add() async {
        await perform {
            guard let response = try await model.api?.paymentMethodCheckout(), let url = URL(string: response.url) else { return }
            checkoutURL = IdentifiableURL(url: url)
        }
    }

    private func setDefault(_ method: PaymentMethodResponse) async {
        await perform { try await model.api?.setDefaultPaymentMethod(id: method.id) }
        await load()
    }

    private func delete(_ method: PaymentMethodResponse) async {
        await perform { try await model.api?.deletePaymentMethod(id: method.id) }
        await load()
    }
}
