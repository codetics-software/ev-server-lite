//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

struct StartRequest: Identifiable, Hashable {
    let stationId: String
    let stationName: String
    let connectorId: Int

    var id: String { "\(stationId)/\(connectorId)" }
}
