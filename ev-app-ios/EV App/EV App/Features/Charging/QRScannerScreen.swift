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
    let onRoute: (ChargeRoute) -> Void

    @State private var manualCode = ""
    @State private var errorMessage: String?
    @State private var foreign: ForeignCode?
    @State private var safariURL: IdentifiableURL?
    @State private var recognized = 0

    private struct ForeignCode: Equatable {
        let url: URL
        let chargingPoint: String?
    }

    private var cameraAvailable: Bool {
        DataScannerViewController.isSupported && DataScannerViewController.isAvailable
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if cameraAvailable {
                    camera
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
            .sensoryFeedback(.success, trigger: recognized)
            .confirmationDialog(
                "Charging point of another operator",
                isPresented: Binding(get: { foreign != nil }, set: { if !$0 { foreign = nil } }),
                titleVisibility: .visible,
                presenting: foreign
            ) { code in
                Button("Charge with this app") {
                    foreign = nil
                    onRoute(.chargingPointID(code.chargingPoint, operatorURL: code.url))
                }
                Button("Open the operator’s website") {
                    foreign = nil
                    safariURL = IdentifiableURL(url: code.url)
                }
                Button("Cancel", role: .cancel) { foreign = nil }
            } message: { code in
                Text("This QR code opens \(code.url.host() ?? code.url.absoluteString). You can pay on the operator’s website, or look for the charging point in this app.")
            }
            .sheet(item: $safariURL) { SafariView(url: $0.url).ignoresSafeArea() }
        }
    }

    private var camera: some View {
        QRCameraView { handle($0) }
            .ignoresSafeArea(edges: .bottom)
            .overlay(alignment: .top) {
                Text("Point the camera at the QR code on the charging point.")
                    .font(.subheadline.weight(.medium))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(.regularMaterial, in: .capsule)
                    .padding(.top, 12)
                    .padding(.horizontal)
            }
            .overlay(alignment: .bottom) {
                VStack(spacing: 12) {
                    if let errorMessage {
                        Text(errorMessage)
                            .font(.footnote)
                            .padding(12)
                            .background(.regularMaterial, in: .rect(cornerRadius: 12))
                    }
                    Button {
                        onRoute(.chargingPointID(nil, operatorURL: nil))
                    } label: {
                        Label("Enter the charging point ID", systemImage: "keyboard")
                            .font(.subheadline.weight(.semibold))
                            .padding(.horizontal, 6)
                    }
                    .buttonStyle(.borderedProminent)
                    .buttonBorderShape(.capsule)
                    .controlSize(.large)
                    .accessibilityIdentifier("scan.enterId")
                }
                .padding()
                .padding(.bottom, 8)
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
            Section {
                Button("Enter the charging point ID", systemImage: "keyboard") {
                    onRoute(.chargingPointID(nil, operatorURL: nil))
                }
                .accessibilityIdentifier("scan.enterId")
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
        guard foreign == nil, safariURL == nil, let portal = model.configuration?.portalURL else { return }
        switch QRCodeParser.read(value, portalURL: portal) {
        case .success(.portal(let link)):
            recognized += 1
            onRoute(.portal(link))
        case .success(.chargingPoint(let id)):
            recognized += 1
            onRoute(.chargingPointID(id, operatorURL: nil))
        case .success(.operatorPortal(let url, let id)):
            recognized += 1
            errorMessage = nil
            foreign = ForeignCode(url: url, chargingPoint: id)
        case .failure(let failure):
            errorMessage = QRCodeParser.message(for: failure)
        }
    }
}
