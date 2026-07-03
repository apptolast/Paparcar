package io.apptolast.paparcar

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
