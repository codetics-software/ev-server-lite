//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct StationFilterMenu: View {
    @Environment(AppModel.self) private var model
    @Binding var filter: StationFilter

    private var connectorTypes: [String] {
        Array(Set(model.stations.stations.flatMap { $0.connectors.compactMap(\.type) })).sorted()
    }

    var body: some View {
        Menu {
            Toggle("Available now", isOn: $filter.onlyAvailable)
            Picker("Connector", selection: $filter.connectorType) {
                Text("All connectors").tag(String?.none)
                ForEach(connectorTypes, id: \.self) { type in
                    Text(ConnectorType.label(for: type)).tag(String?.some(type))
                }
            }
            .pickerStyle(.menu)
            Picker("Power", selection: $filter.minimumPowerKw) {
                Text("Any power").tag(0.0)
                Text("11 kW or more").tag(11.0)
                Text("22 kW or more").tag(22.0)
                Text("50 kW or more").tag(50.0)
                Text("150 kW or more").tag(150.0)
            }
            .pickerStyle(.menu)
        } label: {
            Label("Filter", systemImage: filter.isActive ? "line.3.horizontal.decrease.circle.fill" : "line.3.horizontal.decrease.circle")
        }
    }
}
