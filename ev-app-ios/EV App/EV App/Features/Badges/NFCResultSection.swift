//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct NFCResultSection: View {
    @Environment(AppModel.self) private var model
    let outcome: BadgeMatcher.Outcome
    @State private var showsReport = false

    var body: some View {
        Section("Result") {
            switch outcome {
            case .registered(let token):
                result("This card is registered on your account.", symbol: "checkmark.seal.fill", color: .green, detail: "Badge \(token.visualId ?? token.uid), held by \(token.userName ?? model.principal?.name ?? "")")
            case .disabled(let token):
                result("This card is on your account but disabled.", symbol: "pause.circle.fill", color: .orange, detail: "Badge \(token.visualId ?? token.uid). Contact your operator to enable it again.")
            case .expired(let token):
                result("This card is on your account but has expired.", symbol: "calendar.badge.exclamationmark", color: .orange, detail: "Badge \(token.visualId ?? token.uid). Contact your operator to renew it.")
            case .notRegistered(let uid):
                result("This card is not registered on your account.", symbol: "xmark.seal.fill", color: .red, detail: "Card number \(uid). It may be unknown to this network or belong to someone else.")
                Button("Ask my operator to add it", systemImage: "exclamationmark.bubble") { showsReport = true }
                    .sheet(isPresented: $showsReport) {
                        ReportProblemScreen(stationId: nil, connectorId: nil, subject: String(localized: "Please add my card \(uid)"))
                    }
            }
        }
    }

    private func result(_ title: LocalizedStringResource, symbol: String, color: Color, detail: LocalizedStringResource) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: symbol)
                .font(.title2)
                .foregroundStyle(color)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(.headline)
                Text(detail).font(.footnote).foregroundStyle(.secondary)
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("nfc.result")
    }
}
