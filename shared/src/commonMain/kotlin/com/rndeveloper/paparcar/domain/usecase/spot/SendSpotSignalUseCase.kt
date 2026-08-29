package com.rndeveloper.paparcar.domain.usecase.spot

import com.rndeveloper.paparcar.domain.repository.SpotRepository

/**
 * Sends a community acceptance or rejection signal for a spot.
 *
 * Accepted  → "Still there" — the spot was still free when the user arrived.
 * Rejected  → "Gone"        — the spot was already taken.
 *
 * Each call atomically increments the corresponding counter in Firestore.
 * The real-time listener in [SpotRepository.observeNearbySpots] propagates
 * the updated counts to the local Room cache, causing [Spot.confidence] to
 * be recalculated with `communityConfidence` on the next emission.
 *
 * ⚠️ [SPOT-FRESHNESS-IS-AGE-NOT-A-COUNTDOWN-001] These votes no longer reach any pixel. The
 * freshness ramp the UI colours itself from is now derived from a spot's AGE, and `confidence` was
 * its only consumer. The signal is still collected and still correct — surfacing it again (a
 * rejected spot should arguably be withdrawn, not merely re-tinted) is its own ticket:
 * `docs/backlog/SPOT-COMMUNITY-VOTES-NEED-A-CONSEQUENCE-001.md`.
 */
class SendSpotSignalUseCase(
    private val spotRepository: SpotRepository,
) {
    suspend operator fun invoke(spotId: String, accepted: Boolean): Result<Unit> =
        spotRepository.sendSpotSignal(spotId, accepted)
}
