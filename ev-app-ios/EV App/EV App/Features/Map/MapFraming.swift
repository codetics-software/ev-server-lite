//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import MapKit

enum MapFraming {
    static func rect(containing coordinates: [CLLocationCoordinate2D]) -> MKMapRect? {
        guard !coordinates.isEmpty else { return nil }
        let rect = coordinates.reduce(MKMapRect.null) { partial, coordinate in
            partial.union(MKMapRect(origin: MKMapPoint(coordinate), size: MKMapSize(width: 1, height: 1)))
        }
        let minimum = 4_000 * MKMapPointsPerMeterAtLatitude(coordinates[0].latitude)
        let width = max(rect.width, minimum)
        let height = max(rect.height, minimum)
        return MKMapRect(x: rect.midX - width / 2, y: rect.midY - height / 2, width: width, height: height)
            .insetBy(dx: -width * 0.25, dy: -height * 0.35)
    }
}
