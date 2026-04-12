package io.apptolast.paparcar.domain.usecase.spot

import io.apptolast.paparcar.domain.repository.SpotRepository

/**
 * Sends a community acceptance or rejection signal for a spot.
 *
 * Accepted  → "Still there" — the spot was still free when the user arrived.
 * Rejected  → "Gone"        — the spot was already taken.
 *
 * Each call atomically increments the corresponding counter in Firestore.
 * The real-time listener in [SpotRepository.observeNearbySpots] propagates
 * the updated counts to the local Room cache, causing [Spot.confidence] to
 * be recalculated with [decayedConfidence] on the next emission.
 */
class SendSpotSignalUseCase(
    private val spotRepository: SpotRepository,
) {
    suspend operator fun invoke(spotId: String, accepted: Boolean): Result<Unit> =
        spotRepository.sendSpotSignal(spotId, accepted)
}
