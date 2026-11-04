//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated enum ConfigurationIssue: Sendable, Equatable, Identifiable {
    case missingFile
    case placeholder(key: String)
    case invalidValue(key: String)
    case insecureURL(key: String)

    var id: String {
        switch self {
        case .missingFile: "missingFile"
        case .placeholder(let key): "placeholder.\(key)"
        case .invalidValue(let key): "invalid.\(key)"
        case .insecureURL(let key): "insecure.\(key)"
        }
    }

    var message: String {
        switch self {
        case .missingFile:
            String(localized: "AppConfiguration.plist is missing from the application.")
        case .placeholder(let key):
            String(localized: "\(key) still contains a placeholder.")
        case .invalidValue(let key):
            String(localized: "\(key) has an invalid value.")
        case .insecureURL(let key):
            String(localized: "\(key) must use HTTPS.")
        }
    }
}
