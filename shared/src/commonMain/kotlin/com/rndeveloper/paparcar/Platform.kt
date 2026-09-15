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
