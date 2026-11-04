//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated struct CustomerEvseLink: Decodable, Sendable, Identifiable, Hashable {
    let id: UUID
    let connectorId: Int
    let workspaceSlug: String
    let name: String
}
