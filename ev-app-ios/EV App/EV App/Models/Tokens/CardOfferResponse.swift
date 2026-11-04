//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated struct CardOfferResponse: Decodable, Sendable {
    let available: Bool
    let priceMinor: Int?
    let currency: String
    let description: String?
    let shipping: Bool
}
