//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI
import Testing
@testable import EV_App

extension DemoBackedTests {
    @MainActor
    struct AuthAndLaunchViewTests {
        @Test func signInRejectsWrongPasswordThenSignsIn() async throws {
            let model = await DemoEnvironment.guest()
            let host = ViewHost(SignInEntryScreen())
            defer { host.close() }
            #expect(await host.waitFor("Réseau Démo"))
            #expect(host.contains("Create an account"))
            #expect(host.contains("Forgot password?"))
            #expect(host.textField("Workspace") == nil)
            #expect(host.type(DemoData.email, into: "Email"))
            #expect(host.type("wrong-password", into: "Password"))
            await host.settle()
            #expect(host.activate(label: "Sign in"))
            #expect(await host.waitFor("Identifiants incorrects."))
            #expect(!model.isSignedIn)
            #expect(host.type(DemoData.password, into: "Password"))
            await host.settle()
            #expect(host.activate(label: "Sign in"))
            #expect(await host.waitUntil { model.isSignedIn })
        }

        @Test func signUpAndPasswordResetForms() async throws {
            await DemoEnvironment.guest()
            let signUp = ViewHost(SignUpScreen())
            #expect(await signUp.waitFor("Use between 16 and 128 characters"))
            #expect(signUp.contains("Read the privacy policy"))
            #expect(signUp.textField("Full name") != nil)
            signUp.close()
            let reset = ViewHost(PasswordResetScreen(workspace: "demo", email: DemoData.email))
            defer { reset.close() }
            #expect(await reset.waitFor("Forgot password"))
            #expect(reset.activate(label: "Send"))
            #expect(await reset.waitFor("If this account exists, a link to choose a new password has been sent."))
        }

        @Test func eulaShowsTheTermsAndAcceptsThem() async throws {
            let model = await DemoEnvironment.signedIn()
            let host = ViewHost(EulaScreen())
            defer { host.close() }
            #expect(await host.waitFor("Supervision de recharge Lite — Kevin Vavelin, Codetics"))
            #expect(host.contains("Version, 1.0"))
            #expect(host.activate(label: "Accept and continue"))
            await host.settle()
            #expect(model.isSignedIn)
            #expect(model.principal?.eulaAccepted == true)
        }

        @Test func launchStatesExplainThemselves() async throws {
            await DemoEnvironment.signedIn()
            let launch = ViewHost(LaunchView())
            await launch.settle()
            launch.close()
            let unavailable = ViewHost(ServerUnavailableView(message: "Serveur injoignable."))
            #expect(await unavailable.waitFor("Server unavailable"))
            #expect(unavailable.contains("Serveur injoignable."))
            #expect(unavailable.contains("Try again"))
            unavailable.close()
            let issues: [ConfigurationIssue] = [.placeholder(key: "API_URL"), .insecureURL(key: "PORTAL_URL")]
            let misconfigured = ViewHost(ConfigurationIssuesView(issues: issues))
            defer { misconfigured.close() }
            #expect(await misconfigured.waitFor(issues[0].message))
            #expect(misconfigured.contains(issues[1].message))
            #expect(misconfigured.contains("Continue with the demo account"))
        }

        @Test func configurationScreenStartsTheDemoAsGuest() async throws {
            let model = await DemoEnvironment.signedIn()
            let host = ViewHost(ConfigurationIssuesView(issues: [.missingFile]))
            defer { host.close() }
            #expect(await host.waitFor("Continue as a demo guest"))
            #expect(host.activate(label: "Continue as a demo guest"))
            #expect(await host.waitUntil { !model.isSignedIn && model.launch == .ready })
        }

        @Test func rootAndTabsFollowTheAccount() async throws {
            let model = await DemoEnvironment.signedIn()
            let host = ViewHost(RootView())
            defer { host.close() }
            #expect(await host.waitFor("Sessions"))
            #expect(host.contains("Badges"))
            #expect(host.contains("Account"))
            model.selectedTab = .account
            #expect(await host.waitFor("My vehicles"))
            model.selectedTab = .map
            await model.signOut()
            #expect(await host.waitUntil { !host.contains("Badges") })
            model.selectedTab = .account
            #expect(await host.waitFor("Forgot password?"))
            model.selectedTab = .map
        }
    }
}
