@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.spot

import com.rndeveloper.paparcar.domain.detection.SpotReleaseAction
import com.rndeveloper.paparcar.domain.detection.SpotReleaseLane
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEvent
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEventLogger
import com.rndeveloper.paparcar.domain.model.SpotTtlPolicy
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.repository.SpotRepository
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlin.time.Clock

/** [DET-COORDINATOR-NO-OPTIONAL-DEPS-001] The seam, for the same reason as its twin. */
fun interface WithdrawSpotRelease {
    /** @see WithdrawSpotReleaseUseCase.invoke */
    suspend operator fun invoke(lane: String): Boolean
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
    private val detectionEventLogger: DetectionEventLogger,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : WithdrawSpotRelease {

    /**
     * @param lane which lane is asking (`bt` / `coordinator`). Recorded, not obeyed: this call still
     *   withdraws whatever is pending. Making the asker visible is the whole point — the two lanes
     *   are driven by different jobs and a withdrawal that cannot name its author cannot be told
     *   apart from one lane taking down the other's nomination.
     *   [DET-A-RELEASING-SPOT-MUST-SAY-IT-WENT-OUT-001]
     * @return true when a spot was actually withdrawn. Safe to call with nothing pending.
     */
    override suspend operator fun invoke(lane: String): Boolean {
        // Nothing pending is not an event: there was no nomination, so there is no lifecycle to
        // report. Every coordinator job end calls this, and most of them have nothing to say.
        val pending = appPreferences.pendingSpotRelease() ?: return false
        val now = nowMs()
        val liveMs = now - pending.nominatedAtMs
        // ⛔ [DET-A-WITHDRAWAL-MUST-NAME-WHAT-IT-TAKES-BACK-001] Solo el carril que la abrió puede
        // cerrarla. Antes de esta puerta el comentario del sitio que llama afirmaba «the trip it
        // opened» y la llamada no comparaba nada: un job del Coordinator que siguiera vivo cuando
        // llegó un CONNECT de Bluetooth (el CONNECT cancela `btHuntJob`, nunca `detectionJob`)
        // terminaba 18-36 s después y se llevaba por delante la plaza del otro carril.
        //
        // Se NIEGA, no se relaja: el sello queda intacto, así que la sesión no gasta su única
        // nominación y la plaza sigue en pie con su dueño legítimo todavía vigilándola —
        // `btWatchJob` la retirará en `NeverDrove`, y si ese dueño muere queda la TTL de suelo.
        if (pending.lane != lane && pending.lane != SpotReleaseLane.LEGACY) {
            PaparcarLogger.d(
                TAG,
                "⊘ $lane no retira ${pending.spotId.take(8)} — la nominó ${pending.lane} " +
                    "[DET-A-WITHDRAWAL-MUST-NAME-WHAT-IT-TAKES-BACK-001]",
            )
            record(SpotReleaseAction.SKIPPED, pending.spotId, lane, liveMs, REASON_NOT_MY_NOMINATION)
            return false
        }
        if (!pending.isLive(now, SpotTtlPolicy.RELEASING_SPOT_TTL_MS)) {
            PaparcarLogger.d(
                TAG,
                "nothing to withdraw for ${pending.spotId.take(8)} — its releasing window ran out " +
                    "${liveMs - SpotTtlPolicy.RELEASING_SPOT_TTL_MS}ms ago " +
                    "[DET-RETRACT-DENIED-FOREVER-001]",
            )
            record(SpotReleaseAction.WITHDRAW_EXPIRED, pending.spotId, lane, liveMs)
            return false
        }
        return spotRepository.retractSpot(pending.spotId)
            .onFailure {
                // Already swept, or offline. The short TTL is the floor precisely because this
                // call can fail; nothing else to do here.
                PaparcarLogger.w(TAG, "could not withdraw ${pending.spotId.take(8)} — the TTL still bounds it", it)
                // ⛔ Recorded AFTER the wire answered, never before. A trace that says WITHDRAWN on
                // a retraction the rules denied would leave a phantom standing and a log claiming
                // it was taken down — the exact shape of lie this ticket exists to remove.
                record(SpotReleaseAction.WITHDRAW_FAILED, pending.spotId, lane, liveMs, it.message)
            }
            .onSuccess {
                PaparcarLogger.d(
                    TAG,
                    "withdrew the releasing plaza ${pending.spotId.take(8)} — the boarding never " +
                        "became a departure (${liveMs}ms live)",
                )
                record(SpotReleaseAction.WITHDRAWN, pending.spotId, lane, liveMs)
            }
            .isSuccess
    }

    /**
     * [DET-A-RELEASING-SPOT-MUST-SAY-IT-WENT-OUT-001] One event per ending, wrapped so a
     * diagnostics failure can never change whether a plaza came down.
     */
    private suspend fun record(
        action: SpotReleaseAction,
        spotId: String,
        lane: String,
        liveMs: Long,
        reason: String? = null,
    ) {
        runCatching {
            detectionEventLogger.log(
                DetectionEvent.SpotRelease(
                    // The spot's id IS the parking session's id, so the event files itself under
                    // the park it is about without carrying a column of its own.
                    sessionId = spotId,
                    timestampMs = nowMs(),
                    action = action,
                    lane = lane,
                    reason = reason,
                    liveMs = liveMs,
                ),
            )
        }
    }

    private companion object {
        const val TAG = "PARKDIAG/SpotRelease"

        /** Citable: esta retirada era de otro carril. [DET-A-WITHDRAWAL-MUST-NAME-WHAT-IT-TAKES-BACK-001] */
        const val REASON_NOT_MY_NOMINATION = "not_my_nomination"
    }
}
