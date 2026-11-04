//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import PhotosUI
import SwiftUI

struct StationPhotosSection: View {
    @Environment(AppModel.self) private var model
    let stationId: String

    @State private var photos: [StationPhotoResponse] = []
    @State private var selection: PhotosPickerItem?
    @State private var showsCamera = false
    @State private var isUploading = false
    @State private var errorMessage: String?
    @State private var viewing: StationPhotoResponse?

    private var hasCamera: Bool { UIImagePickerController.isSourceTypeAvailable(.camera) }

    var body: some View {
        Section {
            if !photos.isEmpty {
                ScrollView(.horizontal) {
                    LazyHStack(spacing: 8) {
                        ForEach(photos) { photo in
                            Button {
                                viewing = photo
                            } label: {
                                StationPhotoThumbnail(photo: photo)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .scrollIndicators(.hidden)
            }
            if hasCamera {
                Button("Take a photo", systemImage: "camera") { showsCamera = true }
                    .accessibilityIdentifier("photo.camera")
            }
            PhotosPicker(selection: $selection, matching: .images) {
                Label("Choose from library", systemImage: "photo.on.rectangle")
            }
            .accessibilityIdentifier("photo.library")
            #if DEBUG
            if model.isDemo {
                Button("Add a demo photo", systemImage: "wand.and.stars") {
                    Task { await upload(DemoImages.station()) }
                }
                .accessibilityIdentifier("photo.demo")
            }
            #endif
            if isUploading {
                ProgressView("Sending photo…")
            }
            if let errorMessage {
                ErrorBanner(message: errorMessage)
            }
        } header: {
            Text("Photos")
        } footer: {
            Text("Photos are visible to the other drivers of this network. Do not show people or number plates.")
        }
        .fullScreenCover(isPresented: $showsCamera) {
            CameraPicker { image in
                Task { await upload(image) }
            }
            .ignoresSafeArea()
        }
        .sheet(item: $viewing) { photo in
            StationPhotoViewer(photo: photo, stationId: stationId) {
                photos.removeAll { $0.id == photo.id }
            }
        }
        .onChange(of: selection) { _, item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self), let image = UIImage(data: data) {
                    await upload(image)
                } else {
                    errorMessage = String(localized: "This image could not be read.")
                }
                selection = nil
            }
        }
        .task(id: stationId) { await load() }
    }

    private func load() async {
        do {
            photos = try await model.api?.photos(stationId: stationId) ?? []
        } catch is CancellationError {
            return
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func upload(_ image: UIImage) async {
        guard let data = PhotoEncoder.jpeg(from: image) else {
            errorMessage = String(localized: "This image could not be prepared.")
            return
        }
        isUploading = true
        errorMessage = nil
        defer { isUploading = false }
        do {
            if let photo = try await model.api?.uploadPhoto(stationId: stationId, jpeg: data) {
                photos.insert(photo, at: 0)
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
