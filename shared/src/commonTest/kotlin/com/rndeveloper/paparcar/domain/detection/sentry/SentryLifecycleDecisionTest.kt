package com.rndeveloper.paparcar.domain.detection.sentry

import com.rndeveloper.paparcar.domain.detection.ServicePresence

import kotlin.test.Test
import kotlin.test.assertEquals

class SentryLifecycleDecisionTest {

    // ── resolvePostDetectionLifecycle ────────────────────────────────────────

    private val NO_BT = emptySet<String>()

    @Test
    fun should_stop_when_auto_detect_off_even_with_parked_session() {
        // [F3] Detection turned off in Settings → no residency, byte-for-byte wake-and-kill.
        assertEquals(
            PostDetectionLifecycle.Stop,
            resolvePostDetectionLifecycle(autoDetectEnabled = false, parkedCarNeedsWatcher = true),
        )
    }

    @Test
    fun should_enter_sentry_when_auto_detect_on_and_a_parked_car_needs_watching() {
        assertEquals(
            PostDetectionLifecycle.EnterSentry,
            resolvePostDetectionLifecycle(autoDetectEnabled = true, parkedCarNeedsWatcher = true),
        )
    }

    @Test
    fun should_stop_when_auto_detect_on_but_nothing_needs_watching() {
        // Tras un revert / sin sesión activa no hay salida que cazar — nunca se deja un FGS
        // residente sin propósito.
        assertEquals(
            PostDetectionLifecycle.Stop,
            resolvePostDetectionLifecycle(autoDetectEnabled = true, parkedCarNeedsWatcher = false),
        )
    }

    // ── parkedCarNeedsWatcher [DET-A-PARKED-BT-CAR-NEEDS-NO-RESIDENT-WATCHER-001] ────────────

    @Test
    fun should_needNoWatcher_when_nothingIsParked() {
        assertEquals(false, parkedCarNeedsWatcher(parkedVehicleIds = emptyList(), vehiclesThatNeedNoWatcher = NO_BT))
    }

    /**
     * ⛔ EL TESTIGO DE LA ISSUE #57. Un solo coche, emparejado por BT, aparcado: su ACL CONNECT es
     * su propia salida y lo entrega un receptor de manifiesto al proceso muerto, así que el
     * centinela —y su notificación permanente— sobran. Antes esto devolvía «residir» porque se
     * preguntaba por la estrategia del MOMENTO, y un coche aparcado está desconectado por
     * definición.
     */
    @Test
    fun should_needNoWatcher_when_theOnlyParkedCarIsBtPaired() {
        assertEquals(
            false,
            parkedCarNeedsWatcher(parkedVehicleIds = listOf("v-bt"), vehiclesThatNeedNoWatcher = setOf("v-bt")),
        )
    }

    @Test
    fun should_needWatcher_when_theParkedCarHasNoBtPairing() {
        assertEquals(
            true,
            parkedCarNeedsWatcher(parkedVehicleIds = listOf("v-plain"), vehiclesThatNeedNoWatcher = setOf("v-bt")),
        )
    }

    /** Lo que pidió el user: con otro coche aparcado sin BT, la residencia se queda por ÉSE. */
    @Test
    fun should_needWatcher_when_oneOfTwoParkedCarsHasNoBtPairing() {
        assertEquals(
            true,
            parkedCarNeedsWatcher(
                parkedVehicleIds = listOf("v-bt", "v-plain"),
                vehiclesThatNeedNoWatcher = setOf("v-bt"),
            ),
        )
    }

    /** Ante la duda se vigila: una sesión sin coche conocido no puede declarar despertador. */
    @Test
    fun should_needWatcher_when_aParkedSessionHasNoVehicle() {
        assertEquals(
            true,
            parkedCarNeedsWatcher(parkedVehicleIds = listOf(null), vehiclesThatNeedNoWatcher = setOf("v-bt")),
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
