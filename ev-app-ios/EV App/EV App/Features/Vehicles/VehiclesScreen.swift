//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct VehiclesScreen: View {
    @Environment(AppModel.self) private var model
    @State private var cars: [RecordResponse<CarData>] = []
    @State private var defaultCarId: UUID?
    @State private var candidates: [AutoChargeCandidate] = []
    @State private var isLoading = true
    @State private var errorMessage: String?
    @State private var editor: VehicleEditorTarget?

    var body: some View {
        List {
            if let errorMessage {
                ErrorBanner(message: errorMessage)
            }
            ForEach(cars) { car in
                NavigationLink {
                    VehicleDetailScreen(car: car, candidates: candidates) { await load() }
                } label: {
                    VehicleRow(car: car, isDefault: car.id == defaultCarId)
                }
            }
            .onDelete { offsets in
                Task { await delete(offsets) }
            }
        }
        .overlay {
            if cars.isEmpty, !isLoading, errorMessage == nil {
                ContentUnavailableView {
                    Label("No vehicle", systemImage: "car")
                } description: {
                    Text("Add your vehicle to get better charging estimates and automatic charging.")
                } actions: {
                    Button("Add a vehicle") { editor = .new }
                        .buttonStyle(.borderedProminent)
                }
            }
        }
        .navigationTitle("My vehicles")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button("Add a vehicle", systemImage: "plus") { editor = .new }
                    .accessibilityIdentifier("vehicles.add")
            }
        }
        .sheet(item: $editor) { target in
            VehicleEditorScreen(target: target) { await load() }
        }
        .task { await load() }
        .refreshable { await load() }
    }

    private func load() async {
        guard let api = model.api else { return }
        defer { isLoading = false }
        do {
            async let owned = api.cars()
            async let profile = api.profile()
            async let plugged = api.autoChargeCandidates()
            cars = try await owned
            defaultCarId = try await profile.defaultCarId
            candidates = try await plugged
            errorMessage = nil
        } catch is CancellationError {
            return
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func delete(_ offsets: IndexSet) async {
        for index in offsets {
            do {
                try await model.api?.deleteCar(id: cars[index].id)
            } catch {
                errorMessage = error.localizedDescription
            }
        }
        await load()
    }
}
