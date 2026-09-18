package com.rndeveloper.paparcar.presentation.map

/**
 * Drives a one-shot camera animation in PaparcarMapView.
 *
 * [token] is incremented on each request so that [LaunchedEffect] fires even when
 * the same coordinates are targeted twice in a row.
 */
data class CameraTarget(
    val lat: Double,
    val lon: Double,
    /** Null means preserve whatever zoom the user has at the moment of the tap. */
    val zoom: Float? = null,
    /**
     * Qué es [zoom]: un SUELO o un encuadre. Por defecto un suelo, que es lo que quiere casi toda
     * animación — «acércame al menos así», nunca «aléjame hasta aquí».
     * [MAP-A-CAMERA-MOVE-MUST-NOT-COST-A-PINCH-001]
     */
    val zoomIntent: ZoomIntent = ZoomIntent.AtLeast,
    val token: Int = 0,
    /** When set, animate to fit both points with [paddingDp] margin instead of single-point zoom. */
    val boundsLat2: Double? = null,
    val boundsLon2: Double? = null,
    val paddingDp: Int = 80,
)