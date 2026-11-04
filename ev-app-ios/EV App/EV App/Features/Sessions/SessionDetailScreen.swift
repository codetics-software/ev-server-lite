//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct SessionDetailScreen: View {
    @Environment(AppModel.self) private var model
    let session: ChargingSessionResponse

    @State private var detail: ChargingSessionResponse?
    @State private var points: [ConsumptionPoint] = []
    @State private var signed: SignedMeterData?
    @State private var showsReport = false

    private var current: ChargingSessionResponse { detail ?? session }

    var body: some View {
        List {
            Section {
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                    MetricTile(title: "Energy", value: Formatters.energy(current.energyKwh), systemImage: "bolt.fill")
                    MetricTile(title: "Duration", value: Formatters.duration(seconds: current.durationSeconds), systemImage: "timer")
                    MetricTile(title: "Price", value: Formatters.price(current.price, currency: current.currency), systemImage: "eurosign.circle")
                    MetricTile(title: "Idle time", value: Formatters.duration(seconds: current.inactivitySeconds), systemImage: "pause.circle")
                }
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)
            }
            if points.count > 1 {
                Section {
                    ConsumptionChart(points: points)
                }
            }
            Section("Details") {
                LabeledContent("Station", value: model.stations.station(id: current.stationId)?.name ?? current.stationId)
                LabeledContent("Connector", value: String(current.connectorId))
                LabeledContent("Started") {
                    Text(current.startedAt, format: .dateTime.day().month().year().hour().minute())
                }
                if let stopped = current.stoppedAt {
                    LabeledContent("Ended") {
                        Text(stopped, format: .dateTime.day().month().year().hour().minute())
                    }
                }
                if let start = current.stateOfChargeStart, let end = current.stateOfCharge {
                    LabeledContent("Battery", value: "\(start)% → \(end)%")
                }
                if let badge = current.tokenUid {
                    LabeledContent("Badge", value: badge)
                }
                if let partner = current.roamingPartner {
                    LabeledContent("Roaming partner", value: partner)
                }
                LabeledContent("Billing") {
                    Text(BillingStatus.label(current.billingStatus))
                }
            }
            if let signed, signed.start != nil || signed.stop != nil {
                SignedMeterSection(data: signed)
            }
            Section {
                if current.isActive {
                    NavigationLink("Follow live") { LiveSessionScreen(sessionId: current.id) }
                }
                Button("Report a problem", systemImage: "exclamationmark.bubble") { showsReport = true }
            }
        }
        .navigationTitle("Session \(current.id)")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showsReport) {
            ReportProblemScreen(stationId: current.stationId, connectorId: current.connectorId, subject: String(localized: "Session \(current.id)"))
        }
        .task(id: session.id) {
            guard let api = model.api else { return }
            detail = try? await api.session(id: session.id)
            points = (try? await api.consumptions(sessionId: session.id)) ?? []
            if current.signedMeterData {
                signed = try? await api.signedData(sessionId: session.id)
            }
        }
    }
}
