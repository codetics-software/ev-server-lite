//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation
import Testing
@testable import EV_App

struct AppLanguageTests {
    @Test func keepsSupportedLanguages() {
        #expect(AppLanguage.resolve(["es"]) == "es")
        #expect(AppLanguage.resolve(["fr-CA", "en"]) == "fr")
        #expect(AppLanguage.resolve(["Base", "en"]) == "en")
    }

    @Test func fallsBackToEnglish() {
        #expect(AppLanguage.resolve(["de", "it"]) == "en")
        #expect(AppLanguage.resolve([]) == "en")
    }
}
