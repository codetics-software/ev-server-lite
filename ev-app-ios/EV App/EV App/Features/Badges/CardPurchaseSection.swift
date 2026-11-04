//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct CardPurchaseSection: View {
    @Environment(AppModel.self) private var model
    let offer: CardOfferResponse?
    let orders: [CardOrderResponse]
    let onActivate: () -> Void

    @State private var purchase: IdentifiableURL?

    private var awaitsActivation: Bool {
        orders.contains { $0.state == .shipped }
    }

    private var purchaseURL: URL? {
        guard offer?.available == true, let portal = model.configuration?.portalURL else { return nil }
        return portal.appending(path: "card-orders")
    }

    var body: some View {
        Section {
            if awaitsActivation {
                Label("Your card is ready. Activate it when you receive it.", systemImage: "checkmark.seal")
                    .font(.subheadline)
            }
            Button(action: onActivate) {
                ChargeActionLabel(
                    title: "Activate a card",
                    detail: "Scan your new card, or enter the number printed on it or given by your operator.",
                    systemImage: "creditcard.and.123"
                )
            }
            .accessibilityIdentifier("cards.activate")
            if let purchaseURL {
                Button {
                    purchase = IdentifiableURL(url: purchaseURL)
                } label: {
                    ChargeActionLabel(
                        title: "Order a card",
                        detail: orderDetail,
                        systemImage: "cart"
                    )
                }
                .accessibilityIdentifier("cards.order")
            }
            ForEach(orders) { order in
                CardOrderRow(order: order)
            }
        } header: {
            Text("Charging card")
        }
        .sheet(item: $purchase) { item in
            SafariView(url: item.url).ignoresSafeArea()
        }
    }

    private var orderDetail: LocalizedStringResource {
        if let offer, let price = offer.priceMinor {
            return "On your online account, for \(Formatters.minor(price, currency: offer.currency))."
        }
        return "On your online account."
    }
}
