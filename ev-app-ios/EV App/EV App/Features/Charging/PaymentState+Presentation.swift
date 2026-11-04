//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

extension PaymentState {
    var label: LocalizedStringResource {
        switch self {
        case .pendingPayment: "Waiting for payment"
        case .paid: "Credit paid"
        case .startRequested: "Starting"
        case .charging: "Charging"
        case .stopRequested: "Stopping"
        case .settling: "Calculating the balance"
        case .refundPending: "Refund in progress"
        case .completed: "Finished"
        case .cancelled: "Cancelled"
        case .failed: "Needs attention"
        case .disputed: "Payment disputed"
        }
    }

    var explanation: LocalizedStringResource? {
        switch self {
        case .pendingPayment:
            "If you closed the payment page, this request expires on its own and you are not charged."
        case .startRequested:
            "Payment confirmed. Waiting for the station to start. If it does not start within five minutes, your credit is refunded."
        case .stopRequested:
            "The stop request has been sent. If the station does not respond, stop charging from your vehicle or at the station."
        case .failed:
            "The operator has been told and will take care of it."
        default:
            nil
        }
    }
}
