package com.rndeveloper.paparcar.worker

import com.rndeveloper.paparcar.detection.worker.EnrichParkingSessionWorker
import com.rndeveloper.paparcar.domain.detection.DetectionPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [DET-A-NUDGE-MUST-SAY-WHO-ASKED-001] The pin-to-pin route reconstruction decides eligibility by
 * PARSING the confirmation path, and this is the witness that it looked at the whole population.
 *
 * It used to be `session.detectionPath !in setOf("safety_net_backfill", "manual", "user", "nudge")`
 * — equality against a hand-kept list. The moment `nudge` learned to carry who asked
 * (`nudge_bt_no_move`), every nudge pin would have fallen out of that set and stayed routeless
 * forever, and nothing would have failed: a missing route is silence, not a crash. This test is the
 * noise that silence lacked.
 */
class PinToPinEligibilityTest {

    private fun eligible(label: String?) = EnrichParkingSessionWorker.isPinToPinEligible(label)

    @Test
    fun should_reconstructTheHop_when_thePlacementIsTrusted() {
        // The safety net's reconciled backfill plus the three user-ground-truth placements.
        listOf("safety_net_backfill", "manual", "user", "nudge").forEach {
            assertTrue(eligible(it), "path=$it must stay eligible")
        }
    }

    @Test
    fun should_reconstructTheHop_when_theNudgeSaysWhoAsked() {
        // The regression this test exists for: the composed label is the SAME kind of pin.
        listOf("bt_no_ride", "bt_no_move", "bt_ride_unmeasurable", "stale_pending_watchdog")
            .forEach { source ->
                val label = DetectionPath.Nudge(source).label
                assertTrue(eligible(label), "path=$label must stay eligible")
            }
    }

    @Test
    fun should_leaveTheGuessedAndTheMeasuredAlone() {
        // Unchanged exclusions, each for its own reason (see the worker's doc): a measured route
        // already exists, or a guessed route to a guessed pin would stack doubt on doubt.
        listOf(
            "bt", "bt_timeout",
            "unattended_timeout", "unattended_zone_walk_entered_anchor",
            "closed_approximate_pin", "closed_approximate_zone",
            "steps+egress", "kinematic+egress", "vehicle-exit",
            "user_moved",
        ).forEach { assertFalse(eligible(it), "path=$it must NOT be eligible") }
    }

    @Test
    fun should_refuse_when_theLabelIsUnknownOrAbsent() {
        // Fails closed, like `DetectionPath.ofLabel` itself: an unrecognised path is not a licence
        // to invent a route for it.
        assertFalse(eligible(null))
        assertFalse(eligible(""))
        assertFalse(eligible("something_new"))
        // A dangling prefix is still a nudge with no asker — never written by us, but if it ever
        // arrives it must behave like the bare label, not fall off the eligible list.
        assertTrue(eligible("nudge_"))
    }

    @Test
    fun should_classifyEveryDeclaredPath_soANewOneCannotDefaultIn() {
        // Population witness: every label the app can write is asked, and the answer is a decision
        // someone made — not the fall-through of a set that never heard of it.
        val declared = DetectionPath.fixedLabelPaths.map { it.label } +
            DetectionPath.UnattendedZone("gap_anchor").label +
            DetectionPath.Nudge("bt_no_move").label
        val eligibleOnes = declared.filter { eligible(it) }.toSet()
        assertEquals(
            setOf("safety_net_backfill", "manual", "user", "nudge", "nudge_bt_no_move"),
            eligibleOnes,
        )
    }
}
