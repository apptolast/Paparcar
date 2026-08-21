@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.domain.usecase.parking

import io.apptolast.paparcar.domain.diagnostics.DetectionEvent
import io.apptolast.paparcar.domain.diagnostics.DetectionEventLogger
import io.apptolast.paparcar.domain.repository.SpotRepository
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.util.PaparcarLogger
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
 * **Why a state and not a delete.** See [io.apptolast.paparcar.domain.model.SpotStatus]: a deleted
 * document just stops arriving, taking the explanation with it.
 *
 * **What is deliberately NOT undone:** the `provisionalDepartureAtMs` marker stays. It is also the
 * "this deduction already spent its one publication" guard, and clearing it would let the safety
 * net re-deduce the same departure on its next 15-minute pass and publish the same wrong guess
 * again — the blinking ghost §B closed. Keeping it also means a drive measured LATER still
 * promotes the spot and releases the car through the ordinary path: a retraction withdraws a
 * report, it does not close the case.
 */
class RetractDeducedDepartureUseCase(
    private val userParkingRepository: UserParkingRepository,
    private val spotRepository: SpotRepository,
    private val detectionEventLogger: DetectionEventLogger? = null,
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

        val now = Clock.System.now().toEpochMilliseconds()
        var retracted = 0
        pending.forEach { session ->
            val deducedAtMs = session.provisionalDepartureAtMs ?: return@forEach
            // A private zone never published anything, so there is nothing to take back. The
            // marker still stands: the car is still the user's to release when a drive proves it.
            if (session.privateZoneId != null) return@forEach

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
