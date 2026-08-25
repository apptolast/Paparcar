package io.apptolast.paparcar.domain.detection.stages

import io.apptolast.paparcar.domain.detection.state.DetectionSessionState
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig

/**
 * **No drive, no decision.** Every stage below this one reasons about a trip; until the session is
 * authorized as post-drive there is nothing for them to decide, so the pass ends here.
 *
 * The smallest stage of the ten — two lines in the loop — and it is worth having as a stage rather
 * than as an early `return` for one reason: it is a **precedence claim**. Four branches outrank it
 * (the hold, the false-ENTER abort, the no-movement budget and the user's tap) and five are gated by
 * it, and that ranking was previously nothing but where the `if` happened to sit. `DetectionStage`
 * now says it, and `StageOrderTest` fails if anyone moves it.
 *
 * ⚠️ It is an authorization gate, not evidence. `driveAuthorized` is the NOMINATION — the arm may
 * have lent it on trust and a dismissed departure can still take it back [07 §3.3]. A stage below
 * this one still has to ask the drive proof what was actually measured; passing this gate proves
 * nothing about the trip.
 */
class PreDriveSkipStage : SessionStage {

    override val stage = DetectionStage.PRE_DRIVE_SKIP

    override fun evaluate(
        state: DetectionSessionState,
        fix: GpsPoint,
        now: Long,
        stoppedDurationMs: Long,
        config: ParkingDetectionConfig,
    ): StageVerdict = if (state.session.driveAuthorized) {
        StageVerdict.Skip()
    } else {
        // Changes nothing and asks for nothing: it only ends the pass. That combination is exactly
        // what `stopsIteration` exists to express — before it, the difference between a branch that
        // fell through and one that returned was whether a `return` happened to follow it.
        StageVerdict.Handled(
            newState = state,
            stopsIteration = true,
            notes = notes("  ⏸ skipping: !hasEverReachedDrivingSpeed"),
        )
    }
}
