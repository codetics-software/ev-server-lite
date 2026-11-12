//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct ChargingPointIDScreen: View {
    @Environment(AppModel.self) private var model
    let operatorURL: URL?
    let onRoute: (ChargeRoute) -> Void

    @State private var code: String
    @State private var isSearching = false
    @State private var notFound = false
    @State private var safariURL: IdentifiableURL?
    @FocusState private var focused: Bool

    init(prefill: String?, operatorURL: URL?, onRoute: @escaping (ChargeRoute) -> Void) {
        self.operatorURL = operatorURL
        self.onRoute = onRoute
        _code = State(initialValue: prefill ?? "")
    }

    var body: some View {
        Form {
            Section {
                TextField("FR*ABC*E12345*1", text: $code)
                    .font(.title3.monospaced())
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                    .submitLabel(.search)
                    .focused($focused)
                    .onSubmit { Task { await search() } }
                    .onChange(of: code) { notFound = false }
                    .accessibilityIdentifier("chargingPoint.code")
            } header: {
                Text("Charging point ID")
            } footer: {
                Text("Type the ID printed on the charging point, usually next to the QR code or on the connector label.")
            }
            Section {
                Button {
                    Task { await search() }
                } label: {
                    HStack {
                        Text("Find this charging point")
                        Spacer()
                        if isSearching { ProgressView() }
                    }
                }
                .disabled(ChargingPointID.normalize(code).isEmpty || isSearching)
                .accessibilityIdentifier("chargingPoint.find")
            }
            if notFound {
                Section {
                    Label {
                        Text("This charging point is not on \(model.brandName). Check the ID, or charge from the operator’s website.")
                    } icon: {
                        Image(systemName: "questionmark.circle").foregroundStyle(.orange)
                    }
                    if let operatorURL {
                        Button("Continue on \(operatorURL.host() ?? "")", systemImage: "safari") {
                            safariURL = IdentifiableURL(url: operatorURL)
                        }
                    }
                }
            }
        }
        .navigationTitle("Charging point ID")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $safariURL) { SafariView(url: $0.url).ignoresSafeArea() }
        .onAppear { if code.isEmpty { focused = true } }
    }

    private func search() async {
        guard !ChargingPointID.normalize(code).isEmpty else { return }
        isSearching = true
        defer { isSearching = false }
        if model.stations.stations.isEmpty { await model.stations.reload() }
        var match = ChargingPointID.match(code, in: model.stations.stations)
        if match == nil {
            await model.stations.reload()
            match = ChargingPointID.match(code, in: model.stations.stations)
        }
        guard let match else {
            notFound = true
            return
        }
        if !model.isSignedIn,
           let link = match.station.publicEvses.first(where: { match.connectorId == nil || $0.connectorId == match.connectorId })
        {
            onRoute(.portal(PortalLink(workspace: link.workspaceSlug, evseId: link.id)))
        } else {
            onRoute(.station(match.station.id, connectorId: match.connectorId))
        }
    }
}
