package io.apptolast.paparcar.presentation.util

import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.SpotType

/**
 * Presentation-layer reliability level for a community [Spot].
 *
 * Maps to colour-coded badge variants:
 *  - [HIGH]   → neon green  (strong auto-detection signal)
 *  - [MEDIUM] → amber       (weak auto-detection signal)
 *  - [LOW]    → red         (stale / very low confidence)
 *  - [MANUAL] → blue        (user manually reported)
 */
enum class SpotReliabilityUiState {
    HIGH,
    MEDIUM,
    LOW,
    MANUAL,
}

/** Maps a [Spot] domain object to its [SpotReliabilityUiState] presentation level. */
fun Spot.toReliabilityUiState(): SpotReliabilityUiState = when {
    type == SpotType.MANUAL_REPORT              -> SpotReliabilityUiState.MANUAL
    confidence >= HIGH_CONFIDENCE_THRESHOLD     -> SpotReliabilityUiState.HIGH
    confidence >= MEDIUM_CONFIDENCE_THRESHOLD   -> SpotReliabilityUiState.MEDIUM
    else                                        -> SpotReliabilityUiState.LOW
}

private const val HIGH_CONFIDENCE_THRESHOLD = 0.75f
private const val MEDIUM_CONFIDENCE_THRESHOLD = 0.55f
