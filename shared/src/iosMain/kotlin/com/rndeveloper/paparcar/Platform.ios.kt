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
actual val isBatteryOptimizationRelevant: Boolean = false

// Blank on purpose: no key ⇒ Germany's source does not claim, and the map degrades to OSM's honest
// floor. The key arrives with iOS's own configuration when iOS ships (parked 07-09-2026).
// [FUEL-GERMANY-THE-FEED-ANSWERS-WHOLE-001]
actual val germanyFuelFeedKey: String = ""
