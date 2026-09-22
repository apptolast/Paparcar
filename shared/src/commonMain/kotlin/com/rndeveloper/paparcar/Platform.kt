package com.rndeveloper.paparcar

/**
 * Whether the app is running in a debug build.
 *
 * Android: resolves to [BuildConfig.DEBUG] at compile time.
 * iOS:     resolves to `kotlin.native.Platform.isDebugBinary`, which follows
 *          the Xcode build configuration (Debug → true, Release → false).
 */
expect val isDebugBuild: Boolean

/** Human-readable version string (e.g. "1.2.3") sourced from the platform build system. */
expect val appVersion: String

/**
 * Whether "battery optimization exemption" (Android Doze / OEM killers) is a concept on this
 * platform. Android only — iOS has no equivalent user-grantable exemption, so the Settings
 * "unrestricted battery" improvement row is hidden there. [SETTINGS-REMODEL-001]
 */
expect val isBatteryOptimizationRelevant: Boolean

/**
 * Whether pairing the car's Bluetooth can change how parking is detected on this platform.
 *
 * Android: true — the deterministic BT lane exists (ACL connect/disconnect of the paired MAC),
 * and pairing is the single jump to the Automatic tier.
 * iOS: false — no third-party API delivers Classic BT (A2DP/HFP) connect/disconnect events, so
 * the BT lane is structurally impossible and the resolver always runs the Coordinator. Every
 * surface that sells the pairing (permission row, Settings improvement row) hides behind this
 * flag so iOS never promises a tier it cannot reach.
 * [IOS-A-BT-LANE-NEEDS-A-REAL-SIGNAL-001]
 *
 * ⚠️ Must agree with `DeviceCapabilities.supportsBtStrategy` (per-platform DI module): this val
 * gates what the UI paints, the capability gates what the domain promises. They are the same
 * platform fact written where each consumer can reach it.
 */
expect val isBtStrategyRelevant: Boolean

/**
 * Whether this platform can link a car's Bluetooth by READING the live audio route (the iOS
 * sensor), rather than by the ACL lane Android uses. True only on iOS: it is where no bonded-device
 * list exists, so the "link this car" row on the vehicle page reads `AVAudioSession.currentRoute`
 * with the car connected instead of showing a picker. On Android the deterministic BT lane already
 * owns pairing (`isBtStrategyRelevant`), so this row never appears.
 * [IOS-DET-AUDIO-ROUTE-PROVES-WHOSE-CAR-001]
 */
expect val isAudioRouteLinkRelevant: Boolean

/**
 * Tankerkönig API key for Germany's fuel prices. [FUEL-GERMANY-THE-FEED-ANSWERS-WHOLE-001]
 *
 * Personal and gitignored (`local.properties → GermanyFuelStationKey` — the repo is public), so a
 * build without one is a legitimate build, and **blank means Germany simply has no feed**: the
 * source does not claim the country and the map degrades to OSM's honest floor. It is never an
 * error state.
 *
 * Android: `BuildConfig` → [AppBuildInfo], set in `Application.onCreate` like every build fact.
 * iOS: blank until iOS ships and brings its own configuration (iOS work is parked, 07-09-2026).
 */
expect val germanyFuelFeedKey: String
