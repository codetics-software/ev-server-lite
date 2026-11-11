//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated enum AppLanguage {
    static let supported = ["en", "fr", "es"]

    static var current: String {
        resolve(Bundle.main.preferredLocalizations)
    }

    static func resolve(_ localizations: [String]) -> String {
        for localization in localizations {
            let code = Locale(identifier: localization).language.languageCode?.identifier ?? localization
            if supported.contains(code) { return code }
        }
        return "en"
    }
}
