//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import Charts
import SwiftUI

struct LiveSessionScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let sessionId: Int

    @State private var session: ChargingSessionResponse?
    @State private var points: [ConsumptionPoint] = []
    @State private var errorMessage: String?
    @State private var isStopping = false
    @State private var stopRequested = false
    @State private var confirmsStop = false

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                if let session {
                    LiveSessionGauge(session: session, station: model.stations.station(id: session.stationId))
                    metrics(session)
                    if points.count > 1 {
                        ConsumptionChart(points: points)
                            .padding()
                            .background(.fill.quaternary, in: .rect(cornerRadius: 16))
                    }
                    if session.isActive {
                        stopButton
                    }
                    if stopRequested, session.isActive {
                        Text("The stop request has been sent. If the station does not respond, stop charging from your vehicle or at the station.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                } else if errorMessage == nil {
                    ProgressView().padding(.top, 60)
                }
                if let errorMessage {
                    ErrorBanner(message: errorMessage)
                }
            }
            .padding()
        }
        .navigationTitle(session?.isActive == false ? "Session finished" : "Charging")
        .navigationBarTitleDisplayMode(.inline)
        .task(id: sessionId) { await poll() }
    }

    private func metrics(_ session: ChargingSessionResponse) -> some View {
        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
            MetricTile(title: "Energy", value: Formatters.energy(session.energyKwh), systemImage: "bolt.fill")
            MetricTile(title: "Duration", value: Formatters.duration(seconds: session.durationSeconds), systemImage: "timer")
            MetricTile(title: session.isActive ? "Estimated price" : "Price", value: Formatters.price(session.price, currency: session.currency), systemImage: "eurosign.circle")
            MetricTile(title: "Connector", value: String(session.connectorId), systemImage: "powerplug.portrait.fill")
        }
    }

    private var stopButton: some View {
        Button(role: .destructive) {
            confirmsStop = true
        } label: {
            Label(isStopping ? "Stopping…" : "Stop charging", systemImage: "stop.circle.fill")
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.borderedProminent)
        .controlSize(.large)
        .tint(.red)
        .disabled(isStopping)
        .accessibilityIdentifier("session.stop")
        .confirmationDialog("Stop this charging session?", isPresented: $confirmsStop, titleVisibility: .visible) {
            Button("Stop charging", role: .destructive) { Task { await stop() } }
        }
    }

    private func poll() async {
        while !Task.isCancelled {
            await refresh()
            if session?.isActive == false { return }
            try? await Task.sleep(for: .seconds(5))
        }
    }

    private func refresh() async {
        guard let api = model.api else { return }
        do {
            async let current = api.session(id: sessionId)
            async let curve = api.consumptions(sessionId: sessionId)
            let (latest, samples) = try await (current, curve)
            withAnimation { session = latest }
            points = samples
            errorMessage = nil
        } catch is CancellationError {
            return
        } catch APIError.unauthorized {
            model.sessionExpired()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func stop() async {
        isStopping = true
        defer { isStopping = false }
        do {
            _ = try await model.api?.stopSession(id: sessionId)
            stopRequested = true
            await refresh()
            await model.charging.refresh()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
