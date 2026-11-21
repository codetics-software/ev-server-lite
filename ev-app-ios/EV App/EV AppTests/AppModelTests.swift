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

extension DemoBackedTests {
    @MainActor
    struct AppModelTests {
        @Test func demoSignInLoadsTheDriverContext() async throws {
            let model = await DemoEnvironment.signedIn()
            #expect(model.launch == .ready)
            #expect(model.isDemo)
            #expect(model.principal?.email == DemoData.email)
            #expect(model.workspace?.slug == "demo")
            #expect(model.components?.cars == true)
            #expect(model.brandName == "Réseau Démo")
            #expect(model.server?.pushEnabled == true)
            await model.stations.reload()
            #expect(model.stations.stations.count == 7)
            #expect(model.stations.errorMessage == nil)
            #expect(!model.stations.isLoading)
            #expect(model.stations.station(id: "PAR-04")?.name == "République — Voirie")
            #expect(model.stations.station(id: "NOPE") == nil)
        }

        @Test func usableBadgesSkipDisabledCardsAndPutTheDefaultFirst() async throws {
            let model = await DemoEnvironment.signedIn()
            await model.charging.refresh()
            #expect(model.charging.tokens.count >= 2)
            let badges = model.charging.usableBadges
            #expect(badges.first?.uid == DemoData.ownCard)
            #expect(badges.allSatisfy { $0.enabled })
            #expect(!badges.contains { $0.uid == "04D5E6F7A8B990" })
        }

        @Test func portalLinksOpenTheChargeTab() async throws {
            let model = await DemoEnvironment.signedIn()
            model.handle(url: URL(string: "https://evil.example/charge/demo/\(DemoData.evseId)")!)
            #expect(model.pendingPortalLink == nil)
            #expect(model.selectedTab == .map)
            model.handle(url: URL(string: DemoData.portalLink)!)
            #expect(model.pendingPortalLink == PortalLink(workspace: "demo", evseId: UUID(uuidString: DemoData.evseId)!))
            #expect(model.selectedTab == .charge)
            model.pendingPortalLink = nil
        }

        @Test func notificationsOpenTheirSession() async throws {
            let model = await DemoEnvironment.signedIn()
            model.openNotification(transactionId: 2140)
            #expect(model.openedSessionId == 2140)
            #expect(model.selectedTab == .charge)
            model.openedSessionId = nil
            model.selectedTab = .map
            model.openNotification(transactionId: nil)
            #expect(model.openedSessionId == nil)
            #expect(model.selectedTab == .charge)
        }

        @Test func wrongPasswordKeepsTheDriverSignedOut() async throws {
            let model = await DemoEnvironment.guest()
            #expect(!model.isSignedIn)
            await #expect(throws: APIError.self) {
                try await model.signIn(workspace: " demo ", email: DemoData.email, password: "nope")
            }
            #expect(!model.isSignedIn)
            try await model.signIn(workspace: " demo ", email: " \(DemoData.email) ", password: DemoData.password)
            #expect(model.isSignedIn)
            #expect(model.workspace?.slug == "demo")
        }

        @Test func signingOutForgetsTheSession() async throws {
            let model = await DemoEnvironment.signedIn()
            model.selectedTab = .badges
            await model.signOut()
            #expect(!model.isSignedIn)
            #expect(model.workspace == nil)
            #expect(model.selectedTab == .map)
            #expect(model.charging.tokens.isEmpty)
            #expect(model.charging.active.isEmpty)
            #expect(model.api?.client.bearerToken == nil)
            await model.stations.reload()
            #expect(model.stations.stations.count == 7)
            model.openNotification(transactionId: 1)
            #expect(model.openedSessionId == nil)
            #expect(model.selectedTab == .map)
            await model.charging.refresh()
            #expect(model.charging.tokens.isEmpty)
        }

        @Test func signingOutEverywhereAlsoSignsOut() async throws {
            let model = await DemoEnvironment.signedIn()
            await model.signOut(everywhere: true)
            #expect(!model.isSignedIn)
        }

        @Test func expiredSessionsWarnTheDriver() async throws {
            let model = await DemoEnvironment.signedIn()
            model.sessionExpired()
            #expect(!model.isSignedIn)
            #expect(model.alertMessage == APIError.unauthorized.errorDescription)
            model.alertMessage = nil
        }

