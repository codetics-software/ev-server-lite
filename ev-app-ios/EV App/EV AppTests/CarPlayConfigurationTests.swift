//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import CarPlay
import Foundation
import Testing
@testable import EV_App

struct CarPlayConfigurationTests {
    @Test func sceneManifestDeclaresTheCarPlayDelegate() throws {
        let manifest = try #require(Bundle.main.object(forInfoDictionaryKey: "UIApplicationSceneManifest") as? [String: Any])
        let configurations = try #require(manifest["UISceneConfigurations"] as? [String: Any])
        let carPlay = try #require(configurations["CPTemplateApplicationSceneSessionRoleApplication"] as? [[String: Any]])
        let name = try #require(carPlay.first?["UISceneDelegateClassName"] as? String)
        let type = try #require(NSClassFromString(name))
        #expect(type is CPTemplateApplicationSceneDelegate.Type)
    }

    @Test func sessionTabExplainsSignedOutState() {
        let content = CarPlayContent.session(AppModel())
        #expect(!content.isActive)
        #expect(content.items.count == 1)
    }

    @Test func secureUsageDescriptionsArePresent() {
        for key in ["NSCameraUsageDescription", "NSLocationWhenInUseUsageDescription", "NFCReaderUsageDescription"] {
            #expect(Bundle.main.object(forInfoDictionaryKey: key) is String)
        }
    }
}
