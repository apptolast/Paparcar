package io.apptolast.paparcar

/**
 * Whether the app is running in a debug build.
 *
 * Android: resolves to [BuildConfig.DEBUG] at compile time.
 * iOS:     always `false`; debug/release distinction is handled by the Xcode scheme.
 */
expect val isDebugBuild: Boolean
