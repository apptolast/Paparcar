@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.parking

import com.rndeveloper.paparcar.domain.diagnostics.DetectionEvent
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEventLogger
import com.rndeveloper.paparcar.domain.model.SpotTtlPolicy
import com.rndeveloper.paparcar.domain.repository.SpotRepository
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.flow.firstOrNull
import kotlin.time.Clock

/**
 * [DET-HANDOFF-NOT-MANUAL-001 §B.3] Withdraws the community spot of a DEDUCED departure that the
 * trip itself went on to refute — the losing half of the pair whose winning half is
 * [FinalizeDeducedDepartureUseCase].
 *
 * A deduced departure publishes immediately and gives up nothing: the spot goes out provisionally
 * (short TTL) while the session and its geofence stay put. Then the trip it was deduced from ends,
 * and it has said one of two things:
 *
 * - **it measured a drive** → [FinalizeDeducedDepartureUseCase] promotes the spot and releases the
 *   car. The deduction was right.
 * - **it measured no drive at all** → this. The phone moved and the car did not (field 2026-08-19:
 *   a bicycle ride). Nothing was taken from the user, so there is nothing to restore — but a
 *   stranger is still being offered a space that was never freed, and that is ours to take back.
 *
 * **Why retract instead of letting the TTL run out.** The TTL is the floor, not the plan: it bounds
 * the damage when nothing else works (process death, no network, OEM kill). When we DO know, we
 * should say so in seconds rather than leave the ghost standing for the rest of its twelve minutes
 * — a driver who reaches it finds nothing and learns the app lies.
 *
 * **Why a state and not a delete.** See [com.rndeveloper.paparcar.domain.model.SpotStatus]: a deleted
 * document just stops arriving, taking the explanation with it.
 *
 * **What is deliberately NOT undone:** the `provisionalDepartureAtMs` marker stays. It is also the
 * "this deduction already spent its one publication" guard, and clearing it would let the safety
 * net re-deduce the same departure on its next 15-minute pass and publish the same wrong guess
 * again — the blinking ghost §B closed. Keeping it also means a drive measured LATER still
 * promotes the spot and releases the car through the ordinary path: a retraction withdraws a
 * report, it does not close the case.
 *
 * **[DET-RETRACT-DENIED-FOREVER-001] And because the marker never clears, the attempt has to bound
 * itself.** The marker answers "is a deduction pending" — which is what
 * [FinalizeDeducedDepartureUseCase] needs — but this use case was reading it as "is there a spot out
 * there", and those stopped being the same fact the moment the spot's TTL expired (or the moment a
 * departure was too stale to publish one at all). Past [SpotTtlPolicy.PROVISIONAL_SPOT_TTL_MS] there
 * is nothing to take back, so it says so once per session end instead of writing to a document that
 * is not there.
 */
class RetractDeducedDepartureUseCase(
    private val userParkingRepository: UserParkingRepository,
    private val spotRepository: SpotRepository,
    private val detectionEventLogger: DetectionEventLogger? = null,
    // [DET-RETRACT-DENIED-FOREVER-001] Injected so the TTL bound below is reachable from a test at
    // all — same shape as `RunDepartureCheckUseCase`. Reading the wall clock inline would have made
    // "the provisional window has elapsed" untestable, which is how it went unbounded to begin with.
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    /**
     * @return the number of spots withdrawn.
     *
     * Retracts EVERY pending deduction, not one keyed to a vehicle: a trip that never reached
     * driving speed usually never locked a vehicle id either, so there is nothing to key on. In
     * practice there is at most one pending deduction (it takes an active session and a dispatched
     * departure to create one), and if there were two, withdrawing both is the safe direction —
     * a spot that comes back costs nobody anything, a phantom costs a stranger a wasted drive.
     *
     * Safe to call on every session end: with no pending deduction it reads one list and returns 0.
     */
    suspend operator fun invoke(): Int {
        val pending = runCatching {
            userParkingRepository.observeActiveSessions().firstOrNull().orEmpty()
        }.getOrElse { e ->
            PaparcarLogger.w(TAG, "could not read active sessions — nothing retracted", e)
            emptyList()
        }.filter { it.provisionalDepartureAtMs != null }

        if (pending.isEmpty()) return 0

        val now = nowMs()
        var retracted = 0
        pending.forEach { session ->
            val deducedAtMs = session.provisionalDepartureAtMs ?: return@forEach
            // A private zone never published anything, so there is nothing to take back. The
            // marker still stands: the car is still the user's to release when a drive proves it.
            if (session.privateZoneId != null) return@forEach

            // [DET-RETRACT-DENIED-FOREVER-001] Past the provisional TTL there is no document left to
            // withdraw — the expiry already did it, which is what the failure branch below has always
            // claimed. Without this bound the attempt repeats on EVERY session end for as long as the
            // marker stands, and the marker is deliberately never cleared (see the KDoc). Worse, the
            // repeat is not a harmless no-op: `retractSpot` is an UPDATE, and every branch of the
            // `allow update` rule dereferences `resource.data`, which does not exist for a deleted
            // document — so Firestore answers PERMISSION_DENIED, not NOT_FOUND, and the log fills with
            // what reads like a rules bug. Measured on the Oppo (`diagnostics/2026-08-26/`): 256
            // denials across five days, all for one spot that had never been published at all.
            val provisionalAgeMs = now - deducedAtMs
            if (provisionalAgeMs > SpotTtlPolicy.PROVISIONAL_SPOT_TTL_MS) {
                PaparcarLogger.d(
                    TAG,
                    "nothing to withdraw for spot=${session.id.take(8)} — its provisional TTL ran out " +
                        "${provisionalAgeMs - SpotTtlPolicy.PROVISIONAL_SPOT_TTL_MS}ms ago " +
                        "[DET-RETRACT-DENIED-FOREVER-001]",
                )
                return@forEach
            }

            // The spot carries the session's own id, so the withdrawal lands on the very document
            // the deduction published. Idempotent by construction — the same two fields, again.
            spotRepository.retractSpot(session.id)
                .onFailure { e ->
                    // Already swept away by the expiry cleanup, or offline. The short TTL is the
                    // floor precisely because this call can fail; nothing else to do here.
                    PaparcarLogger.w(TAG, "retract failed for spot=${session.id.take(8)} — the short TTL still bounds it", e)
                }
                .onSuccess {
                    retracted++
                    PaparcarLogger.d(
                        TAG,
                        "withdrew the provisional spot ${session.id.take(8)} — the trip it was deduced " +
                            "from ended without ever measuring a drive (${now - deducedAtMs}ms live) " +
                            "[DET-HANDOFF-NOT-MANUAL-001 §B.3]",
                    )
                    detectionEventLogger?.log(
                        DetectionEvent.SpotRetracted(
                            sessionId = session.geofenceId ?: session.id,
                            timestampMs = now,
                            sessionAgeMs = now - deducedAtMs,
                            location = session.location,
                        )
                    )
                }
        }
        return retracted
    }

    private companion object {
        const val TAG = "PARKDIAG/RetractDeducedDeparture"
    }
}
