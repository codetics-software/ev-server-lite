//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct InvoiceRow: View {
    let invoice: InvoiceResponse

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(invoice.number ?? String(localized: "Draft invoice")).font(.headline)
                Spacer()
                Text(Formatters.minor(invoice.amountMinor, currency: invoice.currency)).font(.subheadline.monospacedDigit())
            }
            HStack {
                Text(invoice.createdAt, format: .dateTime.day().month().year())
                Spacer()
                Text(InvoiceStatus.label(invoice.status))
                    .foregroundStyle(invoice.status == "OPEN" || invoice.status == "FAILED" ? .orange : .secondary)
            }
            .font(.caption)
        }
        .contentShape(.rect)
        .accessibilityElement(children: .combine)
    }
}
