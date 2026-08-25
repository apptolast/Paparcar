package io.apptolast.paparcar.domain.detection.stages

import io.apptolast.paparcar.domain.detection.state.DetectionSessionState
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig

/**
 * [VEH-ACTIVE-FENCE-001][DET-BT-OWNERSHIP-001] **Whose car is this?** — settled once, on the first
 * driving-speed fix, before anything below can try to save a park.
 *
 * A park with no owner cannot be saved, which is the whole reason this outranks every confirm lane:
 * `should_resolve_the_vehicle_before_confirming_within_the_same_fix` (P0.1) pins that adjacency, and
 * it is the test that caught the snapshot bug in P3.6.
 *
 * ## The one stage that needs I/O, and what that actually means
 *
 * The plan flags this as the only stage requiring a repository, with a warning attached: if wiring
 * it gets complicated, the EFFECT is wrong — not the rule about repositories. Following that warning
 * changes the shape of the answer, and it is worth being plain about it.
 *
 * The obvious design — *decide in pure, then ask for the lookup* — is impossible here, because the
 * policy needs the lookup's ANSWER to decide: which vehicle is active, and whether the nominating
 * one is Bluetooth-paired. The sequence is ask → decide, not decide → ask.
 *
 * So the effect asks for FACTS rather than announcing a verdict, and the decision stays where it
 * already was: `VehicleFenceOwnershipPolicy.resolveSessionVehicleId`, a pure function extracted long
 * before this refactor. What this stage contributes is therefore modest and should not be dressed
 * up: **the gate is declared, its precedence is declared, and the I/O is named**. It does not make
 * the decision richer — it makes the decision's PLACE in the order a value instead of a line number.
 *
 * The stage does not end the pass. The branch it replaces falls through to the user-confirm lane on
 * success; only the abort ends anything, and the abort is discovered by the executor after the
 * lookup — which is why an effect is allowed to end the pass on its own.
 */
class VehicleAttributionStage : SessionStage {

    override val stage = DetectionStage.VEHICLE_ATTRIBUTION

    override fun evaluate(
        state: DetectionSessionState,
        fix: GpsPoint,
        now: Long,
        stoppedDurationMs: Long,
        config: ParkingDetectionConfig,
    ): StageVerdict {
        // Lock on the first driving-speed fix, and only once. [BUG-NEW-VEHICLE-DEFAULT][BUG-SHORT-TRIP]
        if (!state.session.driveAuthorized) return StageVerdict.Skip()
        if (state.session.attributedVehicleId != null) return StageVerdict.Skip()

        return StageVerdict.Handled(
            newState = state,
            effects = listOf(DetectionEffect.ResolveVehicle(state.session.nominatingVehicleId)),
        )
    }
}
