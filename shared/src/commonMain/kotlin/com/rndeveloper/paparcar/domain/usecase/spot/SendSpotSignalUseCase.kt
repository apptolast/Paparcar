package com.rndeveloper.paparcar.domain.usecase.spot

import com.rndeveloper.paparcar.domain.model.SpotVoteOutcome
import com.rndeveloper.paparcar.domain.model.SpotVotePolicy
import com.rndeveloper.paparcar.domain.repository.SpotRepository

/**
 * Sends a community acceptance or rejection signal for a spot, **and applies its consequence**.
 *
 * Accepted  → "Still there" — the spot was still free when the user arrived.
 * Rejected  → "Gone"        — the spot was already taken.
 *
 * [SPOT-COMMUNITY-VOTES-NEED-A-CONSEQUENCE-001] Both used to do nothing but increment a counter.
 * The counters fed [com.rndeveloper.paparcar.domain.model.Spot.confidence], and once the freshness
 * ramp stopped reading `confidence` the votes reached no pixel at all — a driver could stand at an
 * occupied space, say so, and the next driver was still sent there.
 *
 * Now, for a voter close enough to be a witness ([SpotVotePolicy]):
 *  - **Gone** withdraws the spot for everyone, through [SpotStatus.RETRACTED] rather than a tint —
 *    "occupied" is a different statement from "less fresh", and retraction is the path that already
 *    knows how to explain itself instead of vanishing a marker mid-approach.
 *  - **Still there** restarts the spot's age, which is exactly what the witness just observed.
 *
 * The counters are still incremented in both cases: they are the raw record, and the vote ratio
 * still feeds `communityConfidence()`. What changed is that the vote no longer *only* does that.
 *
 * A vote cast too far away (or with no location fix) writes **nothing at all** — not even the
 * counter. Without the proximity gate a single tap could withdraw a spot from anywhere, which is
 * exactly what makes trusting one voice safe here.
 */
class SendSpotSignalUseCase(
    private val spotRepository: SpotRepository,
) {
    /**
     * @param distanceMeters how far the voter is from the spot, or null when there is no fix.
     * @return the outcome that was applied, so the UI can say what happened.
     */
    suspend operator fun invoke(
        spotId: String,
        accepted: Boolean,
        distanceMeters: Double?,
    ): Result<SpotVoteOutcome> {
        val outcome = SpotVotePolicy.outcomeOf(accepted = accepted, distanceMeters = distanceMeters)
        if (outcome == SpotVoteOutcome.IGNORED_TOO_FAR) return Result.success(outcome)

        return spotRepository.sendSpotSignal(spotId, accepted)
            .mapCatching {
                when (outcome) {
                    SpotVoteOutcome.RETRACT -> spotRepository.retractSpot(spotId).getOrThrow()
                    SpotVoteOutcome.REFRESH -> spotRepository.refreshSpot(spotId).getOrThrow()
                    SpotVoteOutcome.IGNORED_TOO_FAR -> Unit // unreachable, returned above
                }
                outcome
            }
    }
}
