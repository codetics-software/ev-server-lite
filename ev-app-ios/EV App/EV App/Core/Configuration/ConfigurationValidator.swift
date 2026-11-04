//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Foundation

nonisolated enum ConfigurationValidator {
    enum Outcome: Sendable, Equatable {
        case valid(AppConfiguration)
        case invalid([ConfigurationIssue])
    }

    private static let urlKeys = ["API_URL", "PORTAL_URL", "PRIVACY_POLICY_URL"]
    private static let textKeys = ["WORKSPACE_SLUG", "BRAND_NAME", "SUPPORT_EMAIL"]
    private static let slug = /[a-z0-9][a-z0-9-]{0,62}/
    private static let email = /[^\s@]+@[^\s@]+\.[^\s@]+/

    static func loadBundled(bundle: Bundle = .main, environment: [String: String] = ProcessInfo.processInfo.environment) -> Outcome {
        guard
            let url = bundle.url(forResource: "AppConfiguration", withExtension: "plist"),
            let data = try? Data(contentsOf: url),
            var values = (try? PropertyListSerialization.propertyList(from: data, format: nil)) as? [String: Any]
        else {
            return .invalid([.missingFile])
        }
        #if DEBUG
        for key in urlKeys + textKeys {
            if let override = environment["EV_\(key)"] { values[key] = override }
        }
        return validate(values, allowInsecureLocalhost: true)
        #else
        return validate(values, allowInsecureLocalhost: false)
        #endif
    }

    static func validate(_ values: [String: Any], allowInsecureLocalhost: Bool) -> Outcome {
        var issues: [ConfigurationIssue] = []
        var urls: [String: URL] = [:]
        var texts: [String: String] = [:]

        for key in urlKeys + textKeys {
            let value = (values[key] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            if value.isEmpty {
                issues.append(.invalidValue(key: key))
            } else if isPlaceholder(value) {
                issues.append(.placeholder(key: key))
            } else if urlKeys.contains(key) {
                switch parse(url: value, allowInsecureLocalhost: allowInsecureLocalhost) {
                case .success(let url): urls[key] = url
                case .failure(let problem): issues.append(problem.issue(key: key))
                }
            } else {
                texts[key] = value
            }
        }
        if let value = texts["WORKSPACE_SLUG"], value.wholeMatch(of: slug) == nil {
            issues.append(.invalidValue(key: "WORKSPACE_SLUG"))
        }
        if let value = texts["SUPPORT_EMAIL"], value.wholeMatch(of: email) == nil {
            issues.append(.invalidValue(key: "SUPPORT_EMAIL"))
        }
        guard
            issues.isEmpty,
            let api = urls["API_URL"],
            let portal = urls["PORTAL_URL"],
            let privacy = urls["PRIVACY_POLICY_URL"],
            let workspace = texts["WORKSPACE_SLUG"],
            let brand = texts["BRAND_NAME"],
            let support = texts["SUPPORT_EMAIL"]
        else {
            return .invalid(issues)
        }
        return .valid(
            AppConfiguration(
                apiURL: api,
                portalURL: portal,
                workspaceSlug: workspace,
                brandName: brand,
                supportEmail: support,
                privacyPolicyURL: privacy
            )
        )
    }

    private enum URLProblem: Error {
        case invalid
        case insecure

        func issue(key: String) -> ConfigurationIssue {
            switch self {
            case .invalid: .invalidValue(key: key)
            case .insecure: .insecureURL(key: key)
            }
        }
    }

    private static func parse(url value: String, allowInsecureLocalhost: Bool) -> Result<URL, URLProblem> {
        guard let url = URL(string: value), let scheme = url.scheme?.lowercased(), let host = url.host(), !host.isEmpty else {
            return .failure(.invalid)
        }
        if ["example.com", "example.org", "example.net", "exemple.fr"].contains(where: { host == $0 || host.hasSuffix(".\($0)") }) {
            return .failure(.invalid)
        }
        if scheme == "https" { return .success(url) }
        if scheme == "http", allowInsecureLocalhost, ["localhost", "127.0.0.1"].contains(host) { return .success(url) }
        return .failure(.insecure)
    }

    private static func isPlaceholder(_ value: String) -> Bool {
        value.contains("__") || value.contains("<") || value.uppercased().contains("PLACEHOLDER") || value.uppercased().contains("CHANGE_ME")
    }
}
