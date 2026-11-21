//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import CoreLocation
import Foundation
import MapKit
import Testing
@testable import EV_App

@MainActor
struct StationLocationTests {
    @Test func coordinateNeedsBothValues() {
        #expect(TestData.station(latitude: 45.76, longitude: 4.83).coordinate?.latitude == 45.76)
        #expect(TestData.station(latitude: nil, longitude: 4.83).coordinate == nil)
        #expect(TestData.station(latitude: 45.76, longitude: nil).coordinate == nil)
    }

    @Test func addressLineJoinsTheAvailableParts() {
        #expect(TestData.station(address: "1 place de la Gare", postalCode: "69002", city: "Lyon").addressLine == "1 place de la Gare, 69002 Lyon")
        #expect(TestData.station(city: "Lyon").addressLine == "Lyon")
        #expect(TestData.station(address: "1 place de la Gare").addressLine == "1 place de la Gare")
        #expect(TestData.station(postalCode: "69002").addressLine == "69002")
        #expect(TestData.station().addressLine == nil)
    }

    @Test func distanceIsMeasuredFromTheDriver() throws {
        let station = TestData.station(latitude: 48.8443, longitude: 2.3744)
        #expect(station.distance(from: nil) == nil)
        #expect(TestData.station(latitude: nil).distance(from: CLLocation(latitude: 0, longitude: 0)) == nil)
        let meters = try #require(station.distance(from: CLLocation(latitude: 48.8530, longitude: 2.3690)))
        #expect(meters > 900 && meters < 1_200)
    }

    @Test func highestPowerPrefersConnectorValues() {
        let station = TestData.station(connectors: [TestData.connector(1, power: 22), TestData.connector(2, power: 150)], maximumPowerKw: 50)
        #expect(station.highestPowerKw == 150)
        let unknownConnectors = TestData.station(connectors: [TestData.connector(power: nil)], maximumPowerKw: 50)
        #expect(unknownConnectors.highestPowerKw == 50)
        #expect(TestData.station(connectors: [TestData.connector(power: nil)]).highestPowerKw == nil)
    }
}

@MainActor
struct StationFilterTests {
    private let paris = CLLocation(latitude: 48.8566, longitude: 2.3522)

    private var stations: [CustomerStationResponse] {
        [
            TestData.station("PAR-01", name: "Gare de Lyon", connectors: [TestData.connector(status: "Available", type: "T2", power: 22)], latitude: 48.8443, longitude: 2.3744, siteName: "Gare", city: "Paris"),
            TestData.station("PAR-02", name: "Bercy Rapide", connectors: [TestData.connector(status: "Charging", type: "CCS", power: 150)], latitude: 48.8326, longitude: 2.3862, address: "28 rue François Truffaut", city: "Paris"),
            TestData.station("LYO-01", name: "Écully", connectors: [TestData.connector(status: "Preparing", type: "CHADEMO", power: 50)], latitude: 45.7867, longitude: 4.7766, city: "Lyon"),
            TestData.station("NOW-01", name: "Sans position", connectors: [TestData.connector(status: "Available", type: "T2", power: 7)], latitude: nil, longitude: nil),
        ]
    }

    @Test func defaultFilterIsInactiveAndKeepsEverything() {
        let filter = StationFilter()
        #expect(!filter.isActive)
        #expect(filter.apply(to: stations, search: "", from: paris).count == 4)
    }

    @Test func eachCriterionActivatesTheFilter() {
        #expect(StationFilter(onlyAvailable: true).isActive)
        #expect(StationFilter(connectorType: "CCS").isActive)
        #expect(StationFilter(minimumPowerKw: 50).isActive)
    }

    @Test func availabilityTypeAndPowerNarrowTheList() {
        #expect(StationFilter(onlyAvailable: true).apply(to: stations, search: "", from: nil).map(\.id) == ["LYO-01", "PAR-01", "NOW-01"])
        #expect(StationFilter(connectorType: "CCS").apply(to: stations, search: "", from: nil).map(\.id) == ["PAR-02"])
        #expect(StationFilter(minimumPowerKw: 50).apply(to: stations, search: "", from: paris).map(\.id) == ["PAR-02", "LYO-01"])
        #expect(StationFilter(onlyAvailable: true, minimumPowerKw: 50).apply(to: stations, search: "", from: nil).map(\.id) == ["LYO-01"])
    }

