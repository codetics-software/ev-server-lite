//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated struct PortalLink: Hashable, Sendable, Identifiable {
    let workspace: String
    let evseId: UUID

    var id: UUID { evseId }
}
