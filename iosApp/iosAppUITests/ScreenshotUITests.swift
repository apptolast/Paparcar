//
//  ScreenshotUITests.swift
//  iosAppUITests
//
//  Captures the App Store frames. Each frame is a FRESH LAUNCH carrying the scenario it needs —
//  never a sequence of taps through a data state built by the previous frame — because the
//  carousel is shot in nine languages and a run that depends on what the last frame left behind
//  is a run that silently shoots the wrong thing in the eighth one.
//
//  The scenario names come from `ShotPreset`, the same enum `DevMainActivity` reads from
//  `--es shot <key>` for the Play carousel. One definition, two stores.
//  [PLAY-SHOTS-EVERY-LANGUAGE-SHOWS-ITS-OWN-UI-001]
//
//  ⛔ This bundle is NOT in the App Store binary, and neither is what it drives. A `.xctest`
//  bundle is never embedded in the archive's `.ipa`; the Swift hook lives inside `#if DEBUG`; and
//  the Kotlin mock graph reaches the framework only with `-Ppaparcar.screenshots=true`. Three
//  independent exclusions, because the thing they prevent — guideline 2.3.1, hidden functionality
//  — already cost this team a rejection. [IOS-STORE-SHOTS-WITHOUT-SHIPPING-THE-DEMO-CODE-001]
//
//  Run:  cd iosApp && fastlane ios screenshots
//

import XCTest

final class ScreenshotUITests: XCTestCase {

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testCaptureScreenshots() throws {
        // 01 · What the product is for: the car is parked and the app is watching.
        capture("01_Parked", shot: "Parked", tab: .home)

        // 02 · The map with what the community has freed nearby.
        capture("02_Map", shot: "Map", tab: .home)

        // 03 · The garage: the cars, and which of them is being watched how.
        capture("03_Vehicles", shot: "Map", tab: .vehicles)

        // 04 · The figures — only Plus fills this one, hence the Numbers scenario.
        capture("04_Numbers", shot: "Numbers", tab: .vehicles)

        // 05 · Settings, where detection health and the privacy rows live.
        capture("05_Settings", shot: "Settings", tab: .settings)
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private enum Tab: Int {
        case home = 0, vehicles = 1, settings = 2

        /// `Modifier.testTag` de la barra, derivado de la RUTA — ver AppBottomNavigation.
        var identifier: String {
            switch self {
            case .home: return "nav_home"
            case .vehicles: return "nav_vehicles"
            case .settings: return "nav_settings"
            }
        }
    }

    @MainActor
    private func capture(_ name: String, shot: String, tab: Tab) {
        let app = XCUIApplication()
        setupSnapshot(app)
        app.launchArguments += ["-PaparcarScreenshots", "-PaparcarShot", shot]
        app.launch()
        waitForFirstFrame(app)
        select(tab, in: app)
        snapshot(name)
        app.terminate()
    }

    /// Compose renders into a single native view, so there is no element to wait for by identity.
    /// Waiting for the app to be running and then letting the first composition settle is the
    /// honest version of "ready" here; the alternative is a fixed sleep pretending to be a check.
    @MainActor
    private func waitForFirstFrame(_ app: XCUIApplication) {
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 30), "the app never came to the foreground")
        Thread.sleep(forTimeInterval: 3.0)
    }

    /// Cambia de pestaña por IDENTIFICADOR, y falla si no puede.
    ///
    /// ⛔ La primera versión tocaba por COORDENADA porque el proyecto no tenía un solo `testTag`.
    /// El toque no cambiaba de pestaña y el test seguía en VERDE: tres de las cinco capturas eran
    /// Home con el nombre de otra pantalla, y eso se sube a la tienda sin que nadie lo note. Por eso
    /// aquí no hay respaldo silencioso — si el identificador no aparece, la tanda se pone roja.
    /// [IOS-STORE-SHOTS-WITHOUT-SHIPPING-THE-DEMO-CODE-001]
    @MainActor
    private func select(_ tab: Tab, in app: XCUIApplication) {
        guard tab != .home else { return }   // Home es el destino inicial: no hay que tocar nada
        let item = app.descendants(matching: .any).matching(identifier: tab.identifier).firstMatch
        XCTAssertTrue(
            item.waitForExistence(timeout: 10),
            "No aparece '\(tab.identifier)' en la barra. Sin identificador esta captura sería la " +
                "pantalla de inicio con el nombre de otra — que es exactamente el fallo que este " +
                "assert existe para impedir."
        )
        item.tap()
        Thread.sleep(forTimeInterval: 1.5)
    }
}
