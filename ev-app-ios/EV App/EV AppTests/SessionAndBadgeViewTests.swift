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
    struct SessionAndBadgeViewTests {
        @Test func historyNamesTheStations() async throws {
            let model = await DemoEnvironment.signedIn()
            await model.stations.reload()
            let host = ViewHost(SessionsScreen())
            defer { host.close() }
            #expect(await host.waitFor("Gare de Lyon — Borne A"))
            #expect(host.contains("Bercy Village — Rapide"))
            #expect(host.contains("History"))
            #expect(host.contains("Statistics"))
        }

        @Test func statisticsShowTheTotalAndStations() async throws {
            await DemoEnvironment.signedIn()
            let host = ViewHost(NavigationStack { StatisticsView() })
            defer { host.close() }
            #expect(await host.waitFor("By station"))
            #expect(host.contains("Bercy Village — Rapide"))
            #expect(host.contains("Indicator"))
            #expect(await host.waitFor("Year"))
        }

        @Test func sessionDetailShowsBillingAndSignedData() async throws {
            let model = await DemoEnvironment.signedIn()
            let session = try #require(try await model.api?.session(id: 2140))
            let host = ViewHost(NavigationStack { SessionDetailScreen(session: session) })
            defer { host.close() }
            #expect(await host.waitFor("Signed meter data"))
            #expect(host.contains("Badge, \(DemoData.ownCard)"))
            #expect(host.contains("Invoiced"))
            #expect(host.contains(DemoData.ownCard))
            #expect(host.contains("Format, OCMF"))
            #expect(host.contains("Report a problem"))
        }

        @Test func badgesListCardsAndSimulateANFCRead() async throws {
            await DemoEnvironment.signedIn()
            let host = ViewHost(BadgesScreen())
            defer { host.close() }
            #expect(await host.waitFor("CARTE-1042"))
            #expect(host.contains("My badges"))
            #expect(host.contains("Activate a card"))
            #expect(await host.waitFor("Order a card"))
            #expect(host.activate(labelPrefix: "Check a card with NFC"))
            #expect(await host.waitFor("Simulate an unknown card"))
            #expect(host.activate(label: "Simulate an unknown card"))
            #expect(await host.waitFor("This card is not registered on your account."))
            #expect(host.contains("04FFEEDDCCBB80"))
        }

        @Test func activationScreenReflectsTheOrderState() async throws {
            let model = await DemoEnvironment.signedIn()
            let api = try #require(model.api)
            let pending = try await api.cardOrders().first?.state == .shipped
            let activated = Box<Bool>()
            let host = ViewHost(ActivateCardScreen { activated.value = true })
            defer { host.close() }
            #expect(await host.waitFor("Scan my card"))
            #expect(host.activate(labelPrefix: "Scan my card"))
            #expect(await host.waitFor("Simulate my ordered card"))
            #expect(host.activate(label: "Simulate my ordered card"))
            if pending {
                #expect(await host.waitFor("Your card is active."))
                #expect(activated.value == true)
            } else {
                #expect(await host.waitFor("Aucune carte commandée sur votre compte ne correspond à ce numéro."))
                #expect(activated.value == nil)
            }
        }

        @Test func badgeDetailShowsThePassPreview() async throws {
            let model = await DemoEnvironment.signedIn()
            await model.charging.refresh()
            let token = try #require(model.charging.tokens.first { $0.uid == DemoData.ownCard })
            let host = ViewHost(NavigationStack { BadgeDetailScreen(token: token) })
            defer { host.close() }
            #expect(await host.waitFor("Badge QR code"))
            #expect(host.contains("Printed number, CARTE-1042"))
            #expect(host.contains("Thomas Bernard"))
            #expect(host.contains("Apple Wallet"))
        }

        @Test func nfcResultsExplainEachOutcome() async throws {
            await DemoEnvironment.signedIn()
            let disabled = TestData.token("AA11", enabled: false)
            let expired = TestData.token("BB22", expires: .distantPast)
            let registered = TestData.token("CC33")
            let host = ViewHost(List {
                NFCResultSection(outcome: .registered(registered))
                NFCResultSection(outcome: .disabled(disabled))
                NFCResultSection(outcome: .expired(expired))
                NFCResultSection(outcome: .notRegistered(uid: "DD44"))
            })
            defer { host.close() }
            #expect(await host.waitFor("This card is registered on your account."))
            #expect(host.contains("This card is on your account but disabled."))
            #expect(host.contains("This card is on your account but has expired."))
            #expect(host.contains("This card is not registered on your account."))
            #expect(host.contains("Ask my operator to add it"))
        }
    }
}
