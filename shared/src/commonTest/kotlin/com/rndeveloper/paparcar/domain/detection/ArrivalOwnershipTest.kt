package com.rndeveloper.paparcar.domain.detection

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [DET-BACKFILL-MUST-NOT-PIN-A-MOVING-CAR-001] Who owns the arrival after a dispatched departure.
 *
 * The load-bearing case is the first one: it is the exact configuration of the 2026-08-30 field
 * false positive, and under the old ordering it produced a pin on a motorway.
 */
class ArrivalOwnershipTest {

    // ── The field FP: both on offer, the measurement must win ──────────────────────────────────

    /**
     * Field 2026-08-30 21:27:34 (Oppo, Calle del Verdugo 24). Every `backfillBounded` clause passed
     * on a wake-up fix that declared `speed=0.0` while doing ~37 km/h, and the handoff started in
     * the same second. The old ordering placed the guess and never asked the measurement.
     *
     * ⚠️ Falsification check for whoever edits [arrivalOwner]: invert the first two branches (put
     * the backfill ahead of the handoff) and THIS assertion must go red. If it still passes, the
     * test is watching something else and the guard is unprotected.
     */
    @Test
    fun should_giveArrivalToLiveDetection_when_handoffStartedAndBackfillAlsoBounded() {
        assertEquals(
            ArrivalOwner.LiveDetection,
            arrivalOwner(
                handoffStarted = true,
                departurePreconfirmed = true,
                backfillBounded = true,
            ),
        )
    }

    // ── The backstop that justifies the backfill still existing ────────────────────────────────

    /** FGS-start denied (Android 12+/OEM): nobody can measure, so the bounded guess is the best
     *  remaining answer — the case the backfill was built for. */
    @Test
    fun should_giveArrivalToBackfill_when_handoffDeniedAndPositionBounded() {
        assertEquals(
            ArrivalOwner.Backfill,
            arrivalOwner(
                handoffStarted = false,
                departurePreconfirmed = true,
                backfillBounded = true,
            ),
        )
    }

    // ── The honest exits: never neither [DET-ARRIVAL-HANDOFF-001] ──────────────────────────────

    /** [DET-DEPARTURE-IS-NOT-ARRIVAL-001] The step budget was spent proving the ride, so it cannot
     *  also bound the new pin. With no handoff either, the user is asked. */
    @Test
    fun should_askTheUser_when_handoffDeniedAndPositionUnbounded() {
        assertEquals(
            ArrivalOwner.UserPrompt,
            arrivalOwner(
                handoffStarted = false,
                departurePreconfirmed = true,
                backfillBounded = false,
            ),
        )
    }

    /** A departure the evaluator did not pre-confirm never licenses a reconstructed pin, bounded
     *  fix or not — the trip is not provably over. */
    @Test
    fun should_askTheUser_when_departureNotPreconfirmed() {
        assertEquals(
            ArrivalOwner.UserPrompt,
            arrivalOwner(
                handoffStarted = false,
                departurePreconfirmed = false,
                backfillBounded = true,
            ),
        )
    }

    /** The handoff outranks the backfill on every combination, so no input pair can leave the
     *  arrival orphaned while live detection is running. */
    @Test
    fun should_alwaysGiveArrivalToLiveDetection_when_handoffStarted() {
        listOf(true, false).forEach { preconfirmed ->
            listOf(true, false).forEach { bounded ->
                assertEquals(
                    ArrivalOwner.LiveDetection,
                    arrivalOwner(
                        handoffStarted = true,
                        departurePreconfirmed = preconfirmed,
                        backfillBounded = bounded,
                    ),
                    "handoff must win for preconfirmed=$preconfirmed bounded=$bounded",
                )
            }
        }
    }
}
