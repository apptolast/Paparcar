package com.rndeveloper.paparcar.presentation.home

import com.rndeveloper.paparcar.domain.error.PaparcarError
import com.rndeveloper.paparcar.domain.model.SpotVoteOutcome

/**
 * Efectos de un solo uso para la pantalla Home.
 * Estos efectos se consumen una sola vez (navegación, snackbar, etc.).
 */
sealed class HomeEffect {
    data class ShowError(val error: PaparcarError) : HomeEffect()
    data object SpotReported : HomeEffect()
    data object TestSpotSent : HomeEffect()
    data object RequestLocationPermission : HomeEffect()
    /** A community vote landed. Carries what it DID, so the confirmation can say it rather than
     *  thanking the user for nothing. [SPOT-COMMUNITY-VOTES-NEED-A-CONSEQUENCE-001] */
    data class SpotSignalSent(val outcome: SpotVoteOutcome) : HomeEffect()
    /** Move the map camera to (lat, lon), framed for [frame]'s PURPOSE. The VM says what the
     *  camera is for; the screen owns the zoom numbers. [UI-ZONE-MANAGE-001] */
    data class MoveCameraTo(
        val lat: Double,
        val lon: Double,
        val frame: CameraFrame = CameraFrame.Navigate,
    ) : HomeEffect()
    data object ZoneSaved : HomeEffect()
    /** Auto-detection just re-enabled from the Home banner — confirm with a snackbar. [DET-TOGGLE-001] */
    data object DetectionEnabled : HomeEffect()
    /** Auto-detection enabled but permissions still missing — open the permissions screen at [focus]
     *  ("producer" or "all") so one banner tap brings detection fully online. [DET-TOGGLE-001] */
    data class OpenDetectionPermissions(val focus: String) : HomeEffect()
    /** Detection just dropped from a working state into a stopped one (turned off in Settings, or a
     *  producer/core permission revoked) — show a snackbar with one-tap re-activation. [DET-TOGGLE-002] */
    data object DetectionStopped : HomeEffect()

    /**
     * The user stopped the TRIP being followed right now ("Parar detección"). Deliberately not
     * [DetectionStopped]: the feature stays on and there is nothing to re-activate — this confirms
     * that the silence was asked for, and says how to come back. [DET-STOP-BUTTON-001]
     */
    data object TripDetectionStopped : HomeEffect()

    /** "Activate now" on the battery nudge — the screen launches the system battery-optimization
     *  exemption request (and, on aggressive OEMs, the manufacturer's foreground/autostart settings
     *  page). [DET-BATTERY-EXEMPTION-NUDGE-001] */
    data object RequestBatteryOptimizationExemption : HomeEffect()
}

/**
 * Why the camera is being moved — the screen turns each purpose into a zoom.
 *
 *  - [Navigate]: go look at a place (zone chip tap, recentre on GPS, search result).
 *  - [ZoneEditing]: place or resize a zone — the whole circle must fit on screen, and a
 *    zone's radius reaches 500 m, so the navigation zoom would crop it. [UI-ZONE-MANAGE-001]
 */
enum class CameraFrame { Navigate, ZoneEditing }