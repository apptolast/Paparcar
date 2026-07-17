package io.apptolast.paparcar.presentation.home

import io.apptolast.paparcar.domain.error.PaparcarError

/**
 * Efectos de un solo uso para la pantalla Home.
 * Estos efectos se consumen una sola vez (navegación, snackbar, etc.).
 */
sealed class HomeEffect {
    data class ShowError(val error: PaparcarError) : HomeEffect()
    data object SpotReported : HomeEffect()
    data object TestSpotSent : HomeEffect()
    data object RequestLocationPermission : HomeEffect()
    data object SpotSignalSent : HomeEffect()
    /** Move the map camera to (lat, lon). Used by the zone-chip tap flow. */
    data class MoveCameraTo(val lat: Double, val lon: Double) : HomeEffect()
    data object ZoneSaved : HomeEffect()
    /** Auto-detection just re-enabled from the Home banner — confirm with a snackbar. [DET-TOGGLE-001] */
    data object DetectionEnabled : HomeEffect()
    /** Auto-detection enabled but permissions still missing — open the permissions screen at [focus]
     *  ("producer" or "all") so one banner tap brings detection fully online. [DET-TOGGLE-001] */
    data class OpenDetectionPermissions(val focus: String) : HomeEffect()
    /** Detection just dropped from a working state into a stopped one (turned off in Settings, or a
     *  producer/core permission revoked) — show a snackbar with one-tap re-activation. [DET-TOGGLE-002] */
    data object DetectionStopped : HomeEffect()
}