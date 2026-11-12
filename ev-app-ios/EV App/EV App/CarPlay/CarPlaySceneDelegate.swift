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
    private var tabBar: CPTabBarTemplate?
    private var stationsTemplate: CPPointOfInterestTemplate?
    private var sessionTemplate: CPInformationTemplate?
    private var refreshTask: Task<Void, Never>?
    private var onlyAvailable = false
    private var mapCenter: CLLocation?
    private var lastSessionId: Int?

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
        updateFilterButton()

        let session = CPInformationTemplate(title: String(localized: "Charging"), layout: .leading, items: [], actions: [])
        session.tabTitle = String(localized: "Charging")
        session.tabImage = UIImage(systemName: "bolt.fill")
        sessionTemplate = session

        let tabs = CPTabBarTemplate(templates: [stations, session])
        tabBar = tabs
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
        tabBar = nil
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
        let current = model.charging.current?.id
        if let current, current != lastSessionId, let tabBar {
            tabBar.select(sessionTemplate)
        }
        lastSessionId = current
    }

    private func updateFilterButton() {
        let title = onlyAvailable ? String(localized: "Available") : String(localized: "All")
        let button = CPBarButton(title: title) { [weak self] _ in
            guard let self else { return }
            onlyAvailable.toggle()
            updateFilterButton()
            update()
        }
        stationsTemplate?.trailingNavigationBarButtons = [button]
    }

    private func pointsOfInterest(_ model: AppModel) -> [CPPointOfInterest] {
        let origin = mapCenter ?? model.location.location
        let filter = StationFilter(onlyAvailable: onlyAvailable)
        return filter.apply(to: model.stations.stations, search: "", from: origin)
            .prefix(12)
            .compactMap { station in
                guard let coordinate = station.coordinate else { return nil }
                let item = MKMapItem(location: CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude), address: nil)
                item.name = station.name
                let availability = StationAvailability(station)
                let summary = [station.highestPowerKw.map(Formatters.power), station.distance(from: model.location.location).map { Formatters.distance(meters: $0) }]
                    .compactMap { $0 }
                    .joined(separator: " · ")
                let point = CPPointOfInterest(
                    location: item,
                    title: station.name,
                    subtitle: String(localized: availability.label),
                    summary: summary.isEmpty ? nil : summary,
                    detailTitle: station.name,
                    detailSubtitle: station.addressLine,
                    detailSummary: CarPlayContent.connectorSummary(station) ?? String(localized: availability.label),
                    pinImage: pin(for: availability)
                )
                point.primaryButton = CPTextButton(title: String(localized: "Directions"), textStyle: .confirm) { _ in
                    item.openInMaps(launchOptions: [MKLaunchOptionsDirectionsModeKey: MKLaunchOptionsDirectionsModeDriving])
                }
                return point
            }
    }

    private func pin(for availability: StationAvailability) -> UIImage? {
        let color: UIColor =
            switch availability {
            case .available: .systemGreen
            case .busy: .systemBlue
            case .outOfOrder: .systemRed
            case .offline: .systemGray
            }
        let configuration = UIImage.SymbolConfiguration(pointSize: 28, weight: .semibold)
            .applying(UIImage.SymbolConfiguration(paletteColors: [.white, color]))
        return UIImage(systemName: "bolt.circle.fill", withConfiguration: configuration)
    }

    private func stopAction() -> CPTextButton {
        CPTextButton(title: String(localized: "Stop charging"), textStyle: .cancel) { [weak self] _ in
            self?.presentStopConfirmation()
        }
    }

    private func presentStopConfirmation() {
        let stop = CPAlertAction(title: String(localized: "Stop charging"), style: .destructive) { [weak self] _ in
            guard let self else { return }
            interfaceController?.dismissTemplate(animated: true, completion: nil)
            Task { await self.stop() }
        }
        let cancel = CPAlertAction(title: String(localized: "Cancel"), style: .cancel) { [weak self] _ in
            self?.interfaceController?.dismissTemplate(animated: true, completion: nil)
        }
        let alert = CPAlertTemplate(titleVariants: [String(localized: "Stop this charging session?")], actions: [stop, cancel])
        interfaceController?.presentTemplate(alert, animated: true, completion: nil)
    }

    private func stop() async {
        let model = AppModel.shared
        guard let session = model.charging.current else { return }
        _ = try? await model.api?.stopSession(id: session.id)
        await model.charging.refresh()
        update()
    }

    func pointOfInterestTemplate(_ template: CPPointOfInterestTemplate, didChangeMapRegion region: MKCoordinateRegion) {
        mapCenter = CLLocation(latitude: region.center.latitude, longitude: region.center.longitude)
        update()
    }

    func pointOfInterestTemplate(_ template: CPPointOfInterestTemplate, didSelectPointOfInterest pointOfInterest: CPPointOfInterest) {
        template.selectedIndex = template.pointsOfInterest.firstIndex(of: pointOfInterest) ?? NSNotFound
    }
}
