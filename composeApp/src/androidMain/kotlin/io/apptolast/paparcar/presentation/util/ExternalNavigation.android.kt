package io.apptolast.paparcar.presentation.util

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import io.apptolast.paparcar.domain.util.PaparcarLogger

/**
 * Android impl: launches the Google Maps `google.navigation:` intent in
 * turn-by-turn mode, falling back to a generic `geo:` URI when Google Maps
 * is not installed so any other maps app on the device can claim the intent.
 *
 * Walking vs driving is selected via the `&mode=` query parameter (`w` / `d`),
 * which the Maps app honours when constructing the route.
 */
@Composable
actual fun rememberOpenExternalNavigation(): (lat: Double, lon: Double, walking: Boolean) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { lat, lon, walking ->
            val mode = if (walking) "w" else "d"
            val gmaps = Intent(
                Intent.ACTION_VIEW,
                "google.navigation:q=$lat,$lon&mode=$mode".toUri(),
            ).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(gmaps)
            } catch (_: ActivityNotFoundException) {
                val fallback = Intent(
                    Intent.ACTION_VIEW,
                    "geo:$lat,$lon?q=$lat,$lon".toUri(),
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                runCatching { context.startActivity(fallback) }.onFailure { e ->
                    PaparcarLogger.w(TAG, "No external maps app available to handle navigation intent", e)
                }
            }
        }
    }
}

private const val TAG = "ExternalNavigation"
