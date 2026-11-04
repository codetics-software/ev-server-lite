//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated struct WorkspaceResponse: Decodable, Sendable, Equatable {
    let id: UUID
    let slug: String
    let name: String
    let active: Bool
    let components: WorkspaceComponents
    let logoUrl: String?
}
