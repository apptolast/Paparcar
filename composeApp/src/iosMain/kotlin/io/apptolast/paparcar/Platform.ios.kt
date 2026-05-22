@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package io.apptolast.paparcar

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
