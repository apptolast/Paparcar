package io.apptolast.paparcar.presentation.map

/**
 * Drives a one-shot camera animation in PlatformMap.
 *
 * [token] is incremented on each request so that [LaunchedEffect] fires even when
 * the same coordinates are targeted twice in a row.
 */
data class CameraTarget(
    val lat: Double,
    val lon: Double,
    val zoom: Float = 17f,
    val token: Int = 0,
)