package io.apptolast.paparcar.presentation.util

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
