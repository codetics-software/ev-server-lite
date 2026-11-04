//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated struct ErrorReportInput: Encodable, Sendable {
    let subject: String
    let description: String
    let stationId: String?
    let connectorId: Int?
    let phone: String?
}
