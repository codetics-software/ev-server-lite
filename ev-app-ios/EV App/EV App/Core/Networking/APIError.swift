//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated enum APIError: LocalizedError, Sendable, Equatable {
    case unauthorized
    case server(status: Int, message: String?)
    case offline
    case invalidResponse

    var errorDescription: String? {
        switch self {
        case .unauthorized:
            String(localized: "Your session has expired. Sign in again.")
        case .server(_, let message):
            message ?? String(localized: "The server refused this request.")
        case .offline:
            String(localized: "The server cannot be reached. Check your connection and try again.")
        case .invalidResponse:
            String(localized: "The server returned an unexpected response.")
        }
    }

    var status: Int? {
        if case .server(let status, _) = self { return status }
        return nil
    }
}
