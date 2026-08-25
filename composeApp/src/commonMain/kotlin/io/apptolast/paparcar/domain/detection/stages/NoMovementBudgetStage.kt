package io.apptolast.paparcar.domain.detection.stages

import io.apptolast.paparcar.domain.detection.physics.SessionOutcome
import io.apptolast.paparcar.domain.detection.state.DetectionSessionState
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig

/**
 * **A session that never drove has a budget, and this is where it runs out.** The guard against a
 * spurious `IN_VEHICLE` ENTER: if nothing measured driving within the budget, fold the session
 * rather than keep a foreground service and a GPS stream alive on a phone sitting at home.
 *
 * It outranks the vehicle attribution and every confirm lane for the reason P0.1 pinned: a session
 * with nothing measured is over before it can be answered — a user tap cannot make a trip happen
 * (`should_fold_the_no_movement_budget_even_when_the_user_already_said_yes`).
 *
 * ## Three budgets, not one, and each has an incident behind it
 *
 *  - **The standard budget** (`maxNoMovementMs`, ~4 min) for an ordinary spurious arm.
 *  - **The short probe** (`staleExitNoMovementMs`) for a stale-delivered EXIT [DET-ZOMBIE-PROBE-001].
 *    A real mid-drive far delivery shows driving fixes within seconds; a zombie delivery never will,
 *    so there is no point burning the full window on it.
 *  - **The extended budget** (`jamExtendedNoMovementMs`) for a TRAFFIC-JAM CRAWL [DET-JAM-WINDOW-001],
 *    which is neither of the above: the car DID leave the spot but creeps below driving speed past
 *    the standard budget — a long light, a jam at the exit — and the silent fold lost the whole
 *    trip's coverage. Measured creep buys the extension; a stationary spurious arm shows only GPS
 *    noise and keeps folding at the standard budget, so the OEM power profile of false starts is
 *    unchanged. **Stale-lane zombies never get the extension**, which is what keeps the two guards
 *    from cancelling each other out.
 *
 * ## What this stage does NOT own
 *
 * The creep window itself — a sliding deque of recent fixes — and the latch that says the extension
 * already ran are per-session MUTABLE bookkeeping maintained by the loop on every fix, including
 * fixes this stage never sees. They are presented as measurements: how far the position crept, and
 * whether the extension has already been announced. A stage that owned a deque would be a stage that
 * has to be fed on the fixes where it is skipped, which is not what a stage is.
 *
 */
class NoMovementBudgetStage {

    /**
     * ⚠️ **This is the one stage that does NOT implement [SessionStage] yet**, and pretending
     * otherwise would be worse than saying so.
     *
     * Its decision needs three things the common signature cannot carry: which delivery lane armed
     * the session, how far the position crept inside a sliding window, and whether the extension has
     * already been announced. Two of those are per-session MUTABLE bookkeeping the loop maintains on
     * every fix — including the fixes where this stage is skipped.
     *
     * The alternatives were both worse. Inventing a state field per measurement to satisfy an
     * interface is the tail wagging the dog; implementing [SessionStage.evaluate] as a function that
     * throws puts a lie in the type system, which is exactly the class of defect this refactor keeps
     * finding — something that reads like a contract and is not one.
     *
     * So it stays a plain class with its own signature and its place in [DetectionStage] declared
     * below. It joins the interface when the creep window has a home: [09 §5] slates it to be
     * absorbed by the drive proof ring, which already retains recent fixes for a different question.
     * That is a Phase 4 consolidation — two windows with different retention rules is a merge that
     * needs its own argument, not a rider on a move.
     */
    val stage = DetectionStage.NO_MOVEMENT_BUDGET

    /**
     * @param staleExitDelivery The arming EXIT arrived late enough to be a zombie delivery.
     * @param recentCreepMeters Ground covered inside the creep window, measured by the loop.
     * @param extensionAlreadyAnnounced Whether the extension has already been logged for this
     *   session — the latch that keeps one line in the trace instead of one per fix.
     */
    @Suppress("LongParameterList")
    fun evaluate(
        state: DetectionSessionState,
        fix: GpsPoint,
        sessionAgeMs: Long,
        staleExitDelivery: Boolean,
        recentCreepMeters: Double,
        extensionAlreadyAnnounced: Boolean,
        config: ParkingDetectionConfig,
    ): StageVerdict {
        if (state.session.driveAuthorized) return StageVerdict.Skip()

        val budgetMs = if (staleExitDelivery) config.staleExitNoMovementMs else config.maxNoMovementMs
        if (sessionAgeMs <= budgetMs) return StageVerdict.Skip()

        val jamCrawl = !staleExitDelivery && recentCreepMeters >= config.jamCreepMinMeters
        if (jamCrawl && sessionAgeMs <= config.jamExtendedNoMovementMs) {
            // Keep watching. One line per session, not one per fix.
            val notes = if (extensionAlreadyAnnounced) {
                emptyList()
            } else {
                listOf(
                    "  ⏲ no-movement budget EXTENDED — recent creep ${recentCreepMeters.toInt()}m " +
                        "in ${config.jamCreepWindowMs}ms without driving speed (jam/stop-go " +
                        "crawl) → watching until ${config.jamExtendedNoMovementMs}ms [DET-JAM-WINDOW-001]",
                )
            }
            return StageVerdict.Handled(newState = state, notes = notes)
        }

        // [DET-JAM-WINDOW-001] A DISTINCT outcome when the extension ran, plus its telemetry: field
        // data sizes this cohort — a jam that never cleared, or a crawl into a re-park? — before
        // anyone decides whether it deserves a nudge. The 21-08 sweep found the cohort EMPTY, so the
        // question is still open and the instrument stays.
        val effects = buildList {
            add(
                DetectionEffect.EndSession(
                    if (extensionAlreadyAnnounced) {
                        SessionOutcome.AbortedNoMovementJam.serialized
                    } else {
                        SessionOutcome.AbortedNoMovement.serialized
                    },
                ),
            )
            if (extensionAlreadyAnnounced) {
                add(
                    DetectionEffect.RecordJamFold(
                        recentCreepMeters = recentCreepMeters,
                        rawPeakMps = state.drive.peakMps,
                        at = fix,
                    ),
                )
            }
        }

        return StageVerdict.Handled(
            newState = state,
            effects = effects,
            stopsIteration = true,
            notes = listOf(
                "  ⚑ no-movement guard hit after ${sessionAgeMs}ms " +
                    "(budget=${budgetMs}ms staleExitDelivery=$staleExitDelivery " +
                    "recentCreep=${recentCreepMeters.toInt()}m jamExtended=$extensionAlreadyAnnounced) " +
                    "→ completed=true",
            ),
        )
    }

}
