//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

extension CardOrderState {
    var label: LocalizedStringResource {
        switch self {
        case .pendingPayment: "Waiting for payment"
        case .paid: "Paid, being prepared"
        case .shipped: "Ready to activate"
        case .activated: "Activated"
        case .cancelled: "Cancelled"
        }
    }

    var color: Color {
        switch self {
        case .pendingPayment, .cancelled: .secondary
        case .paid: .orange
        case .shipped: .blue
        case .activated: .green
        }
    }
}
