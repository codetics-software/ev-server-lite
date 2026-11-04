//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated struct CardOrderResponse: Decodable, Sendable, Identifiable {
    let id: UUID
    let state: CardOrderState
    let amountMinor: Int
    let currency: String
    let createdAt: Date
    let paidAt: Date?
    let shippedAt: Date?
    let activatedAt: Date?
    let refunded: Bool
}
