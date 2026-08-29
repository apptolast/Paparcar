package com.rndeveloper.paparcar.domain.detection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [DET-ASK-STATE-001] The visibility rule for the in-app "did you park?" row.
 *
 * The window is written and cleared by the notification adapter at the two verbs of the
 * confirmation channel, so every real ANSWER arrives here as a `null`. What these tests pin down is
 * the opposite: making the tray card disappear without answering must NOT close the question, and
 * only the response deadline does.
 */
class PendingPromptWindowTest {

    private val timeout = 15 * 60_000L

    @Test
    fun should_be_closed_when_no_question_was_ever_posted() {
        assertFalse(isPromptWindowOpen(window = null, nowMs = 1_000L, timeoutMs = timeout))
    }

    @Test
    fun should_be_open_the_instant_the_question_is_posted() {
        val posted = PendingPromptWindow(shownAtMs = 1_000L)
        assertTrue(isPromptWindowOpen(posted, nowMs = 1_000L, timeoutMs = timeout))
    }

    @Test
    fun should_stay_open_for_the_whole_response_window() {
        val posted = PendingPromptWindow(shownAtMs = 1_000L)
        assertTrue(isPromptWindowOpen(posted, nowMs = 1_000L + timeout / 2, timeoutMs = timeout))
        // The boundary belongs to the question: at exactly the timeout the coordinator has not
        // stopped honouring an answer yet, so neither does the row.
        assertTrue(isPromptWindowOpen(posted, nowMs = 1_000L + timeout, timeoutMs = timeout))
    }

    @Test
    fun should_keep_asking_when_the_user_only_silenced_the_notification() {
        // Swiping the card away, or tapping it (auto-cancel) and landing in the app, reaches NO
        // code — no delete-intent on this channel, on purpose. The slot stays written and the row
        // must keep asking: getting rid of a notification is not saying where the car is. The tap
        // case IS the ticket — the user arrives in Home with the question still owed.
        val posted = PendingPromptWindow(shownAtMs = 1_000L, vehicleName = "Škoda Kamiq")
        assertTrue(isPromptWindowOpen(posted, nowMs = 1_000L + 5 * 60_000L, timeoutMs = timeout))
    }

    @Test
    fun should_close_itself_only_when_the_response_deadline_has_passed() {
        // The one thing that ends an unanswered question: past the deadline the coordinator has
        // already emitted its own verdict, so the row would offer an answer nobody is listening for.
        val posted = PendingPromptWindow(shownAtMs = 1_000L)
        assertFalse(isPromptWindowOpen(posted, nowMs = 1_001L + timeout, timeoutMs = timeout))
    }

    @Test
    fun should_close_when_the_clock_moved_backwards() {
        // An age we cannot measure is never taken as evidence the question is live — the same
        // distrust of the device clock the detection evaluators apply.
        val posted = PendingPromptWindow(shownAtMs = 10_000L)
        assertFalse(isPromptWindowOpen(posted, nowMs = 9_000L, timeoutMs = timeout))
    }

    @Test
    fun should_survive_a_process_death_inside_the_window() {
        // Nothing ran to close it, but the question IS still answerable: the notification survived
        // the kill and its buttons still work, so the row must agree with the tray.
        val posted = PendingPromptWindow(shownAtMs = 1_000L, vehicleName = "Škoda Kamiq")
        assertTrue(isPromptWindowOpen(posted, nowMs = 1_000L + 60_000L, timeoutMs = timeout))
    }

    @Test
    fun should_keep_the_wording_the_notification_used_across_the_whole_window() {
        // The row inherits the tray's exact words. Once the card is gone (swiped/tapped) this record
        // is the ONLY copy of them, so it has to carry the name rather than re-derive one.
        val posted = PendingPromptWindow(shownAtMs = 1_000L, vehicleName = "Škoda Kamiq")
        assertEquals("Škoda Kamiq", posted.vehicleName)
        assertTrue(isPromptWindowOpen(posted, nowMs = 1_000L + timeout, timeoutMs = timeout))
    }
}
