//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation
import Testing
@testable import EV_App

struct ConfigurationValidatorTests {
    private let valid: [String: Any] = [
        "API_URL": "https://api.reseau.fr",
        "PORTAL_URL": "https://reseau.fr",
        "WORKSPACE_SLUG": "reseau",
        "BRAND_NAME": "Réseau",
        "SUPPORT_EMAIL": "support@reseau.fr",
        "PRIVACY_POLICY_URL": "https://reseau.fr/privacy",
    ]

    private func issues(_ changes: [String: Any?], local: Bool = false) -> [ConfigurationIssue] {
        var values = valid
        changes.forEach { values[$0.key] = $0.value }
        if case .invalid(let issues) = ConfigurationValidator.validate(values, allowInsecureLocalhost: local) { return issues }
        return []
    }

    @Test func acceptsCompleteConfiguration() {
        guard case .valid(let configuration) = ConfigurationValidator.validate(valid, allowInsecureLocalhost: false) else {
            Issue.record("expected valid")
            return
        }
        #expect(configuration.workspaceSlug == "reseau")
        #expect(configuration.apiURL.host() == "api.reseau.fr")
    }

    @Test func rejectsEveryPlaceholder() {
        for key in valid.keys {
            #expect(issues([key: "__\(key)__"]) == [.placeholder(key: key)])
        }
    }

    @Test func bundledFileIsValidOnlyWithoutPlaceholders() throws {
        let url = try #require(Bundle.main.url(forResource: "AppConfiguration", withExtension: "plist"))
        let values = try #require(PropertyListSerialization.propertyList(from: Data(contentsOf: url), format: nil) as? [String: String])
        let hasPlaceholder = values.values.contains { $0.contains("__") }
        switch ConfigurationValidator.loadBundled(bundle: .main, environment: [:]) {
        case .valid:
            #expect(!hasPlaceholder)
        case .invalid(let found):
            #expect(hasPlaceholder)
            #expect(!found.isEmpty)
        }
    }

    @Test func rejectsMissingAndInvalidValues() {
        #expect(issues(["API_URL": nil]) == [.invalidValue(key: "API_URL")])
        #expect(issues(["WORKSPACE_SLUG": "Mon Espace"]) == [.invalidValue(key: "WORKSPACE_SLUG")])
        #expect(issues(["SUPPORT_EMAIL": "nobody"]) == [.invalidValue(key: "SUPPORT_EMAIL")])
        #expect(issues(["API_URL": "https://supervision.exemple.fr"]) == [.invalidValue(key: "API_URL")])
        #expect(issues(["PORTAL_URL": "https://example.com"]) == [.invalidValue(key: "PORTAL_URL")])
    }

    @Test func requiresHTTPS() {
        #expect(issues(["API_URL": "http://api.reseau.fr"]) == [.insecureURL(key: "API_URL")])
        #expect(issues(["API_URL": "http://localhost:8080"]) == [.insecureURL(key: "API_URL")])
        #expect(issues(["API_URL": "http://localhost:8080"], local: true).isEmpty)
        #expect(issues(["API_URL": "http://api.reseau.fr"], local: true) == [.insecureURL(key: "API_URL")])
    }
}

final class TestBundleMarker {}
