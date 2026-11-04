//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import CarPlay
import MapKit
import UIKit

final class CarPlaySceneDelegate: UIResponder, CPTemplateApplicationSceneDelegate, CPPointOfInterestTemplateDelegate {
    private var interfaceController: CPInterfaceController?
    private var stationsTemplate: CPPointOfInterestTemplate?
    private var sessionTemplate: CPInformationTemplate?
    private var refreshTask: Task<Void, Never>?

    func templateApplicationScene(
        _ templateApplicationScene: CPTemplateApplicationScene,
        didConnect interfaceController: CPInterfaceController
    ) {
        self.interfaceController = interfaceController
        let stations = CPPointOfInterestTemplate(title: String(localized: "Stations"), pointsOfInterest: [], selectedIndex: NSNotFound)
        stations.pointOfInterestDelegate = self
        stations.tabTitle = String(localized: "Stations")
        stations.tabImage = UIImage(systemName: "bolt.car")
        stationsTemplate = stations

        let session = CPInformationTemplate(title: String(localized: "Charging"), layout: .leading, items: [], actions: [])
        session.tabTitle = String(localized: "Charging")
        session.tabImage = UIImage(systemName: "bolt.fill")
        sessionTemplate = session

        let tabs = CPTabBarTemplate(templates: [stations, session])
        interfaceController.setRootTemplate(tabs, animated: false, completion: nil)
        refreshTask = Task { await monitor() }
    }

    func templateApplicationScene(
        _ templateApplicationScene: CPTemplateApplicationScene,
        didDisconnectInterfaceController interfaceController: CPInterfaceController
    ) {
        refreshTask?.cancel()
        refreshTask = nil
        self.interfaceController = nil
    }

    private func monitor() async {
        let model = AppModel.shared
        if model.launch == .checking { await model.bootstrap() }
        model.location.start()
        while !Task.isCancelled {
            await model.stations.reload()
            await model.charging.refresh()
            update()
            try? await Task.sleep(for: .seconds(model.charging.current == nil ? 30 : 5))
        }
    }

    private func update() {
        let model = AppModel.shared
        stationsTemplate?.setPointsOfInterest(pointsOfInterest(model), selectedIndex: NSNotFound)
        guard let sessionTemplate else { return }
        let content = CarPlayContent.session(model)
        sessionTemplate.items = content.items
        sessionTemplate.actions = content.isActive ? [stopAction()] : []
    }

    private func pointsOfInterest(_ model: AppModel) -> [CPPointOfInterest] {
        let location = model.location.location
        let filter = StationFilter()
        return filter.apply(to: model.stations.stations, search: "", from: location)
            .prefix(12)
            .compactMap { station in
                guard let coordinate = station.coordinate else { return nil }
                let item = MKMapItem(location: CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude), address: nil)
                item.name = station.name
                let availability = StationAvailability(station)
                let point = CPPointOfInterest(
                    location: item,
                    title: station.name,
                    subtitle: String(localized: availability.label),
                    summary: station.highestPowerKw.map(Formatters.power),
                    detailTitle: station.name,
                    detailSubtitle: station.addressLine,
                    detailSummary: String(localized: availability.label),
                    pinImage: nil
                )
                point.primaryButton = CPTextButton(title: String(localized: "Directions"), textStyle: .confirm) { _ in
                    item.openInMaps(launchOptions: [MKLaunchOptionsDirectionsModeKey: MKLaunchOptionsDirectionsModeDriving])
                }
                return point
            }
    }

    private func stopAction() -> CPTextButton {
        CPTextButton(title: String(localized: "Stop charging"), textStyle: .cancel) { [weak self] _ in
            guard let self else { return }
            Task { await self.confirmStop() }
        }
    }

    private func confirmStop() async {
        let model = AppModel.shared
        guard let session = model.charging.current else { return }
        _ = try? await model.api?.stopSession(id: session.id)
        await model.charging.refresh()
        update()
    }

    func pointOfInterestTemplate(_ template: CPPointOfInterestTemplate, didChangeMapRegion region: MKCoordinateRegion) {}

    func pointOfInterestTemplate(_ template: CPPointOfInterestTemplate, didSelectPointOfInterest pointOfInterest: CPPointOfInterest) {
        template.selectedIndex = template.pointsOfInterest.firstIndex(of: pointOfInterest) ?? NSNotFound
    }
}
