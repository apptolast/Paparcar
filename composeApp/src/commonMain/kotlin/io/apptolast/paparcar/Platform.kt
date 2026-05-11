package io.apptolast.paparcar

/**
 * Whether the app is running in a debug build.
 *
 * Android: resolves to [BuildConfig.DEBUG] at compile time.
 * iOS:     resolves to `kotlin.native.Platform.isDebugBinary`, which follows
 *          the Xcode build configuration (Debug → true, Release → false).
 */
expect val isDebugBuild: Boolean
