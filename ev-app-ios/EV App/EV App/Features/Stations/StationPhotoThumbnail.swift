//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import SwiftUI

struct StationPhotoThumbnail: View {
    @Environment(AppModel.self) private var model
    let photo: StationPhotoResponse
    @State private var image: UIImage?

    var body: some View {
        Group {
            if let image {
                Image(uiImage: image).resizable().scaledToFill()
            } else {
                Rectangle().fill(.fill.tertiary).overlay { ProgressView() }
            }
        }
        .frame(width: 110, height: 110)
        .clipShape(.rect(cornerRadius: 12))
        .accessibilityLabel("Station photo")
        .task(id: photo.id) {
            if let data = try? await model.api?.photoData(photo) {
                image = UIImage(data: data)
            }
        }
    }
}
