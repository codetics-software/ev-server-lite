//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import PassKit
import SwiftUI

struct BadgeDetailScreen: View {
    @Environment(AppModel.self) private var model
    let token: ChargingTokenResponse

    @State private var pass: IdentifiablePass?
    @State private var isLoading = false
    @State private var errorMessage: String?

    private var alreadyAdded: Bool {
        guard let identifier = pass?.pass.passTypeIdentifier, let serial = pass?.pass.serialNumber else { return false }
        return PKPassLibrary().pass(withPassTypeIdentifier: identifier, serialNumber: serial) != nil
    }

    var body: some View {
        List {
            Section {
                WalletPassPreview(token: token, holder: model.principal?.name ?? "", network: model.workspace?.name ?? model.brandName)
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(Color.clear)
            }
            Section("Details") {
                LabeledContent("Number", value: token.uid)
                if let visual = token.visualId {
                    LabeledContent("Printed number", value: visual)
                }
                LabeledContent("Status") {
                    Text(token.enabled ? "Active" : "Disabled")
                }
                if let group = token.parentIdTag {
                    LabeledContent("Badge group", value: group)
                }
                if let expiry = token.expiresAt {
                    LabeledContent("Expires") {
                        Text(expiry, format: .dateTime.day().month().year())
                    }
                }
                if let used = token.lastUsedAt {
                    LabeledContent("Last used") {
                        Text(used, format: .dateTime.day().month().year().hour().minute())
                    }
                }
            }
            walletSection
        }
        .navigationTitle(token.visualId ?? token.uid)
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $pass) { item in
            AddPassesView(pass: item.pass).ignoresSafeArea()
        }
    }

    @ViewBuilder
    private var walletSection: some View {
        Section {
            if model.server?.walletEnabled == true, PKPassLibrary.isPassLibraryAvailable(), PKAddPassesViewController.canAddPasses() {
                AddPassToWalletButton {
                    Task { await addToWallet() }
                }
                .addPassToWalletButtonStyle(.black)
                .frame(height: 50)
                .frame(maxWidth: .infinity)
                .disabled(isLoading || !token.enabled)
                .accessibilityIdentifier("wallet.add")
                .listRowBackground(Color.clear)
            } else {
                Label("Apple Wallet is not available for this badge on this server or device.", systemImage: "wallet.pass")
                    .foregroundStyle(.secondary)
            }
            if isLoading {
                ProgressView("Preparing your pass…")
            }
            if let errorMessage {
                ErrorBanner(message: errorMessage)
            }
        } header: {
            Text("Apple Wallet")
        } footer: {
            if model.server?.walletNfcEnabled == true {
                Text("The pass is sent over NFC only to readers compatible with Apple Wallet. Other stations still need your physical card or the app.")
            } else {
                Text("This pass shows your badge as a QR code. Contact your operator to find out which stations accept it.")
            }
        }
    }

    private func addToWallet() async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            guard let data = try await model.api?.walletPass(uid: token.uid) else { return }
            pass = IdentifiablePass(pass: try PKPass(data: data))
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
