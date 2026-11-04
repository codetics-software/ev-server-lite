//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

enum ChargeStartError: LocalizedError {
    case notStarted

    var errorDescription: String? {
        String(localized: "The station has not confirmed the start. Check that your vehicle is plugged in and that the station accepted your badge, then try again.")
    }
}
