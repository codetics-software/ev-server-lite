//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated struct AppPrincipal: Codable, Sendable, Equatable {
    let id: UUID
    let tenantId: UUID
    let email: String
    let name: String
    let role: UserRole
    let platformAdmin: Bool
    let eulaAccepted: Bool
}
