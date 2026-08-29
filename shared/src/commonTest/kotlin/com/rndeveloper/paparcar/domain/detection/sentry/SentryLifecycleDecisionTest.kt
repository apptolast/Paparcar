package com.rndeveloper.paparcar.domain.detection.sentry

import com.rndeveloper.paparcar.domain.detection.ServicePresence

import com.rndeveloper.paparcar.domain.detection.ParkingStrategy

import kotlin.test.Test
import kotlin.test.assertEquals

class SentryLifecycleDecisionTest {

    @Test
    fun should_stop_when_auto_detect_off_even_with_parked_session() {
        // [F3] Detection turned off in Settings → no residency, byte-for-byte wake-and-kill.
        assertEquals(
            PostDetectionLifecycle.Stop,
            resolvePostDetectionLifecycle(autoDetectEnabled = false, hasParkedSession = true, strategy = ParkingStrategy.COORDINATOR),
        )
    }

    @Test
    fun should_stop_when_auto_detect_off_and_no_parked_session() {
        assertEquals(
            PostDetectionLifecycle.Stop,
            resolvePostDetectionLifecycle(autoDetectEnabled = false, hasParkedSession = false, strategy = ParkingStrategy.COORDINATOR),
        )
    }

    @Test
    fun should_enter_sentry_when_auto_detect_on_and_parked_session_exists() {
        assertEquals(
            PostDetectionLifecycle.EnterSentry,
            resolvePostDetectionLifecycle(autoDetectEnabled = true, hasParkedSession = true, strategy = ParkingStrategy.COORDINATOR),
        )
    }

    @Test
    fun should_stop_when_auto_detect_on_but_nothing_parked_to_watch() {
        // After a revert / with no active session there is no departure to catch — never leave a
        // resident FGS with no purpose.
        assertEquals(
            PostDetectionLifecycle.Stop,
            resolvePostDetectionLifecycle(autoDetectEnabled = true, hasParkedSession = false, strategy = ParkingStrategy.COORDINATOR),
        )
    }

    @Test
    fun should_stop_when_bluetooth_strategy_owns_detection() {
        // [DET-STRATEGY-GATE-001] The deterministic ACL broadcast wakes the process by itself —
        // residency under BLUETOOTH only burns battery and pins a permanent notification.
        assertEquals(
            PostDetectionLifecycle.Stop,
            resolvePostDetectionLifecycle(autoDetectEnabled = true, hasParkedSession = true, strategy = ParkingStrategy.BLUETOOTH),
        )
    }

    @Test
    fun should_stop_when_strategy_is_none() {
        // A scooter/bike fleet never parks — nothing for a resident watcher to catch.
        assertEquals(
            PostDetectionLifecycle.Stop,
            resolvePostDetectionLifecycle(autoDetectEnabled = true, hasParkedSession = true, strategy = ParkingStrategy.NONE),
        )
    }

    // ── resolveSentryKillVerdict [F2] ─────────────────────────────────────────

    @Test
    fun should_do_nothing_when_no_residency_stamp_exists() {
        assertEquals(
            SentryKillVerdict.None,
            resolveSentryKillVerdict(
                residencyExpected = false,
                presence = ServicePresence.Dead,
                rebootedSince = false,
            ),
        )
    }

    @Test
    fun should_do_nothing_when_sentry_is_alive_and_resident() {
        // The worker's periodic tick lands while the resident watcher is well — the stamp stays.
        assertEquals(
            SentryKillVerdict.None,
            resolveSentryKillVerdict(
                residencyExpected = true,
                presence = ServicePresence.Sentry,
                rebootedSince = false,
            ),
        )
    }

    @Test
    fun should_clear_silently_when_a_reboot_explains_the_dead_residency() {
        // Powering the phone off kills the sentry innocently — the boot receiver re-arms; no kill
        // telemetry (consistent with BackgroundKillSuspected skipping reboots).
        assertEquals(
            SentryKillVerdict.ClearStamp,
            resolveSentryKillVerdict(
                residencyExpected = true,
                presence = ServicePresence.Dead,
                rebootedSince = true,
            ),
        )
    }

    @Test
    fun should_clear_silently_when_a_tracking_job_missed_the_handoff() {
        // Defensive: ACTIVE with the stamp still set means the wake path failed to clear it —
        // the service is demonstrably alive, so it was no kill.
        assertEquals(
            SentryKillVerdict.ClearStamp,
            resolveSentryKillVerdict(
                residencyExpected = true,
                presence = ServicePresence.Active,
                rebootedSince = false,
            ),
        )
    }

    @Test
    fun should_report_killed_when_the_stamp_outlived_a_dead_process() {
        // The MIUI/ColorOS deep-kill signature: residency stamped, no deliberate exit, process dead.
        assertEquals(
            SentryKillVerdict.Killed,
            resolveSentryKillVerdict(
                residencyExpected = true,
                presence = ServicePresence.Dead,
                rebootedSince = false,
            ),
        )
    }
}
