//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct ChargeScreen: View {
    @Environment(AppModel.self) private var model
    @State private var path: [ChargeRoute] = []
    @State private var showsScanner = false

    var body: some View {
        @Bindable var model = model
        NavigationStack(path: $path) {
            List {
                if !model.charging.active.isEmpty {
                    Section("In progress") {
                        ForEach(model.charging.active) { session in
                            NavigationLink(value: ChargeRoute.session(session.id)) {
                                ActiveSessionRow(session: session, station: model.stations.station(id: session.stationId))
                            }
                        }
                    }
                }
                if !model.guestOrders.orders.isEmpty {
                    Section("Paid sessions on this device") {
                        ForEach(model.guestOrders.orders) { order in
                            NavigationLink(value: ChargeRoute.guest(order)) {
                                VStack(alignment: .leading) {
                                    Text(order.evseName)
                                    Text(order.createdAt, format: .dateTime.day().month().hour().minute())
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
                }
                Section {
                    Button {
                        showsScanner = true
                    } label: {
                        ChargeActionLabel(
                            title: "Scan a QR code",
                            detail: model.isSignedIn ? "Point the camera at the code on the station." : "No account needed: pay by card and start charging.",
                            systemImage: "qrcode.viewfinder"
                        )
                    }
                    .accessibilityIdentifier("charge.scan")
                    Button {
                        path.append(.chargingPointID(nil, operatorURL: nil))
                    } label: {
                        ChargeActionLabel(
                            title: "Enter a charging point ID",
                            detail: "Type the ID printed on the station, such as FR*ABC*E12345.",
                            systemImage: "keyboard"
                        )
                    }
                    .accessibilityIdentifier("charge.enterId")
                    Button {
                        model.selectedTab = .map
                    } label: {
                        ChargeActionLabel(
                            title: "Find a station",
                            detail: "See the stations around you on the map.",
                            systemImage: "map"
                        )
                    }
                }
            }
            .navigationTitle("Charge")
            .navigationDestination(for: ChargeRoute.self) { route in
                switch route {
                case .session(let id):
                    LiveSessionScreen(sessionId: id)
                case .portal(let link):
                    ChargingPointScreen(link: link) { path.append($0) }
                case .guest(let order):
                    GuestSessionScreen(order: order)
                case .chargingPointID(let prefill, let operatorURL):
                    ChargingPointIDScreen(prefill: prefill, operatorURL: operatorURL) { path.append($0) }
                case .station(let id, let connectorId):
                    StationDetailScreen(stationId: id, preview: model.stations.station(id: id), initialConnectorId: connectorId, showsClose: false)
                }
            }
            .fullScreenCover(isPresented: $showsScanner) {
                QRScannerScreen { route in
                    showsScanner = false
                    path.append(route)
                }
            }
            .onChange(of: model.pendingPortalLink, initial: true) { _, link in
                if let link {
                    path.append(.portal(link))
                    model.pendingPortalLink = nil
                }
            }
            .onChange(of: model.openedSessionId, initial: true) { _, id in
                if let id {
                    path = [.session(id)]
                    model.openedSessionId = nil
                }
            }
            .refreshable { await model.charging.refresh() }
        }
    }
}
