//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct StationPhotoViewer: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let photo: StationPhotoResponse
    let stationId: String
    let onDeleted: () -> Void

    @State private var image: UIImage?
    @State private var errorMessage: String?
    @State private var showsReport = false

    var body: some View {
        NavigationStack {
            VStack {
                if let image {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFit()
                        .accessibilityLabel("Station photo")
                } else {
                    ProgressView()
                }
                if let errorMessage {
                    ErrorBanner(message: errorMessage)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .navigationTitle(Text(photo.createdAt, format: .dateTime.day().month().year()))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
                ToolbarItem(placement: .primaryAction) {
                    if photo.mine {
                        Button("Delete", systemImage: "trash", role: .destructive) {
                            Task { await delete() }
                        }
                    } else {
                        Button("Report this photo", systemImage: "flag") { showsReport = true }
                    }
                }
            }
            .sheet(isPresented: $showsReport) {
                ReportProblemScreen(
                    stationId: stationId,
                    connectorId: nil,
                    subject: String(localized: "Inappropriate photo \(photo.id.uuidString.lowercased())")
                )
            }
            .task(id: photo.id) {
                if let data = try? await model.api?.photoData(photo) {
                    image = UIImage(data: data)
                }
            }
        }
    }

    private func delete() async {
        do {
            try await model.api?.deletePhoto(photo)
            onDeleted()
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
