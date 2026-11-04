//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import MapKit
import SwiftUI

struct StationMapScreen: View {
    @Environment(AppModel.self) private var model
    @State private var position: MapCameraPosition = .automatic
    @State private var hasFramedStations = false
    @State private var hasFramedUser = false
    @State private var isMapReady = false
    @State private var selection: String?
    @State private var searchText = ""
    @State private var filter = StationFilter()
    @State private var showsList = false
    @State private var presented: CustomerStationResponse?

    private var filtered: [CustomerStationResponse] {
        filter.apply(to: model.stations.stations, search: searchText, from: model.location.location)
    }

    var body: some View {
        NavigationStack {
            Group {
                if showsList {
                    StationListView(stations: filtered, location: model.location.location) { presented = $0 }
                        .searchable(text: $searchText, placement: .navigationBarDrawer(displayMode: .always), prompt: "Name, site or city")
                } else {
                    map
                }
            }
            .navigationTitle("Stations")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    StationFilterMenu(filter: $filter)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(showsList ? "Show map" : "Show list", systemImage: showsList ? "map" : "list.bullet") {
                        showsList.toggle()
                    }
                    .accessibilityIdentifier("map.toggle")
                }
            }
            .sheet(item: $presented) { station in
                NavigationStack {
                    StationDetailScreen(stationId: station.id, preview: station)
                }
                .presentationDetents([.medium, .large])
                .presentationContentInteraction(.scrolls)
            }
            .onChange(of: selection) { _, id in
                presented = id.flatMap(model.stations.station(id:))
            }
            .onChange(of: presented) { _, station in
                if station == nil { selection = nil }
            }
            .onChange(of: model.stations.stations, initial: true) { frame() }
            .onChange(of: model.location.location) { frame() }
            .task {
                model.location.start()
                await model.stations.reload()
            }
            .refreshable { await model.stations.reload() }
        }
    }

    private func frame() {
        guard isMapReady, !hasFramedUser else { return }
        let location = model.location.location
        let nearest = StationFilter().apply(to: model.stations.stations, search: "", from: location).prefix(6)
        guard let rect = MapFraming.rect(containing: nearest.compactMap(\.coordinate) + [location?.coordinate].compactMap { $0 }) else { return }
        if location != nil {
            hasFramedUser = true
        } else if hasFramedStations {
            return
        }
        hasFramedStations = true
        let meters = max(rect.width, rect.height) / MKMapPointsPerMeterAtLatitude(rect.origin.coordinate.latitude)
        position = .camera(MapCamera(centerCoordinate: MKMapPoint(x: rect.midX, y: rect.midY).coordinate, distance: max(meters * 3.5, 12_000)))
    }

    private var map: some View {
        Map(position: $position, selection: $selection) {
            UserAnnotation()
            ForEach(filtered) { station in
                if let coordinate = station.coordinate {
                    Annotation(station.name, coordinate: coordinate) {
                        StationPin(availability: StationAvailability(station))
                    }
                    .tag(station.id)
                }
            }
        }
        .onMapCameraChange(frequency: .onEnd) {
            guard !isMapReady else { return }
            isMapReady = true
            frame()
        }
        .mapControls {
            MapUserLocationButton()
            MapCompass()
            MapScaleView()
        }
        .safeAreaInset(edge: .bottom) {
            NearbyStationsStrip(stations: Array(filtered.prefix(10)), location: model.location.location) { presented = $0 }
        }
        .overlay {
            if model.stations.stations.isEmpty, model.stations.isLoading {
                ProgressView()
            }
        }
    }
}
