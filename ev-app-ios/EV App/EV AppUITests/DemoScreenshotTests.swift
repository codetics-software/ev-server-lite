//
//  EV Supervision Lite — built by Kevin Vavelin, Codetics
//  https://codetics.fr
//  Copyright (c) 2026 Kevin Vavelin, Codetics
//  SPDX-License-Identifier: LicenseRef-Codetics-Attribution-1.0
//

import CoreLocation
import XCTest

final class DemoScreenshotTests: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        XCUIDevice.shared.location = XCUILocation(location: CLLocation(latitude: 48.853, longitude: 2.3499))
    }

    private func launch(guest: Bool = false, locale: String = "fr") {
        app = XCUIApplication()
        app.launchArguments = ["-demoAccount"] + (guest ? ["-demoGuest"] : []) + ["-AppleLanguages", "(\(locale))", "-AppleLocale", "\(locale)_FR"]
        app.launch()
        allowLocation()
    }

    private func allowLocation() {
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        for label in ["Autoriser lorsque l’app est active", "Allow While Using App", "Autoriser", "Allow"] {
            let button = springboard.buttons[label]
            if button.waitForExistence(timeout: 2) {
                button.tap()
                return
            }
        }
    }

    private func shot(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    private func tab(_ index: Int) {
        app.tabBars.buttons.element(boundBy: index).tap()
    }

    private func waitTabs(_ count: Int) {
        XCTAssertTrue(app.tabBars.buttons.element(boundBy: count - 1).waitForExistence(timeout: 20))
    }

    private func element(_ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier].firstMatch
    }

    private func scrollTo(_ identifier: String) -> XCUIElement {
        let target = element(identifier)
        var attempts = 0
        while !target.isHittable, attempts < 8 {
            app.swipeUp()
            attempts += 1
        }
        return target
    }

    private func back() {
        app.navigationBars.buttons.element(boundBy: 0).tap()
        sleep(1)
    }

    func testConfigurationGate() {
        app = XCUIApplication()
        app.launchArguments = ["-AppleLanguages", "(fr)", "-AppleLocale", "fr_FR"]
        app.launch()
        XCTAssertTrue(element("demo.start").waitForExistence(timeout: 10))
        sleep(1)
        shot("00-application-non-configuree")
    }

    func testGuestFlow() {
        launch(guest: true)
        waitTabs(3)
        sleep(20)
        shot("10-invite-carte")
        tab(1)
        XCTAssertTrue(element("charge.scan").waitForExistence(timeout: 5))
        shot("11-invite-recharge")
        element("charge.scan").tap()
        XCTAssertTrue(element("scan.demo").waitForExistence(timeout: 5))
        shot("12-invite-scan")
        element("scan.demo").tap()
        XCTAssertTrue(element("portal.guest").waitForExistence(timeout: 8))
        sleep(1)
        shot("13-invite-point-de-charge")
        element("portal.guest").tap()
        XCTAssertTrue(element("checkout.pay").waitForExistence(timeout: 5))
        sleep(1)
        shot("14-invite-credit")
        element("checkout.pay").tap()
        XCTAssertTrue(element("guest.stop").waitForExistence(timeout: 15))
        shot("15-invite-recharge-en-cours")
        tab(2)
        XCTAssertTrue(element("signin.email").waitForExistence(timeout: 5))
        sleep(1)
        shot("16-connexion")
    }

    func testDriverChargeFlow() {
        launch()
        waitTabs(5)
        sleep(20)
        shot("01-carte-bornes")
        element("map.toggle").tap()
        sleep(1)
        shot("02-liste-bornes")
        let row = app.staticTexts["Gare de Lyon — Borne A"].firstMatch
        XCTAssertTrue(row.waitForExistence(timeout: 5))
        row.tap()
        XCTAssertTrue(element("connector.start.1").waitForExistence(timeout: 8))
        sleep(1)
        shot("03-detail-borne")
        element("connector.start.1").tap()
        XCTAssertTrue(element("start.confirm").waitForExistence(timeout: 5))
        sleep(2)
        shot("05-demarrer-charge")
        element("start.confirm").tap()
        XCTAssertTrue(element("session.stop").waitForExistence(timeout: 20))
        sleep(8)
        shot("06-charge-temps-reel")
        app.swipeUp()
        sleep(1)
        shot("06b-charge-courbe")
        back()
        sleep(1)
        element("station.close").tap()
        sleep(1)
        shot("09-session-en-cours-accessoire")
        element("session.accessory").tap()
        XCTAssertTrue(element("session.stop").waitForExistence(timeout: 10))
        element("session.stop").tap()
        sleep(1)
        shot("09b-arret-confirmation")
    }

    func testStationPhotos() {
        launch()
        waitTabs(5)
        element("map.toggle").tap()
        let row = app.staticTexts["Montparnasse"].firstMatch
        XCTAssertTrue(row.waitForExistence(timeout: 5))
        row.tap()
        sleep(1)
        let demoPhoto = scrollTo("photo.demo")
        demoPhoto.tap()
        sleep(2)
        shot("04-photos-borne")
    }

    func testCardActivation() {
        launch()
        waitTabs(5)
        tab(3)
        XCTAssertTrue(element("cards.activate").waitForExistence(timeout: 8))
        sleep(1)
        shot("24-commander-activer-carte")
        element("cards.activate").tap()
        XCTAssertTrue(element("activation.number").waitForExistence(timeout: 5))
        sleep(1)
        shot("25-activer-carte")
        element("activation.number").tap()
        element("activation.number").typeText("0000")
        element("activation.submit").tap()
        XCTAssertTrue(app.staticTexts["Aucune carte commandée sur votre compte ne correspond à ce numéro."].waitForExistence(timeout: 5))
        shot("26-activer-carte-numero-refuse")
        let field = element("activation.number")
        field.tap()
        field.press(forDuration: 1.2)
        for _ in 0..<4 { field.typeText(XCUIKeyboardKey.delete.rawValue) }
        field.typeText("CARTE-2210")
        element("activation.submit").tap()
        XCTAssertTrue(element("activation.success").waitForExistence(timeout: 5))
        sleep(1)
        shot("27-carte-activee")
        element("activation.close").tap()
        XCTAssertTrue(app.staticTexts["CARTE-2210"].firstMatch.waitForExistence(timeout: 5))
        sleep(1)
        shot("28-carte-dans-mes-badges")
    }

    func testAccountAreas() {
        launch()
        waitTabs(5)
        tab(2)
        sleep(2)
        shot("07-historique")
        element("sessions.mode").buttons.element(boundBy: 1).tap()
        sleep(2)
        shot("08-statistiques")
        tab(3)
        XCTAssertTrue(element("badges.scan").waitForExistence(timeout: 5))
        element("badges.scan").tap()
        XCTAssertTrue(element("nfc.simulate.own").waitForExistence(timeout: 5))
        element("nfc.simulate.own").tap()
        XCTAssertTrue(element("nfc.result").waitForExistence(timeout: 5))
        sleep(1)
        shot("20-nfc-carte-reconnue")
        element("badges.scan").tap()
        XCTAssertTrue(element("nfc.simulate.unknown").waitForExistence(timeout: 5))
        element("nfc.simulate.unknown").tap()
        sleep(1)
        shot("21-nfc-carte-inconnue")
        app.swipeUp()
        sleep(1)
        let card = app.staticTexts["CARTE-1042"].firstMatch
        XCTAssertTrue(card.waitForExistence(timeout: 5))
        card.tap()
        sleep(2)
        shot("22-badge-apple-wallet")
        app.swipeUp()
        sleep(1)
        shot("23-badge-apple-wallet-bouton")
        tab(4)
        sleep(1)
        shot("30-compte")
        element("account.vehicles").tap()
        sleep(2)
        shot("31-vehicules")
        element("vehicles.add").tap()
        sleep(1)
        shot("32-ajouter-vehicule")
        back()
        back()
        element("account.invoices").tap()
        sleep(2)
        shot("33-factures")
        back()
        element("account.notifications").tap()
        sleep(2)
        element("notifications.preferences").tap()
        sleep(2)
        shot("34-preferences-notifications")
        back()
        back()
        scrollTo("account.about").tap()
        sleep(1)
        shot("35-a-propos")
    }
}
