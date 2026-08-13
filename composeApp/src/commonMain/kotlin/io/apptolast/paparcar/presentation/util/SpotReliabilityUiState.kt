package io.apptolast.paparcar.presentation.util

import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.SpotType

/**
 * Presentation-layer reliability level for a community [Spot] — the freshness RAMP, a continuous
 * scale and nothing else:
 *  - [HIGH]   → green  (strong signal — includes manual reports: an eyewitness is confidence 1.0)
 *  - [MEDIUM] → amber  (weak signal)
 *  - [LOW]    → red    (stale / very low confidence)
 *
 * MANUAL is gone from this enum on purpose: it was PROVENANCE (a category) living inside a
 * freshness scale, which cost manual reports their TTL ring and their freshness colour. Where a
 * spot comes from is told by a GLYPH — the person badge, via [Spot.isManualReport] — never by a
 * colour of its own. [UI-COLOR-DOCTRINE-001 F5]
 */
enum class SpotReliabilityUiState {
    HIGH,
    MEDIUM,
    LOW,
}

/** Maps a [Spot] domain object to its [SpotReliabilityUiState] presentation level. Manual reports
 *  carry `MANUAL_REPORT_CONFIDENCE = 1f`, so they land on [HIGH] like the eyewitness sightings
 *  they are. */
fun Spot.toReliabilityUiState(): SpotReliabilityUiState = when {
    confidence >= HIGH_CONFIDENCE_THRESHOLD     -> SpotReliabilityUiState.HIGH
    confidence >= MEDIUM_CONFIDENCE_THRESHOLD   -> SpotReliabilityUiState.MEDIUM
    else                                        -> SpotReliabilityUiState.LOW
}

/** Provenance flag feeding the person badge on the spot puck/pin. */
val Spot.isManualReport: Boolean get() = type == SpotType.MANUAL_REPORT

private const val HIGH_CONFIDENCE_THRESHOLD = 0.75f
private const val MEDIUM_CONFIDENCE_THRESHOLD = 0.55f
