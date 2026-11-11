//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

enum ConnectorType {
    static func label(for type: String?) -> String {
        switch type {
        case "T2", "Type2", "TYPE2", "IEC_62196_T2": "Type 2"
        case "CCS", "CCS2", "IEC_62196_T2_COMBO": "CCS"
        case "CHADEMO", "CHAdeMO": "CHAdeMO"
        case "T3", "IEC_62196_T3": "Type 3"
        case "DOMESTIC", "Domestic", "E/F": String(localized: "Domestic")
        case let value?: value
        case nil: String(localized: "Connector")
        }
    }
}
