package io.apptolast.paparcar.presentation.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * iOS impl: placeholder no-op until the iOS launcher (Apple Maps `maps://`
 * URL scheme via `UIApplication.openURL`) is wired in a future iOS pass.
 * Calling the returned lambda silently does nothing so feature flags and
 * shared composables can compile and run on the iOS target.
 */
@Composable
actual fun rememberOpenExternalNavigation(): (lat: Double, lon: Double, walking: Boolean) -> Unit {
    return remember { { _, _, _ -> } }
}
