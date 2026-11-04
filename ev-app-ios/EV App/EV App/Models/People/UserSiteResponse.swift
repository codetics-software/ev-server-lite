//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated struct UserSiteResponse: Decodable, Sendable, Identifiable {
    let siteId: UUID
    let name: String
    let siteAdmin: Bool
    let siteOwner: Bool

    var id: UUID { siteId }
}
