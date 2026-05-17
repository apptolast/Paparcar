package io.apptolast.paparcar.ui.components

import io.apptolast.paparcar.presentation.util.SpotReliabilityUiState

/**
 * UI state for a parking spot row. Aggregates the precomputed values that the
 * spot item composable needs to render — distance, freshness, reliability,
 * en-route count — so the composable stays presentation-only. ViewModels map
 * `domain.model.Spot` + user location to this class before handing it off.
 *
 * Naming: `*UiState` matches the convention already in use elsewhere
 * (`SpotReliabilityUiState`) and disambiguates this from the `Spot` domain
 * model that lives under `domain/model/`.
 *
 * @param id             Unique spot identifier (LazyColumn key).
 * @param displayLocation Human-readable address or POI label.
 * @param distanceMeters  Distance from the current user, or null if unknown.
 * @param reportedAtMs    Epoch-ms when the spot was reported (freshness).
 * @param reliability     Confidence level of the auto-detection.
 * @param enRouteCount    Number of users currently heading to this spot.
 * @param expiresAt       Epoch-ms when this spot expires; 0 = no TTL set.
 */
data class SpotUiState(
    val id: String,
    val displayLocation: String,
    val distanceMeters: Float? = null,
    val reportedAtMs: Long,
    val reliability: SpotReliabilityUiState = SpotReliabilityUiState.HIGH,
    val enRouteCount: Int = 0,
    val expiresAt: Long = 0L,
)
