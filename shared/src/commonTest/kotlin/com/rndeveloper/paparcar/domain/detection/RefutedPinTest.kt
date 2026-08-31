package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [PARK-A-REFUTED-PIN-LEAVES-THE-HISTORY-001] The predicate that decides whether a parking's own
 * departure REFUTED it, plus the census of the type-level question it rests on.
 *
 * The two field cases it exists for are replayed literally below — 63 s on 2026-08-27 (Ronda del
 * Puerto) and 52 s on 2026-08-30 (Calle del Verdugo) — because a policy about "a short life" is
 * worth nothing unless the two lives that produced it are inside it.
 * [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]
 */
class RefutedPinTest {

    private val config = ParkingDetectionConfig()
    private val parkedAt = 1_756_000_000_000L

    private fun refuted(
        path: DetectionPath?,
        lifeMs: Long,
        maxLifeMs: Long = config.refutedPinMaxLifeMs,
    ) = pinIsRefutedByItsOwnDeparture(
        path = path,
        parkedAtMs = parkedAt,
        departedAtMs = parkedAt + lifeMs,
        maxLifeMs = maxLifeMs,
    )

    // ── The two field cases ───────────────────────────────────────────────────────────────────

    @Test
    fun should_withdrawTheBackfillPin_when_itsOwnDepartureRefutedItAfter63Seconds() {
        // Field 2026-08-27, Oppo. Backfill planted 724befda at 12:29:18; EXIT on that very pin's
        // geofence at 12:29:36; departure confirmed at 16,3 km/h at 12:30:21. The user reported the
        // leftover row as "un FALSO POSITIVAZO en Dia · Calle Ronda del Puerto 15".
        assertTrue(refuted(DetectionPath.SafetyNetBackfill, lifeMs = 63_000L))
    }

    @Test
    fun should_withdrawTheBackfillPin_when_itsOwnDepartureRefutedItAfter52Seconds() {
        // Field 2026-08-30, Oppo, Calle del Verdugo: a pin on the road, mid-drive, with a community
        // space published. DET-BACKFILL-MUST-NOT-PIN-A-MOVING-CAR-001 stops the next one being
        // created; only this reaches the row already written.
        assertTrue(refuted(DetectionPath.SafetyNetBackfill, lifeMs = 52_000L))
    }

    // ── The three conditions ──────────────────────────────────────────────────────────────────

    @Test
    fun should_keepTheParking_when_thePathWasNotAReconstruction() {
        // Every other path had a live session that watched something, so a departure from it is an
        // ordinary ending. A 30-second life is suspicious; it is not OUR evidence of anything.
        DetectionPath.fixedLabelPaths
            .filterNot { it.mayBeWithdrawnByTheApp }
            .forEach { path ->
                assertFalse(
                    refuted(path, lifeMs = 30_000L),
                    "$path is not withdrawable, so no life is short enough to withdraw it",
                )
            }
    }

    @Test
    fun should_keepTheParking_when_itOutlivedTheWindow() {
        assertTrue(refuted(DetectionPath.SafetyNetBackfill, lifeMs = config.refutedPinMaxLifeMs))
        assertFalse(refuted(DetectionPath.SafetyNetBackfill, lifeMs = config.refutedPinMaxLifeMs + 1))
    }

    /**
     * ⛔ Fails CLOSED. An unrecognised label parses to null ([DetectionPath.ofLabel]) and nothing is
     * withdrawn — the same direction that type chose for every other question it answers.
     */
    @Test
    fun should_keepTheParking_when_nothingRecognisesTheLabel() {
        assertFalse(refuted(DetectionPath.ofLabel("a_path_invented_after_this_ticket"), lifeMs = 1_000L))
        assertFalse(refuted(DetectionPath.ofLabel(null), lifeMs = 1_000L))
        assertFalse(refuted(path = null, lifeMs = 1_000L))
    }

    /**
     * A clock that moved backwards is not a young pin. Without this the negative age would read as
     * the shortest life of all and withdraw a parking on the strength of an NTP correction.
     */
    @Test
    fun should_keepTheParking_when_theDepartureLandsBeforeThePark() {
        assertFalse(refuted(DetectionPath.SafetyNetBackfill, lifeMs = -1L))
        assertFalse(refuted(DetectionPath.SafetyNetBackfill, lifeMs = -60 * 60_000L))
    }

    // ── The census of the type-level question ─────────────────────────────────────────────────

    /**
     * Exactly one path may be withdrawn by the app, and the reason is stated where it is answered:
     * it is the only pin placed with NO live session behind it. A new path forced to answer this
     * question and answering `true` should have to change this test on purpose.
     */
    @Test
    fun should_letOnlyTheReconstructionBeWithdrawnByTheApp() {
        val withdrawable = DetectionPath.fixedLabelPaths.filter { it.mayBeWithdrawnByTheApp }
        assertEquals(listOf<DetectionPath>(DetectionPath.SafetyNetBackfill), withdrawable)
        assertFalse(
            DetectionPath.UnattendedZone("gap_anchor").mayBeWithdrawnByTheApp,
            "the composed family answers like its exact sibling — the doubt is in the radius",
        )
    }

    /**
     * The population witness for the census above: a filter over an empty list is also "exactly the
     * expected set" when the expected set is empty, and `fixedLabelPaths` is a hand-kept list.
     */
    @Test
    fun should_haveEveryDeclaredPathInThePopulationItCensuses() {
        assertTrue(
            DetectionPath.fixedLabelPaths.size >= MIN_PATHS,
            "[${DetectionPath.fixedLabelPaths.size} paths] the census above is vacuous below $MIN_PATHS",
        )
        // Nothing the USER placed may ever be withdrawn by the app on its own.
        DetectionPath.fixedLabelPaths
            .filter { it.source == ParkingDetectionSource.Manual }
            .forEach { assertFalse(it.mayBeWithdrawnByTheApp, "$it was placed by the user's own hand") }
    }

    private companion object {
        /** 11 fixed-label paths today; half, per GuardrailScope's doctrine on floors. */
        const val MIN_PATHS = 6
    }
}
