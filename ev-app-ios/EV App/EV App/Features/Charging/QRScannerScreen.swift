//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI
import VisionKit

struct QRScannerScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let onLink: (PortalLink) -> Void

    @State private var manualCode = ""
    @State private var errorMessage: String?

    private var cameraAvailable: Bool {
        DataScannerViewController.isSupported && DataScannerViewController.isAvailable
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if cameraAvailable {
                    QRCameraView { handle($0) }
                        .ignoresSafeArea(edges: .bottom)
                        .overlay(alignment: .bottom) {
                            if let errorMessage {
                                Text(errorMessage)
                                    .font(.footnote)
                                    .padding(12)
                                    .background(.regularMaterial, in: .rect(cornerRadius: 12))
                                    .padding()
                            }
                        }
                } else {
                    manualEntry
                }
            }
            .navigationTitle("Scan a QR code")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }

    private var manualEntry: some View {
        Form {
            Section {
                Label("The camera is not available on this device. Paste the charging point link instead.", systemImage: "camera.badge.ellipsis")
            }
            Section {
                TextField("Charging point link", text: $manualCode)
                    .keyboardType(.URL)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                Button("Open") { handle(manualCode) }
                    .disabled(manualCode.isEmpty)
            }
            #if DEBUG
            if model.isDemo {
                Section("Debug only") {
                    Button("Simulate scanning the demo QR code") { handle(DemoData.portalLink) }
                        .accessibilityIdentifier("scan.demo")
                }
            }
            #endif
            if let errorMessage {
                Section { ErrorBanner(message: errorMessage) }
            }
        }
    }

    private func handle(_ value: String) {
        guard let portal = model.configuration?.portalURL else { return }
        switch QRCodeParser.parse(value, portalURL: portal) {
        case .success(let link): onLink(link)
        case .failure(let failure): errorMessage = QRCodeParser.message(for: failure)
        }
    }
}