    @Test func searchLooksAtNameIdSiteCityAndAddress() {
        let filter = StationFilter()
        #expect(filter.apply(to: stations, search: "  bercy ", from: nil).map(\.id) == ["PAR-02"])
        #expect(filter.apply(to: stations, search: "lyo-01", from: nil).map(\.id) == ["LYO-01"])
        #expect(filter.apply(to: stations, search: "ecully", from: nil).map(\.id) == ["LYO-01"])
        #expect(filter.apply(to: stations, search: "Gare", from: nil).map(\.id) == ["PAR-01"])
        #expect(filter.apply(to: stations, search: "Truffaut", from: nil).map(\.id) == ["PAR-02"])
        #expect(filter.apply(to: stations, search: "Lyon", from: nil).map(\.id).sorted() == ["LYO-01", "PAR-01"])
        #expect(filter.apply(to: stations, search: "Marseille", from: nil).isEmpty)
    }

    @Test func sortsByDistanceWithUnlocatedStationsLast() {
        let ordered = StationFilter().apply(to: stations, search: "", from: paris).map(\.id)
        #expect(ordered == ["PAR-01", "PAR-02", "LYO-01", "NOW-01"])
    }

    @Test func sortsByNameWithoutALocation() {
        let numbered = [TestData.station("B", name: "Station 10"), TestData.station("A", name: "Station 2"), TestData.station("C", name: "Abbesses")]
        #expect(StationFilter().apply(to: numbered, search: "", from: nil).map(\.name) == ["Abbesses", "Station 2", "Station 10"])
    }
}

@MainActor
struct MapFramingTests {
    @Test func noCoordinatesMeansNoFrame() {
        #expect(MapFraming.rect(containing: []) == nil)
    }

    @Test func singleStationGetsAComfortableMinimumSpan() throws {
        let gare = CLLocationCoordinate2D(latitude: 48.8443, longitude: 2.3744)
        let rect = try #require(MapFraming.rect(containing: [gare]))
        let minimum = 4_000 * MKMapPointsPerMeterAtLatitude(gare.latitude)
        #expect(rect.contains(MKMapPoint(gare)))
        #expect(rect.width >= minimum * 1.49)
        #expect(rect.height >= minimum * 1.69)
    }

    @Test func framesEveryStationWithMargins() throws {
        let paris = CLLocationCoordinate2D(latitude: 48.8566, longitude: 2.3522)
        let lyon = CLLocationCoordinate2D(latitude: 45.7640, longitude: 4.8357)
        let rect = try #require(MapFraming.rect(containing: [paris, lyon]))
        #expect(rect.contains(MKMapPoint(paris)))
        #expect(rect.contains(MKMapPoint(lyon)))
        let tight = MKMapRect(origin: MKMapPoint(paris), size: MKMapSize(width: 1, height: 1)).union(MKMapRect(origin: MKMapPoint(lyon), size: MKMapSize(width: 1, height: 1)))
        #expect(rect.width > tight.width * 1.4)
        #expect(rect.height > tight.height * 1.6)
    }
}

@MainActor
struct CarPlayConnectorSummaryTests {
    @Test func groupsConnectorsByTypeWithFreeCount() {
        let station = TestData.station(connectors: [
            TestData.connector(1, status: "Available", type: "T2", power: 11),
            TestData.connector(2, status: "Charging", type: "Type2", power: 22),
            TestData.connector(3, status: "Preparing", type: "CCS", power: 150),
        ])
        let summary = CarPlayContent.connectorSummary(station)
        #expect(summary == "Type 2 \(Formatters.power(22)) · 1/2\nCCS \(Formatters.power(150)) · 1/1")
    }

    @Test func offlineStationsShowNoFreeConnector() {
        let station = TestData.station(connected: false, connectors: [TestData.connector(1, status: "Available", type: "T2", power: nil)])
        #expect(CarPlayContent.connectorSummary(station) == "Type 2 · 0/1")
    }

    @Test func stationWithoutConnectorsHasNoSummary() {
        #expect(CarPlayContent.connectorSummary(TestData.station(connectors: [])) == nil)
    }
}

@MainActor
struct FormattersTests {
    @Test func missingValuesShowADash() {
        #expect(Formatters.price(nil, currency: "EUR") == "—")
        #expect(Formatters.minor(nil, currency: "EUR") == "—")
        #expect(Formatters.energy(nil) == "—")
        #expect(Formatters.power(nil) == "—")
    }

    @Test func pricesKeepUpToFourDecimals() {
        #expect(Formatters.price(Decimal(string: "0.3912"), currency: "EUR").filter(\.isNumber) == "03912")
        #expect(Formatters.price(2, currency: "EUR").filter(\.isNumber) == "200")
        #expect(Formatters.price(2, currency: nil) == Formatters.price(2, currency: "EUR"))
        #expect(Formatters.price(2, currency: "USD") != Formatters.price(2, currency: "EUR"))
    }

