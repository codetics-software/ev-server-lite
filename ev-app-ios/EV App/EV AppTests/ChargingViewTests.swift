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
    struct ChargingViewTests {
        private var demoLink: PortalLink { PortalLink(workspace: "demo", evseId: UUID(uuidString: DemoData.evseId)!) }

        @Test func startChargeOffersOnlyUsableBadgesAndStartsTheSession() async throws {
            let model = await DemoEnvironment.signedIn()
            let api = try #require(model.api)
            try await DemoEnvironment.stopLiveSessions(api)
            let started = Box<ChargingSessionResponse>()
            let host = ViewHost(StartChargeScreen(request: StartRequest(stationId: "PAR-06", stationName: "Nation — Rapide", connectorId: 1)) { started.value = $0 })
            defer { host.close() }
            #expect(await host.waitFor("CARTE-1042"))
            #expect(host.contains("Nation — Rapide"))
            #expect(!host.contains("CARTE-0877"))
            await host.settle(.seconds(1))
            #expect(host.activate(label: "Start"))
            #expect(await host.waitUntil(timeout: .seconds(15)) { started.value != nil })
            #expect(started.value?.stationId == "PAR-06")
            #expect(started.value?.tokenUid == DemoData.ownCard)
            try await DemoEnvironment.stopLiveSessions(api)
            await model.charging.refresh()
        }

        @Test func liveSessionShowsProgressAndFinishedSessionsTheirReason() async throws {
            let model = await DemoEnvironment.signedIn()
            let api = try #require(model.api)
            try await DemoEnvironment.stopLiveSessions(api)
            _ = try await api.startSession(SessionStartInput(stationId: "PAR-07", connectorId: 1, tokenUid: DemoData.ownCard))
            let active = try #require(try await api.sessions(status: "active").items.first)
            let live = ViewHost(NavigationStack { LiveSessionScreen(sessionId: active.id) })
            #expect(await live.waitFor("Stop charging"))
            #expect(live.contains("Montparnasse"))
            #expect(live.contains("Estimated price"))
            _ = try await api.stopSession(id: active.id)
            #expect(await live.waitFor("Session finished", timeout: .seconds(12)))
            #expect(live.contains(String(localized: "Stopped remotely")))
            live.close()

            let finished = ViewHost(NavigationStack { LiveSessionScreen(sessionId: 2140) })
            defer { finished.close() }
            #expect(await finished.waitFor(String(localized: "Vehicle unplugged")))
            #expect(finished.element(label: "Stop charging") == nil)
        }

        @Test func chargeHomeListsActiveSessionsAndOpensPendingLinks() async throws {
            let model = await DemoEnvironment.signedIn()
            let api = try #require(model.api)
            try await DemoEnvironment.stopLiveSessions(api)
            _ = try await api.startSession(SessionStartInput(stationId: "PAR-03", connectorId: 2, tokenUid: DemoData.ownCard))
            await model.charging.refresh()
            let host = ViewHost(ChargeScreen())
            defer { host.close() }
            #expect(await host.waitFor("In progress"))
            #expect(host.contains("Bastille — Parking"))
            #expect(host.contains("Scan a QR code"))
            #expect(host.contains("Enter a charging point ID"))
            model.pendingPortalLink = demoLink
            #expect(await host.waitFor("Start with my badge"))
            #expect(model.pendingPortalLink == nil)
            try await DemoEnvironment.stopLiveSessions(api)
            await model.charging.refresh()
        }

        @Test func chargeHomeOpensNotifiedSessions() async throws {
            let model = await DemoEnvironment.signedIn()
            let host = ViewHost(ChargeScreen())
            defer { host.close() }
            #expect(await host.waitFor("Find a station"))
            model.openedSessionId = 2139
            #expect(await host.waitFor(String(localized: "Vehicle unplugged")))
            #expect(model.openedSessionId == nil)
        }

        @Test func chargeHomeSendsGuestsToTheMap() async throws {
            let model = await DemoEnvironment.guest()
            model.selectedTab = .charge
            let host = ViewHost(ChargeScreen())
            defer { host.close() }
            #expect(await host.waitFor("No account needed"))
            #expect(host.activate(labelPrefix: "Find a station"))
            #expect(await host.waitUntil { model.selectedTab == .map })
        }

        @Test func chargingPointForGuestsOffersCardPayment() async throws {
            await DemoEnvironment.guest()
            let host = ViewHost(NavigationStack { ChargingPointScreen(link: demoLink) { _ in } })
            defer { host.close() }
            #expect(await host.waitFor("Pay by card without an account"))
            #expect(host.contains("Réseau Démo"))
            #expect(host.contains("You pay a credit in advance"))
            #expect(!host.contains("Start with my badge"))
        }

        @Test func guestCheckoutRecordsTheOrder() async throws {
            let model = await DemoEnvironment.guest()
            let api = try #require(model.api)
            let portal = try await api.portal(workspace: "demo", evseId: demoLink.evseId)
            let ordered = Box<GuestOrder>()
            let host = ViewHost(GuestCheckoutScreen(portal: portal) { ordered.value = $0 })
            defer { host.close() }
            #expect(await host.waitFor("Plug in your vehicle before you pay."))
            #expect(host.contains(portal.name))
            #expect(host.activate(labelPrefix: "Pay "))
            #expect(await host.waitUntil { ordered.value != nil })
            let order = try #require(ordered.value)
            #expect(order.evseName == portal.name)
            #expect(order.accessToken.count == 64)
            #expect(model.guestOrders.orders.first?.id == order.id)
            let payment = try await api.guestSession(id: order.id, accessToken: order.accessToken)
            #expect(payment.prepaidMinor == 2_000)
            model.guestOrders.remove(order.id)
        }

        @Test func guestSessionFollowsThePaymentUntilItCanBeRemoved() async throws {
            let model = await DemoEnvironment.guest()
            let api = try #require(model.api)
            let checkout = try await api.checkout(workspace: "demo", evseId: demoLink.evseId, request: CheckoutRequest(requestId: UUID(), accessToken: "guest-view", creditMinor: 3_000))
            let order = GuestOrder(id: checkout.orderId, accessToken: "guest-view", evseName: "Gare de Lyon — Borne A", createdAt: .now)
            model.guestOrders.add(order)
            let host = ViewHost(NavigationStack { GuestSessionScreen(order: order) })
            defer { host.close() }
            #expect(await host.waitFor("Starting"))
            #expect(host.contains("Credit paid"))
            _ = try await api.stopGuestSession(id: order.id, accessToken: order.accessToken)
            #expect(await host.waitFor("Remove from this device", timeout: .seconds(12)))
            #expect(host.contains("Finished"))
            #expect(host.contains("The refund has been sent to Stripe"))
            #expect(host.activate(label: "Remove from this device"))
            #expect(await host.waitUntil { !model.guestOrders.orders.contains { $0.id == order.id } })
        }

        @Test func chargingPointIDFindsTheStationForDrivers() async throws {
            await DemoEnvironment.signedIn()
            let route = Box<ChargeRoute>()
            let host = ViewHost(NavigationStack { ChargingPointIDScreen(prefill: "fr*dem*epar011", operatorURL: nil) { route.value = $0 } })
            defer { host.close() }
            #expect(await host.waitFor("Find this charging point"))
            #expect(host.activate(label: "Find this charging point"))
            #expect(await host.waitUntil { route.value != nil })
            #expect(route.value == .station("PAR-01", connectorId: 1))
        }

        @Test func chargingPointIDSendsGuestsToThePortal() async throws {
            await DemoEnvironment.guest()
            let route = Box<ChargeRoute>()
            let host = ViewHost(NavigationStack { ChargingPointIDScreen(prefill: "FR*DEM*EPAR011", operatorURL: nil) { route.value = $0 } })
            defer { host.close() }
            #expect(await host.waitFor("Find this charging point"))
            #expect(host.activate(label: "Find this charging point"))
            #expect(await host.waitUntil { route.value != nil })
            #expect(route.value == .portal(demoLink))
        }

        @Test func unknownChargingPointOffersTheOperatorWebsite() async throws {
            await DemoEnvironment.signedIn()
            let route = Box<ChargeRoute>()
            let host = ViewHost(NavigationStack { ChargingPointIDScreen(prefill: "FR*XYZ*E999", operatorURL: URL(string: "https://pay.other.example/x")) { route.value = $0 } })
            defer { host.close() }
            #expect(await host.waitFor("Find this charging point"))
            #expect(host.activate(label: "Find this charging point"))
            #expect(await host.waitFor("Continue on pay.other.example"))
            #expect(host.contains("This charging point is not on Réseau Démo"))
            #expect(route.value == nil)
        }

        @Test func scannerFallsBackToManualEntryAndDemoScan() async throws {
            await DemoEnvironment.signedIn()
            let route = Box<ChargeRoute>()
            let host = ViewHost(QRScannerScreen { route.value = $0 })
            defer { host.close() }
            #expect(await host.waitFor("Simulate scanning the demo QR code"))
            #expect(host.contains("The camera is not available on this device"))
            #expect(host.activate(label: "Simulate scanning the demo QR code"))
            #expect(await host.waitUntil { route.value != nil })
            #expect(route.value == .portal(demoLink))
            route.value = nil
            #expect(host.activate(label: "Enter the charging point ID"))
            #expect(await host.waitUntil { route.value != nil })
            #expect(route.value == .chargingPointID(nil, operatorURL: nil))
        }

        @Test func scannerExplainsUnreadableCodes() async throws {
            await DemoEnvironment.signedIn()
            let route = Box<ChargeRoute>()
            let host = ViewHost(QRScannerScreen { route.value = $0 })
            defer { host.close() }
            #expect(await host.waitFor("Open"))
            #expect(host.type("bonjour", into: "Charging point link"))
            await host.settle()
            #expect(host.activate(label: "Open"))
            #expect(await host.waitFor(QRCodeParser.message(for: .notALink)))
            #expect(route.value == nil)
            #expect(host.type("FR*DEM*EPAR011", into: "Charging point link"))
            await host.settle()
            #expect(host.activate(label: "Open"))
            #expect(await host.waitUntil { route.value != nil })
            #expect(route.value == .chargingPointID("FR*DEM*EPAR011", operatorURL: nil))
        }

        @Test func gaugeAndRowsSummariseASession() async throws {
            await DemoEnvironment.signedIn()
            let station = TestData.station(name: "Borne Test")
            let active = TestData.session(soc: 64)
            let stopped = TestData.session(id: 2, stoppedAt: TestData.instant, soc: nil, stopReason: "EVDisconnected")
            let host = ViewHost(List {
                LiveSessionGauge(session: active, station: station)
                LiveSessionGauge(session: stopped, station: nil)
                ActiveSessionRow(session: active, station: nil)
                SessionRow(session: active, stationName: "Borne Test")
                SessionRow(session: stopped, stationName: nil)
            })
            defer { host.close() }
            #expect(await host.waitFor("Battery 64%"))
            #expect(host.contains("Borne Test"))
            #expect(host.contains(String(localized: "Vehicle unplugged")))
            #expect(host.contains("In progress"))
            #expect(host.contains(Formatters.power(11)))
        }

        @Test func tariffRowsHideFreeComponents() async throws {
            await DemoEnvironment.signedIn()
            let host = ViewHost(List {
                PortalTariffRows(tariff: ChargingTariff(currency: "EUR", energyPrice: Decimal(string: "0.39")!, timePrice: 0, flatPrice: Decimal(string: "0.5")!))
            })
            defer { host.close() }
            #expect(await host.waitFor("Session fee"))
            #expect(host.contains("/ kWh"))
            #expect(!host.contains("Charging time"))
        }
    }
}
