//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct VehicleDetailScreen: View {
    @Environment(AppModel.self) private var model
    let car: RecordResponse<CarData>
    let candidates: [AutoChargeCandidate]
    let onChanged: () async -> Void

    @State private var vehicleIds: [VehicleIdResponse] = []
    @State private var errorMessage: String?
    @State private var showsEditor = false

    var body: some View {
        List {
            Section("Vehicle") {
                if let make = car.data.make { LabeledContent("Make", value: make) }
                if let carModel = car.data.model { LabeledContent("Model", value: carModel) }
                if let plate = car.data.licensePlate { LabeledContent("Licence plate", value: plate) }
                if let vin = car.data.vin { LabeledContent("VIN", value: vin) }
                if let battery = car.data.batteryKwh { LabeledContent("Battery", value: Formatters.energy(battery)) }
                if let ac = car.data.maxAcPowerKw { LabeledContent("AC power", value: Formatters.power(ac)) }
                if let dc = car.data.maxDcPowerKw { LabeledContent("DC power", value: Formatters.power(dc)) }
            }
            Section {
                ForEach(vehicleIds) { id in
                    HStack {
                        Label(id.uid, systemImage: "bolt.badge.automatic")
                            .font(.footnote.monospaced())
                        Spacer()
                        Button("Remove", role: .destructive) { Task { await revoke(id) } }
                            .buttonStyle(.borderless)
                    }
                }
                ForEach(candidates) { candidate in
                    Button("Enable with the session on \(candidate.stationId)", systemImage: "plus.circle") {
                        Task { await enroll(candidate) }
                    }
                }
                if vehicleIds.isEmpty, candidates.isEmpty {
                    Text("Plug this vehicle into a compatible station and start a session once. You can then enable automatic charging here for 24 hours.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            } header: {
                Text("Automatic charging")
            } footer: {
                Text("On compatible stations, charging starts when you plug in, with no badge. Anyone using this vehicle can charge on your account.")
            }
            if let errorMessage {
                ErrorBanner(message: errorMessage)
            }
        }
        .navigationTitle(car.name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button("Edit") { showsEditor = true }
            }
        }
        .sheet(isPresented: $showsEditor) {
            VehicleEditorScreen(target: .edit(car)) { await onChanged() }
        }
        .task(id: car.id) { await load() }
    }

    private func load() async {
        do {
            vehicleIds = try await model.api?.vehicleIds(carId: car.id) ?? []
        } catch is CancellationError {
            return
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func enroll(_ candidate: AutoChargeCandidate) async {
        do {
            _ = try await model.api?.enrollAutoCharge(carId: car.id, transactionId: candidate.transactionId)
            await load()
            await onChanged()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func revoke(_ id: VehicleIdResponse) async {
        do {
            try await model.api?.revokeAutoCharge(carId: car.id, uid: id.uid)
            await load()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
