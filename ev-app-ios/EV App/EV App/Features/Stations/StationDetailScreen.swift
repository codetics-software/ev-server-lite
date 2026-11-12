//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import MapKit
import SwiftUI

struct StationDetailScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let stationId: String
    let preview: CustomerStationResponse?
    var initialConnectorId: Int?
    var showsClose = true

    @State private var loaded: CustomerStationResponse?
    @State private var errorMessage: String?
    @State private var startRequest: StartRequest?
    @State private var startedSession: ChargingSessionResponse?
    @State private var showsReport = false
    @State private var offeredStart = false

    private var station: CustomerStationResponse? { loaded ?? preview }

    var body: some View {
        List {
            if let station {
                content(station)
            } else if let errorMessage {
                ErrorBanner(message: errorMessage)
            } else {
                ProgressView()
            }
        }
        .navigationTitle(station?.name ?? String(localized: "Station"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if showsClose {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done", systemImage: "xmark") { dismiss() }
                        .accessibilityIdentifier("station.close")
                }
            }
        }
        .sheet(item: $startRequest) { request in
            StartChargeScreen(request: request) { session in
                startRequest = nil
                startedSession = session
            }
        }
        .sheet(isPresented: $showsReport) {
            ReportProblemScreen(stationId: stationId, connectorId: nil, subject: "")
        }
        .navigationDestination(item: $startedSession) { session in
            LiveSessionScreen(sessionId: session.id)
        }
        .task(id: stationId) {
            await load()
            offerStart()
        }
        .refreshable { await load() }
    }

    @ViewBuilder
    private func content(_ station: CustomerStationResponse) -> some View {
        Section {
            StationHeader(station: station, location: model.location.location)
            if let coordinate = station.coordinate {
                Button("Get directions", systemImage: "arrow.triangle.turn.up.right.diamond.fill") {
                    openDirections(station: station, coordinate: coordinate)
                }
            }
        }
        Section("Connectors") {
            if station.connectors.isEmpty {
                Text("This station has not declared any connector yet.").foregroundStyle(.secondary)
            }
            ForEach(station.connectors) { connector in
                ConnectorRow(connector: connector, station: station) { request in
                    startRequest = request
                } onOpenSession: { id in
                    openSession(id)
                }
            }
            if !model.isSignedIn, station.connectors.contains(where: { ConnectorStatus($0.status).isStartable }) {
                Text("To start a charge, sign in or scan the QR code on the station.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            if station.autoCharge {
                Label("Automatic charging when you plug in", systemImage: "car.side.and.exclamationmark")
                    .font(.footnote)
            }
        }
        if let tariff = station.tariff {
            TariffSection(tariff: tariff)
        }
        if model.isSignedIn {
            StationPhotosSection(stationId: station.id)
            Section {
                Button("Report a problem", systemImage: "exclamationmark.bubble") { showsReport = true }
            }
        }
    }

    private func offerStart() {
        guard !offeredStart, model.isSignedIn, let initialConnectorId, let station, station.connected,
              let connector = station.connectors.first(where: { $0.connectorId == initialConnectorId }),
              connector.currentTransactionId == nil, ConnectorStatus(connector.status).isStartable
        else { return }
        offeredStart = true
        startRequest = StartRequest(stationId: station.id, stationName: station.name, connectorId: connector.connectorId)
    }

    private func openSession(_ id: Int) {
        model.openedSessionId = id
        model.selectedTab = .charge
        dismiss()
    }

    private func openDirections(station: CustomerStationResponse, coordinate: CLLocationCoordinate2D) {
        let item = MKMapItem(location: CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude), address: nil)
        item.name = station.name
        item.openInMaps(launchOptions: [MKLaunchOptionsDirectionsModeKey: MKLaunchOptionsDirectionsModeDriving])
    }

    private func load() async {
        guard let api = model.api else { return }
        do {
            if model.isSignedIn {
                loaded = try await api.station(id: stationId)
            } else if let slug = model.configuration?.workspaceSlug {
                loaded = try await api.publicStation(workspace: slug, id: stationId)
            }
            errorMessage = nil
        } catch is CancellationError {
            return
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
