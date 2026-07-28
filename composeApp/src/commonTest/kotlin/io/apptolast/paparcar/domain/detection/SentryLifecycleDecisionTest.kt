package io.apptolast.paparcar.domain.detection

import kotlin.test.Test
import kotlin.test.assertEquals

class SentryLifecycleDecisionTest {

    @Test
    fun should_stop_when_flag_off_even_with_parked_session() {
        // The default production state: flag OFF → byte-for-byte today's wake-and-kill.
        assertEquals(
            PostDetectionLifecycle.Stop,
            resolvePostDetectionLifecycle(sentryEnabled = false, hasParkedSession = true),
        )
    }

    @Test
    fun should_stop_when_flag_off_and_no_parked_session() {
        assertEquals(
            PostDetectionLifecycle.Stop,
            resolvePostDetectionLifecycle(sentryEnabled = false, hasParkedSession = false),
        )
    }

    @Test
    fun should_enter_sentry_when_flag_on_and_parked_session_exists() {
        assertEquals(
            PostDetectionLifecycle.EnterSentry,
            resolvePostDetectionLifecycle(sentryEnabled = true, hasParkedSession = true),
        )
    }

    @Test
    fun should_stop_when_flag_on_but_nothing_parked_to_watch() {
        // After a revert / with no active session there is no departure to catch — never leave a
        // resident FGS with no purpose.
        assertEquals(
            PostDetectionLifecycle.Stop,
            resolvePostDetectionLifecycle(sentryEnabled = true, hasParkedSession = false),
        )
    }
}
