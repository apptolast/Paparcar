package io.apptolast.paparcar.domain.detection.sentry

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [DET-FGS-REAPER-001] When the safety net may reap the ghost detection FGS notification. */
class GhostFgsReapDecisionTest {

    @Test
    fun reaps_on_the_periodic_tick_when_a_stale_pending_exists_and_detection_is_idle() {
        // The field case (Oppo 2026-07-21): a false-enter session's process was frozen, leaving a
        // ghost FGS. Periodic tick + stale pending + detection idle → reap the orphan.
        assertTrue(
            shouldReapGhostDetectionFgs(
                isPeriodicTick = true,
                isDetectionRunning = false,
                hasStalePending = true,
            ),
        )
    }

    @Test
    fun never_reaps_while_a_live_session_is_running() {
        // The inviolable invariant: a live session's notification is NEVER touched, even if an OLD
        // stale pending still lingers in the store alongside the new live arm.
        assertFalse(
            shouldReapGhostDetectionFgs(
                isPeriodicTick = true,
                isDetectionRunning = true,
                hasStalePending = true,
            ),
        )
    }

    @Test
    fun never_reaps_on_an_expedited_run_that_may_own_the_notification_itself() {
        // Expedited runs post their OWN copy of the notification via getForegroundInfo and self-heal
        // the ghost through WorkManager's lifecycle — reaping there would fight the worker's own notif.
        assertFalse(
            shouldReapGhostDetectionFgs(
                isPeriodicTick = false,
                isDetectionRunning = false,
                hasStalePending = true,
            ),
        )
    }

    @Test
    fun does_not_reap_when_there_is_no_process_death_signal() {
        // No stale pending → no evidence a session's process died → nothing to reap.
        assertFalse(
            shouldReapGhostDetectionFgs(
                isPeriodicTick = true,
                isDetectionRunning = false,
                hasStalePending = false,
            ),
        )
    }
}
