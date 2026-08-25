package io.apptolast.paparcar.domain.detection.state

import io.apptolast.paparcar.domain.coordinator.ConfirmationPhase
import io.apptolast.paparcar.domain.model.GpsPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [09 §5] The transitions of the second sub-state.
 *
 * The phase machine itself is already pinned by `ConfirmationPhaseMappingTest` and by the
 * coordinator's own hold tests. What had no test of its own is the part this step named: the three
 * ways a held confirm can END, which were three identical-looking `pendingConfirm = null` writes.
 */
class ConfirmationLifecycleTest {

    private val here = GpsPoint(36.7, -4.4, accuracy = 8f, timestamp = 1_000L, speed = 0f)
    private val held = PendingConfirm(here, reliability = 0.8f, vehicleId = "veh-1", pathLabel = "steps+egress", confirmedAt = 5_000L)

    // ── The three ends of a hold ──────────────────────────────────────────────

    /**
     * Discarding a stale or driven-off hold says NOTHING to the user: the prompt state must survive
     * untouched, because a response-timeout opened earlier is still ticking. Clearing it here is
     * how a session that already asked something forgets it did.
     */
    @Test
    fun should_leave_the_prompt_alone_when_a_hold_is_merely_discarded() {
        val notified = ConfirmationLifecycle().notified(shownAt = 2_000L).holding(held)
        val after = notified.discardingHold()
        assertNull(after.pendingConfirm)
        assertEquals(ConfirmationPhase.Notified(2_000L), after.phase)
        assertEquals(2_000L, after.promptShownAt, "the response window must keep its original instant")
    }

    /**
     * [DET-SOLID-001] Degrading to a prompt is the OTHER thing: the hold dies and the user is asked,
     * with a FRESH response window. Read as a bare `pendingConfirm = null` these two are the same
     * line, and the difference is a question on the user's screen.
     */
    @Test
    fun should_open_a_fresh_prompt_when_a_refused_save_degrades_the_hold() {
        val after = ConfirmationLifecycle().notified(shownAt = 2_000L).holding(held)
            .degradedToPrompt(shownAt = 9_000L)
        assertNull(after.pendingConfirm)
        assertEquals(9_000L, after.promptShownAt)
    }

    /**
     * ⛔ The hold watchdog compares by IDENTITY. A transition that touches other fields must carry
     * the SAME instance through, or every fix restarts the watchdog and the hold never settles.
     */
    @Test
    fun should_carry_the_held_confirm_through_by_identity() {
        val lifecycle = ConfirmationLifecycle().holding(held)
        val afterPhaseChange = lifecycle.notified(shownAt = 3_000L).userSaidYes()
        assertSame(held, afterPhaseChange.pendingConfirm)
    }

    // ── The conversation ──────────────────────────────────────────────────────

    /** A prompt shown at Low/Medium keeps its instant when HIGH arrives: the user is not asked
     *  twice, so the response timeout must not restart. [BUG-STUCK-SESSION] */
    @Test
    fun should_keep_the_original_prompt_instant_when_high_arrives_after_a_prompt() {
        val notified = ConfirmationLifecycle().notified(shownAt = 2_000L)
        val candidate = notified.candidate(highReachedAt = 7_000L, hadVehicleExit = true, shownAt = notified.promptShownAt!!)
        assertEquals(2_000L, candidate.promptShownAt)
        assertEquals(
            ConfirmationPhase.Candidate(highReachedAt = 7_000L, hadVehicleExit = true, shownAt = 2_000L),
            candidate.phase,
        )
    }

    /** Idle and LowReached mean "nothing on screen", so no response window is open. */
    @Test
    fun should_report_no_prompt_while_nothing_has_been_shown() {
        assertNull(ConfirmationLifecycle().promptShownAt)
        assertNull(ConfirmationLifecycle().lowReached(1_000L).promptShownAt)
    }

    /**
     * The vehicle drove away: the conversation restarts. The hold does NOT — a confirm held through
     * its window is resolved by the hold's own branches, not by the stop ending underneath it.
     */
    @Test
    fun should_restart_the_conversation_but_not_the_hold_when_the_stop_ends() {
        val after = ConfirmationLifecycle().notified(2_000L).holding(held).stopEnded()
        assertEquals(ConfirmationPhase.Idle, after.phase)
        assertSame(held, after.pendingConfirm)
    }

    // ── The user ──────────────────────────────────────────────────────────────

    /** "Sí" is sticky and independent of the phase — it outranks every guard downstream. */
    @Test
    fun should_latch_the_users_yes_across_later_phase_changes() {
        val after = ConfirmationLifecycle().userSaidYes().notified(2_000L).stopEnded()
        assertTrue(after.userConfirmed)
        assertFalse(ConfirmationLifecycle().userConfirmed)
    }
}