    @Test func minorUnitsAreDividedByOneHundred() {
        #expect(Formatters.minor(1_250, currency: "EUR").filter(\.isNumber) == "1250")
        #expect(Formatters.minor(1_250, currency: "EUR") == Formatters.price(Decimal(string: "12.5"), currency: "EUR"))
    }

    @Test func energyAndPowerUseTheirUnits() {
        #expect(Formatters.energy(12.5).contains("kWh"))
        #expect(Formatters.energy(12.5).filter(\.isNumber) == "125")
        #expect(Formatters.energy(3).filter(\.isNumber) == "3")
        #expect(Formatters.power(7.5).contains("kW"))
        #expect(!Formatters.power(7.5).contains("kWh"))
        #expect(Formatters.power(7.5).filter(\.isNumber) == "75")
        #expect(Formatters.power(150).filter(\.isNumber) == "150")
    }

    @Test func durationsShowHoursAndMinutes() {
        #expect(Formatters.duration(seconds: 5_400).filter(\.isNumber) == "130")
        #expect(Formatters.duration(seconds: 600).filter(\.isNumber) == "10")
        #expect(Formatters.duration(seconds: 5_400) != Formatters.duration(seconds: 600))
    }

    @Test func distancesScaleWithTheValue() {
        let near = Formatters.distance(meters: 300)
        let far = Formatters.distance(meters: 25_000)
        #expect(!near.isEmpty)
        #expect(near != far)
    }
}

struct QRCodeReaderTests {
    private let portal = URL(string: "https://reseau.fr")!
    private let evse = "5f9f1c1e-7c3a-4d3a-9c55-0a7e6b1d2f10"

    @Test func printedIdentifiersAreReadAsChargingPoints() {
        #expect(QRCodeParser.read(" fr*cod*e 12345 ", portalURL: portal) == .success(.chargingPoint("FR*COD*E12345")))
    }

    @Test func networkLinksOpenThePortal() throws {
        let scan = try QRCodeParser.read("https://reseau.fr/charge/codetics/\(evse)", portalURL: portal).get()
        #expect(scan == .portal(PortalLink(workspace: "codetics", evseId: UUID(uuidString: evse)!)))
    }

    @Test func otherOperatorsAreOfferedWithTheirIdentifier() throws {
        let link = "https://pay.other.example/start?evse=FR*ABC*E1234*1"
        let scan = try QRCodeParser.read(link, portalURL: portal).get()
        #expect(scan == .operatorPortal(URL(string: link)!, chargingPoint: "FR*ABC*E1234*1"))
        let plain = try QRCodeParser.read("http://other.example/x", portalURL: portal).get()
        #expect(plain == .operatorPortal(URL(string: "http://other.example/x")!, chargingPoint: nil))
    }

    @Test func nonWebSchemesAreRejected() {
        #expect(QRCodeParser.read("ftp://other.example/x", portalURL: portal) == .failure(.notALink))
        #expect(QRCodeParser.read("hello", portalURL: portal) == .failure(.notALink))
        #expect(QRCodeParser.read("https://reseau.fr/account", portalURL: portal) == .failure(.notAChargingPoint))
    }

    @Test func failuresHaveDistinctMessages() {
        let messages = [QRCodeParser.Failure.notALink, .foreignNetwork, .notAChargingPoint].map(QRCodeParser.message(for:))
        #expect(Set(messages).count == 3)
        #expect(messages.allSatisfy { !$0.isEmpty })
    }
}

struct JSONCodingTests {
    @Test func invalidInstantsFailDecoding() {
        #expect(throws: DecodingError.self) {
            try JSONCoding.decoder.decode([Date].self, from: Data(#"["yesterday"]"#.utf8))
        }
    }

    @Test func instantsKeepMillisecondPrecision() throws {
        let date = try #require(JSONCoding.parseInstant("2026-11-04T08:55:28.536984Z"))
        let reference = try #require(JSONCoding.parseInstant("2026-11-04T08:55:28Z"))
        #expect(abs(date.timeIntervalSince(reference) - 0.536) < 0.0005)
    }

    @Test func decodesSnakeCaseAndEncodesISO8601() throws {
        let response = try JSONCoding.decoder.decode(UserSiteResponse.self, from: Data(#"{"site_id":"c0000000-0000-4000-8000-000000000001","name":"Parking","site_admin":true,"site_owner":false}"#.utf8))
        #expect(response.siteAdmin)
        #expect(response.id == UUID(uuidString: "c0000000-0000-4000-8000-000000000001"))
        let data = try JSONCoding.encoder.encode(["at": Date(timeIntervalSince1970: 0)])
        #expect(String(decoding: data, as: UTF8.self).contains("1970-01-01T00:00:00Z"))
    }
}
