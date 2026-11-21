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
    struct StationViewTests {
        @Test func signedInDetailListsConnectorsTariffAndActions() async throws {
            await DemoEnvironment.signedIn()
            let host = ViewHost(NavigationStack { StationDetailScreen(stationId: "PAR-01", preview: nil) })
            defer { host.close() }
            #expect(await host.waitFor("FR*DEM*EPAR012"))
            #expect(await host.waitFor("Tarif réseau"))
            #expect(host.contains("Type 2 · no. 1"))
            #expect(host.contains("Get directions"))
            #expect(host.contains("Report a problem"))
            #expect(host.contains("Add a demo photo"))
            #expect(host.element(label: "Start") != nil)
            #expect(!host.contains("To start a charge, sign in"))
        }

        @Test func guestDetailExplainsHowToStart() async throws {
            await DemoEnvironment.guest()
            let host = ViewHost(NavigationStack { StationDetailScreen(stationId: "PAR-01", preview: nil) })
            defer { host.close() }
            #expect(await host.waitFor("To start a charge, sign in or scan the QR code on the station."))
            #expect(host.contains("Type 2 · no. 2"))
            #expect(host.element(label: "Start") == nil)
            #expect(!host.contains("Report a problem"))
        }

        @Test func autoChargeStationsSayItAndUnknownStationsShowAnError() async throws {
            await DemoEnvironment.signedIn()
            let auto = ViewHost(NavigationStack { StationDetailScreen(stationId: "PAR-02", preview: nil) })
            #expect(await auto.waitFor("Automatic charging when you plug in"))
            #expect(auto.contains("CCS · no. 1"))
            auto.close()
            let missing = ViewHost(NavigationStack { StationDetailScreen(stationId: "NOPE", preview: nil) })
            defer { missing.close() }
            #expect(await missing.waitFor("Borne introuvable."))
        }

        @Test func openingFromAChargingPointOffersToStart() async throws {
            let model = await DemoEnvironment.signedIn()
            try await DemoEnvironment.stopLiveSessions(try #require(model.api))
            let host = ViewHost(NavigationStack { StationDetailScreen(stationId: "PAR-07", preview: nil, initialConnectorId: 1, showsClose: false) })
            defer { host.close() }
            #expect(await host.waitFor("Plug in your vehicle before you start."))
            #expect(await host.waitFor("CARTE-1042"))
        }

        @Test func demoPhotoIsUploadedAndCanBeDeleted() async throws {
            let model = await DemoEnvironment.signedIn()
            let api = try #require(model.api)
            let before = try await api.photos(stationId: "PAR-06").count
            let host = ViewHost(List { StationPhotosSection(stationId: "PAR-06") })
            #expect(await host.waitFor("Add a demo photo"))
            #expect(host.contains("Choose from library"))
            #expect(host.activate(label: "Add a demo photo"))
            var photos: [StationPhotoResponse] = []
            for _ in 0..<100 where photos.count <= before {
                try await Task.sleep(for: .milliseconds(100))
                photos = try await api.photos(stationId: "PAR-06")
            }
            host.close()
            let photo = try #require(photos.first)
            #expect(photos.count == before + 1)
            #expect(photo.mine)

            let deleted = Box<Bool>()
            let viewer = ViewHost(StationPhotoViewer(photo: photo, stationId: "PAR-06") { deleted.value = true })
            defer { viewer.close() }
            #expect(await viewer.waitFor("Station photo"))
            #expect(viewer.activate(label: "Delete"))
            #expect(await viewer.waitUntil { deleted.value == true })
            #expect(try await api.photos(stationId: "PAR-06").count == before)
        }

        @Test func reportProblemShowsTheStation() async throws {
            await DemoEnvironment.signedIn()
            let host = ViewHost(ReportProblemScreen(stationId: "PAR-04", connectorId: 1, subject: "Écran noir"))
            defer { host.close() }
            #expect(await host.waitFor("PAR-04"))
            #expect(host.contains("Report a problem"))
            #expect(host.textField("Subject")?.text == "Écran noir")
        }

        @Test func mapScreenSwitchesToAFilteredList() async throws {
            await DemoEnvironment.signedIn()
            let host = ViewHost(StationMapScreen())
            defer { host.close() }
            #expect(await host.waitFor("Show list"))
            #expect(host.activate(label: "Show list"))
            #expect(await host.waitFor("Bastille — Parking"))
            #expect(host.contains("Montparnasse"))
            #expect(host.contains("Show map"))
        }

        @Test func stationListShowsAnEmptyState() async throws {
            await DemoEnvironment.signedIn()
            let host = ViewHost(StationListView(stations: [], location: nil) { _ in })
            defer { host.close() }
            #expect(await host.waitFor("No stations found"))
        }

        @Test func stationRowsAndCardsDescribeAvailability() async throws {
            let model = await DemoEnvironment.signedIn()
            await model.stations.reload()
            let selected = Box<String>()
            let host = ViewHost(StationListView(stations: model.stations.stations, location: nil) { selected.value = $0.id })
            defer { host.close() }
            #expect(await host.waitFor("Hôtel de Ville"))
            #expect(host.contains("Offline"))
            #expect(host.contains("Out of order"))
            #expect(host.activate(containing: "République"))
            #expect(await host.waitUntil { selected.value == "PAR-04" })
        }

        @Test func nearbyStripListsTheClosestStations() async throws {
            let model = await DemoEnvironment.signedIn()
            await model.stations.reload()
            let host = ViewHost(NearbyStationsStrip(stations: Array(model.stations.stations.prefix(3)), location: nil) { _ in })
            defer { host.close() }
            #expect(await host.waitFor("Bercy Village — Rapide"))
        }
    }
}
