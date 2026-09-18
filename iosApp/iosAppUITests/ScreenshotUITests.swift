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

    /// ⚠️ Tapped by COORDINATE, and it is worth knowing why before someone calls it sloppy: the
    /// shared UI carries no `testTag` anywhere (checked: zero in commonMain), so there is no
    /// accessibility identifier to query, and matching the visible label would break in eight of
    /// the nine languages we shoot. The bar is a fixed three-item row, so thirds are stable — but
    /// if a fourth destination ever lands, this is the line that silently shoots the wrong tab.
    /// Adding `Modifier.testTag` to the three items and querying it here is the real fix.
    @MainActor
    private func select(_ tab: Tab, in app: XCUIApplication) {
        guard tab != .home else { return }   // Home is the start destination: no tap needed
        let x = (CGFloat(tab.rawValue) + 0.5) / 3.0
        app.coordinate(withNormalizedOffset: CGVector(dx: x, dy: 0.965)).tap()
        Thread.sleep(forTimeInterval: 1.5)
    }
}
