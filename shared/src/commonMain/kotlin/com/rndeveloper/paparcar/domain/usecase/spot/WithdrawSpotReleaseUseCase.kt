@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.spot

import com.rndeveloper.paparcar.domain.model.SpotTtlPolicy
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.repository.SpotRepository
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlin.time.Clock

/** [DET-COORDINATOR-NO-OPTIONAL-DEPS-001] The seam, for the same reason as its twin. */
fun interface WithdrawSpotRelease {
    /** @see WithdrawSpotReleaseUseCase.invoke */
    suspend operator fun invoke(): Boolean
}

/**
 * [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001] Takes back a plaza published on a boarding that did not
 * become a departure — the losing half of [NominateSpotReleaseUseCase], and the exact mirror of
 * `RetractDeducedDepartureUseCase` one rung lower down the ladder of certainty.
 *
 * **Why bother, when the TTL is only four minutes.** Because four minutes is long enough for
 * somebody to drive there. The TTL is the floor under a withdrawal that can always fail (process
 * death, no network, an OEM freeze); when we DO know the car never left, saying so in seconds is
 * the difference between a stranger reading "retirada" and a stranger circling an occupied street.
 * [SpotStatus.RETRACTED] keeps arriving for [SpotTtlPolicy.RETRACTION_GRACE_MS] precisely so the
 * app can explain itself instead of letting the marker vanish mid-approach.
 *
 * **Why the age bound.** `retractSpot` is an UPDATE, and every branch of the Firestore rule
 * dereferences `resource.data` — a document the expiry already swept answers PERMISSION_DENIED,
 * not NOT_FOUND. Past the releasing TTL there is nothing out there, so it says so once instead of
 * writing into the void. That is [DET-RETRACT-DENIED-FOREVER-001], measured: 256 denials in five
 * days for one spot that had never been published.
 *
 * **⛔ Why the stamp is NOT cleared.** It answers two questions, and only one of them is over when
 * the spot comes down. *"Is there a document out there to withdraw"* is finished — and the age
 * bound above is what keeps the next call from writing into the void, so clearing is not needed for
 * that. *"Has this parking session already spent its one nomination"* is NOT finished: the car is
 * still in the same space, and a Bluetooth link that drops and returns would otherwise re-announce
 * it. The stamp stays until the departure promotes the document, and a new park gets a new session
 * id that no stale stamp can match. Same reasoning, and the same trap, as the
 * `provisionalDepartureAtMs` marker in `RetractDeducedDepartureUseCase`.
 */
class WithdrawSpotReleaseUseCase(
    private val spotRepository: SpotRepository,
    private val appPreferences: AppPreferences,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : WithdrawSpotRelease {

    /** @return true when a spot was actually withdrawn. Safe to call with nothing pending. */
    override suspend operator fun invoke(): Boolean {
        val pending = appPreferences.pendingSpotRelease() ?: return false
        val now = nowMs()
        if (!pending.isLive(now, SpotTtlPolicy.RELEASING_SPOT_TTL_MS)) {
            PaparcarLogger.d(
                TAG,
                "nothing to withdraw for ${pending.spotId.take(8)} — its releasing window ran out " +
                    "${now - pending.nominatedAtMs - SpotTtlPolicy.RELEASING_SPOT_TTL_MS}ms ago " +
                    "[DET-RETRACT-DENIED-FOREVER-001]",
            )
            return false
        }
        return spotRepository.retractSpot(pending.spotId)
            .onFailure {
                // Already swept, or offline. The short TTL is the floor precisely because this
                // call can fail; nothing else to do here.
                PaparcarLogger.w(TAG, "could not withdraw ${pending.spotId.take(8)} — the TTL still bounds it", it)
            }
            .onSuccess {
                PaparcarLogger.d(
                    TAG,
                    "withdrew the releasing plaza ${pending.spotId.take(8)} — the boarding never " +
                        "became a departure (${now - pending.nominatedAtMs}ms live)",
                )
            }
            .isSuccess
    }

    private companion object {
        const val TAG = "PARKDIAG/SpotRelease"
    }
}
