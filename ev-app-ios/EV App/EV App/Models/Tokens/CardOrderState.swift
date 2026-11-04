//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated enum CardOrderState: String, Decodable, Sendable {
    case pendingPayment = "PENDING_PAYMENT"
    case paid = "PAID"
    case shipped = "SHIPPED"
    case activated = "ACTIVATED"
    case cancelled = "CANCELLED"
}
