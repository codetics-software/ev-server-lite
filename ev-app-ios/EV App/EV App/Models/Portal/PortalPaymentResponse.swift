//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated struct PortalPaymentResponse: Decodable, Sendable, Identifiable {
    let id: UUID
    let state: PaymentState
    let prepaidMinor: Int
    let billedMinor: Int?
    let refundedMinor: Int
    let currency: String
    let transactionId: Int?
    let message: String?
}
