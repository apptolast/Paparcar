package io.apptolast.paparcar.presentation.map

/**
 * Drives a one-shot camera animation in PaparcarMapView.
 *
 * [token] is incremented on each request so that [LaunchedEffect] fires even when
 * the same coordinates are targeted twice in a row.
 */
data class CameraTarget(
    val lat: Double,
    val lon: Double,
    val zoom: Float = 17f,
    val token: Int = 0,
    /** When set, animate to fit both points with [paddingDp] margin instead of single-point zoom. */
    val boundsLat2: Double? = null,
    val boundsLon2: Double? = null,
    val paddingDp: Int = 80,
)