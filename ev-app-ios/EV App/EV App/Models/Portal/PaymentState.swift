//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated enum PaymentState: String, Codable, Sendable {
    case pendingPayment = "PENDING_PAYMENT"
    case paid = "PAID"
    case startRequested = "START_REQUESTED"
    case charging = "CHARGING"
    case stopRequested = "STOP_REQUESTED"
    case settling = "SETTLING"
    case refundPending = "REFUND_PENDING"
    case completed = "COMPLETED"
    case cancelled = "CANCELLED"
    case failed = "FAILED"
    case disputed = "DISPUTED"

    var isFinished: Bool {
        [.completed, .cancelled, .failed, .disputed].contains(self)
    }
}
