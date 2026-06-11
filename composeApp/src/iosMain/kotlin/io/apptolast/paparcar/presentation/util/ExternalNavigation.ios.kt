@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.apptolast.paparcar.presentation.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/**
 * iOS impl: opens Apple Maps with turn-by-turn directions to the destination.
 *
 * Apple Maps URL scheme (`http://maps.apple.com/`) redirects to the native app when
 * installed; otherwise falls back to the web map. `dirflg` picks the travel mode:
 *  - `w` = walking
 *  - `d` = driving (default)
 *
 * No `Info.plist` allow-list is needed for `http://` URLs.
 */
@Composable
actual fun rememberOpenExternalNavigation(): (lat: Double, lon: Double, walking: Boolean) -> Unit {
    return remember {
        { lat, lon, walking ->
            val mode = if (walking) "w" else "d"
            val urlString = "http://maps.apple.com/?daddr=$lat,$lon&dirflg=$mode"
            NSURL.URLWithString(urlString)?.let { url ->
                UIApplication.sharedApplication.openURL(
                    url = url,
                    options = emptyMap<Any?, Any?>(),
                    completionHandler = null,
                )
            }
        }
    }
}
