@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package com.rndeveloper.paparcar

import kotlin.native.Platform

/**
 * Resolved from `kotlin.native.Platform.isDebugBinary`, which Kotlin/Native sets
 * automatically based on the binary's debug flag (-g). Xcode's Debug
 * configuration produces a debug binary; Release does not — so this naturally
 * tracks the Xcode scheme without any additional `freeCompilerArgs` wiring.
 */
actual val isDebugBuild: Boolean = Platform.isDebugBinary
actual val appVersion: String =
    platform.Foundation.NSBundle.mainBundle.infoDictionary
        ?.get("CFBundleShortVersionString") as? String ?: "?"

// iOS has no user-grantable background battery exemption → hide that Settings row. [SETTINGS-REMODEL-001]
actual val platformName: String = "ios"

actual val isBatteryOptimizationRelevant: Boolean = false

// No third-party API on iOS delivers the car's Classic BT (A2DP/HFP) connect/disconnect, so the
// deterministic BT lane cannot exist and pairing unlocks nothing → hide every surface that sells
// it. Analysis: docs/backlog/ios-a-bt-lane-needs-a-real-signal-001.md
// [IOS-A-BT-LANE-NEEDS-A-REAL-SIGNAL-001]
actual val isBtStrategyRelevant: Boolean = false

// iOS links the car by reading the live audio route (no bonded-device list exists to pick from).
// [IOS-DET-AUDIO-ROUTE-PROVES-WHOSE-CAR-001]
actual val isAudioRouteLinkRelevant: Boolean = true

// Blank on purpose: no key ⇒ Germany's source does not claim, and the map degrades to OSM's honest
// floor. The key arrives with iOS's own configuration when iOS ships (parked 07-09-2026).
// [FUEL-GERMANY-THE-FEED-ANSWERS-WHOLE-001]
actual val germanyFuelFeedKey: String = ""
