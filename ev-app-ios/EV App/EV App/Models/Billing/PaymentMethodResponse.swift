//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated struct PaymentMethodResponse: Decodable, Sendable, Identifiable {
    let id: String
    let brand: String?
    let last4: String?
    let expMonth: Int?
    let expYear: Int?
    let `default`: Bool
    let createdAt: Date
}
