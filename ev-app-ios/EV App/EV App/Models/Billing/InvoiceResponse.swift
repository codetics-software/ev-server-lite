//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated struct InvoiceResponse: Decodable, Sendable, Identifiable {
    let id: UUID
    let number: String?
    let userId: UUID
    let userName: String?
    let status: String
    let amountMinor: Int
    let currency: String
    let createdAt: Date
    let sessionIds: [Int]
    let hostedUrl: String?
    let lastError: String?
}
