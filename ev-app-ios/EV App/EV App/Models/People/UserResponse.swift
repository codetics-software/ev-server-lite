//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated struct UserResponse: Decodable, Sendable, Identifiable {
    let id: UUID
    let email: String
    let displayName: String
    let role: UserRole
    let enabled: Bool
    let emailVerified: Bool
    let phone: String?
    let locale: String
    let createdAt: Date?
    let lastLoginAt: Date?
    let billingCustomer: Bool
    let defaultCarId: UUID?
    let siteCount: Int
    let eulaAccepted: Bool
}
