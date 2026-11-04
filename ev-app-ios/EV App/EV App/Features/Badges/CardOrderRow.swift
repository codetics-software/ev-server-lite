//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct CardOrderRow: View {
    let order: CardOrderResponse

    var body: some View {
        LabeledContent {
            Text(order.refunded ? "Refunded" : order.state.label)
                .foregroundStyle(order.state.color)
        } label: {
            Text(order.createdAt, format: .dateTime.day().month().year())
        }
        .font(.subheadline)
        .accessibilityIdentifier("cards.order.\(order.state.rawValue)")
    }
}
