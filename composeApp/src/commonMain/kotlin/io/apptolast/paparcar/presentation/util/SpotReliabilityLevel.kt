package io.apptolast.paparcar.presentation.util

import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.SpotType

/**
 * Presentation-layer classification of a community-reported [Spot] for marker rendering.
 *
 * Maps the domain-level `Spot.confidence` (Float 0..1) and `Spot.type` to one of four
 * visual tiers used by `FreeSpotMarker`. Kept separate from
 * [ParkingReliabilityLevel] because they answer different questions:
 *
 *  - [ParkingReliabilityLevel]: "how sure are we the user actually parked?"
 *    (UserParking.detectionReliability — Confirmed / High / Auto)
 *  - [SpotReliabilityLevel]: "how trustworthy is this free-spot listing for other users?"
 *    (Spot.confidence + Spot.type — HIGH / MEDIUM / LOW / MANUAL)
 *
 * Thresholds mirror the inline values in
 * [io.apptolast.paparcar.ui.components.PaparcarMapView] so the colour tier is
 * computed in one place.
 *
 * Introduced for [MARKERS-001] alongside the new Composable marker set
 * (`FreeSpotMarker` etc.).
 */
enum class SpotReliabilityLevel {
    /** Auto-detected with strong signal (confidence ≥ [HIGH_CONFIDENCE_THRESHOLD]). Green. */
    HIGH,
    /** Auto-detected, medium signal ([MEDIUM_CONFIDENCE_THRESHOLD] ≤ confidence). Amber. */
    MEDIUM,
    /** Auto-detected, weak signal (below [MEDIUM_CONFIDENCE_THRESHOLD]). Red. */
    LOW,
    /** Manually reported by a user — bypasses confidence ladder, painted blue. */
    MANUAL;

    companion object {
        const val HIGH_CONFIDENCE_THRESHOLD   = 0.75f
        const val MEDIUM_CONFIDENCE_THRESHOLD = 0.55f
    }
}

fun Spot.toSpotReliabilityLevel(): SpotReliabilityLevel = when {
    type == SpotType.MANUAL_REPORT                                        -> SpotReliabilityLevel.MANUAL
    confidence >= SpotReliabilityLevel.HIGH_CONFIDENCE_THRESHOLD          -> SpotReliabilityLevel.HIGH
    confidence >= SpotReliabilityLevel.MEDIUM_CONFIDENCE_THRESHOLD        -> SpotReliabilityLevel.MEDIUM
    else                                                                  -> SpotReliabilityLevel.LOW
}
