//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct CarCatalogScreen: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let onSelect: (RecordResponse<CarCatalogData>) -> Void

    @State private var search = ""
    @State private var entries: [RecordResponse<CarCatalogData>] = []
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            List(entries) { entry in
                Button {
                    onSelect(entry)
                } label: {
                    VStack(alignment: .leading) {
                        Text("\(entry.data.make) \(entry.data.model)").font(.headline)
                        if let version = entry.data.version {
                            Text(version).font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
                .buttonStyle(.plain)
            }
            .overlay {
                if let errorMessage {
                    ErrorBanner(message: errorMessage)
                } else if entries.isEmpty {
                    ContentUnavailableView.search
                }
            }
            .searchable(text: $search, prompt: "Make or model")
            .navigationTitle("Vehicle catalog")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
            .task(id: search) {
                try? await Task.sleep(for: .milliseconds(250))
                guard !Task.isCancelled else { return }
                do {
                    entries = try await model.api?.carCatalog(search: search).items ?? []
                    errorMessage = nil
                } catch is CancellationError {
                    return
                } catch {
                    errorMessage = error.localizedDescription
                }
            }
        }
    }
}
