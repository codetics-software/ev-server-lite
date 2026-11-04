//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct VehicleEditorScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let target: VehicleEditorTarget
    let onSaved: () async -> Void

    @State private var name = ""
    @State private var data = CarData()
    @State private var battery = ""
    @State private var ac = ""
    @State private var dc = ""
    @State private var isDefault = false
    @State private var showsCatalog = false
    @State private var errorMessage: String?
    @State private var isSaving = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Button("Choose from the catalog", systemImage: "magnifyingglass") { showsCatalog = true }
                }
                Section("Vehicle") {
                    TextField("Name (optional)", text: $name)
                    TextField("Make", text: binding(\.make))
                        .accessibilityIdentifier("vehicle.make")
                    TextField("Model", text: binding(\.model))
                        .accessibilityIdentifier("vehicle.model")
                    TextField("Licence plate", text: binding(\.licensePlate))
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                    TextField("VIN (17 characters)", text: binding(\.vin))
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                }
                Section("Specifications") {
                    numberField("Battery (kWh)", text: $battery)
                    numberField("AC power (kW)", text: $ac)
                    numberField("DC power (kW)", text: $dc)
                }
                Section {
                    Toggle("Use as my default vehicle", isOn: $isDefault)
                }
                if let errorMessage {
                    Section { ErrorBanner(message: errorMessage) }
                }
            }
            .navigationTitle(isNew ? "New vehicle" : "Edit vehicle")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { Task { await save() } }
                        .disabled(isSaving)
                        .accessibilityIdentifier("vehicle.save")
                }
            }
            .sheet(isPresented: $showsCatalog) {
                CarCatalogScreen { entry in
                    data.carCatalogId = entry.id
                    data.make = entry.data.make
                    data.model = [entry.data.model, entry.data.version].compactMap { $0 }.joined(separator: " ")
                    battery = entry.data.batteryKwh.map { String($0) } ?? battery
                    ac = entry.data.maxAcPowerKw.map { String($0) } ?? ac
                    dc = entry.data.maxDcPowerKw.map { String($0) } ?? dc
                    showsCatalog = false
                }
            }
            .onAppear(perform: populate)
        }
    }

    private var isNew: Bool {
        if case .new = target { return true }
        return false
    }

    private func numberField(_ title: LocalizedStringKey, text: Binding<String>) -> some View {
        TextField(title, text: text).keyboardType(.decimalPad)
    }

    private func binding(_ keyPath: WritableKeyPath<CarData, String?>) -> Binding<String> {
        Binding(
            get: { data[keyPath: keyPath] ?? "" },
            set: { data[keyPath: keyPath] = $0.isEmpty ? nil : $0 }
        )
    }

    private func populate() {
        guard case .edit(let car) = target else { return }
        name = car.name
        data = car.data
        battery = car.data.batteryKwh.map { String($0) } ?? ""
        ac = car.data.maxAcPowerKw.map { String($0) } ?? ""
        dc = car.data.maxDcPowerKw.map { String($0) } ?? ""
        isDefault = car.data.default ?? false
    }

    private func parse(_ value: String) -> Double? {
        Double(value.replacingOccurrences(of: ",", with: "."))
    }

    private func save() async {
        guard let api = model.api else { return }
        isSaving = true
        defer { isSaving = false }
        var payload = data
        payload.batteryKwh = parse(battery)
        payload.maxAcPowerKw = parse(ac)
        payload.maxDcPowerKw = parse(dc)
        payload.licensePlate = payload.licensePlate?.uppercased()
        payload.vin = payload.vin?.uppercased()
        payload.default = isDefault
        let label = name.trimmingCharacters(in: .whitespaces).nilIfEmpty
            ?? [payload.make, payload.model].compactMap { $0 }.joined(separator: " ").nilIfEmpty
            ?? payload.licensePlate
            ?? String(localized: "Vehicle")
        do {
            switch target {
            case .new:
                _ = try await api.createCar(RecordInput(name: label, data: payload, revision: nil))
            case .edit(let car):
                _ = try await api.updateCar(id: car.id, RecordInput(name: label, data: payload, revision: car.revision))
            }
            await onSaved()
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
