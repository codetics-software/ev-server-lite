//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct ConnectorRow: View {
    @Environment(AppModel.self) private var model
    let connector: ConnectorResponse
    let station: CustomerStationResponse
    let onStart: (StartRequest) -> Void
    let onOpenSession: (Int) -> Void

    var body: some View {
        let status = ConnectorStatus(connector.status)
        HStack(spacing: 12) {
            Image(systemName: "powerplug.portrait.fill")
                .foregroundStyle(status.color)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text("\(ConnectorType.label(for: connector.type)) · no. \(connector.connectorId)")
                    .font(.subheadline.weight(.medium))
                HStack(spacing: 8) {
                    Text(status.label).foregroundStyle(status.color)
                    if let power = connector.maximumPowerKw {
                        Text(Formatters.power(power))
                    }
                }
                .font(.caption)
                if let evseId = connector.evseId {
                    Text(evseId)
                        .font(.caption2.monospaced())
                        .foregroundStyle(.tertiary)
                        .textSelection(.enabled)
                }
            }
            Spacer(minLength: 0)
            if let session = connector.currentTransactionId {
                Button("Open my session") { onOpenSession(session) }
                    .buttonStyle(.bordered)
            } else if model.isSignedIn, status.isStartable, station.connected {
                Button("Start") {
                    onStart(StartRequest(stationId: station.id, stationName: station.name, connectorId: connector.connectorId))
                }
                .buttonStyle(.borderedProminent)
                .accessibilityIdentifier("connector.start.\(connector.connectorId)")
            }
        }
    }
}