        @Test func principalCanBeRefreshedAndEulaAccepted() async throws {
            let model = await DemoEnvironment.signedIn()
            await model.refreshPrincipal()
            #expect(model.principal?.eulaAccepted == true)
            try await model.acceptEula()
            #expect(model.isSignedIn)
        }

        @Test func deletingTheAccountSignsOut() async throws {
            let model = await DemoEnvironment.signedIn()
            try await model.deleteAccount()
            #expect(!model.isSignedIn)
        }

        @Test func startingAChargeWaitsForTheStationAndFeedsCarPlay() async throws {
            let model = await DemoEnvironment.signedIn()
            let api = try #require(model.api)
            try await DemoEnvironment.stopLiveSessions(api)
            await model.charging.refresh()
            let idle = CarPlayContent.session(model)
            #expect(!idle.isActive)
            #expect(idle.items.first?.title == String(localized: "No charging in progress"))

            let session = try await model.charging.start(station: "PAR-06", connector: 1, badge: DemoData.ownCard)
            #expect(session.stationId == "PAR-06")
            #expect(session.connectorId == 1)
            #expect(model.charging.current?.id == session.id)

            let live = CarPlayContent.session(model)
            #expect(live.isActive)
            #expect(live.items.map(\.title) == ["Station", "Power", "Energy", "Duration", "Battery", "Estimated price"].map { String(localized: String.LocalizationValue($0)) })
            #expect(live.items.first?.detail == "Nation — Rapide")

            _ = try await api.stopSession(id: session.id)
            await model.charging.refresh()
            #expect(model.charging.current == nil)
        }

        @Test func pushTokensAreRegisteredForSignedInDrivers() async throws {
            let model = await DemoEnvironment.signedIn()
            await model.push.deviceRegistered(token: "a1b2c3")
            #expect(model.push.deviceToken == "a1b2c3")
            #expect(model.push.lastError == nil)
            model.push.registrationFailed(APIError.offline)
            #expect(model.push.lastError == APIError.offline.errorDescription)
            await model.push.unregister()
            await model.push.requestAuthorization()
            #expect(model.isSignedIn)
        }

        @Test func guestOrdersAreKeptNewestFirstAndPrunedWhenUnknown() async throws {
            let model = await DemoEnvironment.guest()
            let store = model.guestOrders
            let api = try #require(model.api)
            let evse = try #require(UUID(uuidString: DemoData.evseId))
            let checkout = try await api.checkout(workspace: "demo", evseId: evse, request: CheckoutRequest(requestId: UUID(), accessToken: "live", creditMinor: 1_000))
            let live = GuestOrder(id: checkout.orderId, accessToken: "live", evseName: "Gare de Lyon", createdAt: .now)
            let ghost = GuestOrder(id: UUID(), accessToken: "ghost", evseName: "Ailleurs", createdAt: .now)
            store.add(ghost)
            store.add(live)
            store.add(live)
            #expect(store.orders.prefix(2).map(\.id) == [live.id, ghost.id])
            #expect(store.orders.filter { $0.id == live.id }.count == 1)
            await store.refresh()
            #expect(store.orders.contains { $0.id == live.id })
            #expect(!store.orders.contains { $0.id == ghost.id })
            store.remove(live.id)
            #expect(!store.orders.contains { $0.id == live.id })
        }

        @Test func finishedGuestOrdersOlderThanADayAreDropped() async throws {
            let model = await DemoEnvironment.guest()
            let store = model.guestOrders
            let api = try #require(model.api)
            let evse = try #require(UUID(uuidString: DemoData.evseId))
            let checkout = try await api.checkout(workspace: "demo", evseId: evse, request: CheckoutRequest(requestId: UUID(), accessToken: "old", creditMinor: 1_000))
            _ = try await api.stopGuestSession(id: checkout.orderId, accessToken: "old")
            let old = GuestOrder(id: checkout.orderId, accessToken: "old", evseName: "Gare", createdAt: .now.addingTimeInterval(-2 * 86_400))
            store.add(old)
            await store.refresh()
            #expect(!store.orders.contains { $0.id == old.id })
        }

        @Test func placeholderConfigurationIsReportedAtLaunch() async throws {
            let model = AppModel.shared
            await model.retryConnection()
            guard case .misconfigured(let issues) = model.launch else {
                Issue.record("expected misconfigured, got \(model.launch)")
                return
            }
            #expect(issues.contains(.placeholder(key: "API_URL")))
            await DemoEnvironment.signedIn()
            #expect(model.launch == .ready)
        }
    }
}
