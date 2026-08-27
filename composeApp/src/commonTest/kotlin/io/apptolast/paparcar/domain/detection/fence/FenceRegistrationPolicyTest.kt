package io.apptolast.paparcar.domain.detection.fence

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [DET-FENCE-REREGISTER-BY-CAUSE-001 §A] Every successful re-registration punches a hole in the
 * very trigger it is protecting (Play Services resets INSIDE/OUTSIDE to unknown), so the only
 * registrations worth skipping are the ones we can PROVE are redundant.
 */
class FenceRegistrationPolicyTest {

    private val window = 5 * 60_000L
    private val now = 1_000_000L

    @Test
    fun should_register_when_this_process_has_no_record_of_the_fence() {
        // The force-stop case, and the one that must never be skipped: a fresh process cannot know
        // whether Play Services still holds the fence, and there is no API to ask.
        assertTrue(FenceRegistrationPolicy.shouldRegister(null, now, window))
    }

    @Test
    fun should_skip_a_second_pass_moments_after_one_we_performed_ourselves() {
        // Measured on the Oppo 2026-08-20: the same fence re-registered twice, 4.3 s apart, because
        // two callers enqueue the janitor on app start. Two blind windows for one restoration.
        assertFalse(FenceRegistrationPolicy.shouldRegister(now - 4_300L, now, window))
    }

    @Test
    fun should_register_again_once_the_dedup_window_has_elapsed() {
        assertTrue(FenceRegistrationPolicy.shouldRegister(now - window, now, window))
        assertFalse(FenceRegistrationPolicy.shouldRegister(now - window + 1, now, window))
    }

    @Test
    fun should_let_a_known_cause_override_a_recent_registration() {
        // A dismissed false EXIT poisons the fence state OUTSIDE. The fence exists and is useless,
        // which is exactly what the cure is for — "I registered it a minute ago" is no argument.
        assertFalse(FenceRegistrationPolicy.shouldRegister(now - 1_000L, now, window))
        assertTrue(FenceRegistrationPolicy.shouldRegister(now - 1_000L, now, window, hasKnownCause = true))
    }

    @Test
    fun should_register_when_the_stamp_lies_in_the_future() {
        // A clock jumped backwards must never mute registration forever — the same guard the
        // user-stop quiet period carries.
        assertTrue(FenceRegistrationPolicy.shouldRegister(now + 60_000L, now, window))
    }
}
