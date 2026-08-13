package io.apptolast.paparcar.domain.detection

/**
 * [IOS-F0-06] Durable side-records of the detection system, as common TYPED contracts.
 *
 * Doctrine [DET-B-02]: coordinator state is in-memory BY DESIGN; durability lives in these small
 * disk records, reconciled on wake. They are the memory the iOS wake-and-query reconstruction
 * reads first (`docs/IOS-IMPLEMENTATION-PLAN.md` §4) and what the Android safety net already
 * scans every 15 min.
 *
 * Design decision (over a generic KV `DetectionSideStore`): one TYPED contract per record —
 * each has its own semantics, expiry and owner, and a generic map would push parsing and
 * invariants onto every consumer. The step-seal record already has its own port
 * (`DetectionStepAnchors`) and is not duplicated here. `SentryResidenceStore` is deliberately
 * NOT ported: SENTRY residence is Android-only (iOS: the OS wake mesh replaces it).
 *
 * Android impls WRAP the existing SharedPreferences code — same file, same keys, same value
 * format, no field-data migration. iOS impls persist to NSUserDefaults.
 */

/**
 * [DET-NEVER-SILENT-001] Durable record of an ARMED detection session. Survives process death,
 * so the watchdog can recover a park that was silently lost when the OS killed the process
 * mid-trip. Keyed by `armId` (NOT geofenceId — an arm has no saved parking yet). The
 * orchestrator writes it at arm, refreshes its heartbeat while the session is alive, and clears
 * it at any terminal. A pending whose heartbeat has gone stale ⇒ the process died before
 * resolving ⇒ the watchdog nudges.
 */
interface PendingArmRecords {

    /** Persist a new arm (heartbeat = [armedAt], sawDriving = false). */
    fun arm(armId: String, armedAt: Long, trigger: String)

    /** Refresh the heartbeat of a live session; [sawDriving] latches true and never unlatches.
     *  No-op if the arm was already cleared (a terminal must stay terminal). */
    fun heartbeat(armId: String, heartbeatAt: Long, sawDriving: Boolean)

    fun clear(armId: String)

    /** Pendings whose heartbeat is older than [deadMs] — presumed orphaned by process death. */
    fun scanStale(nowMs: Long, deadMs: Long): List<PendingArm>
}

data class PendingArm(
    val armId: String,
    val armedAt: Long,
    val heartbeatAt: Long,
    val trigger: String,
    val sawDriving: Boolean,
)

/**
 * [DET-CONJUNCTION-001] The FACT that the OS delivered a geofence EXIT, keyed by geofenceId —
 * recorded when delivery lands too far from the fence to grant departure authority on its own
 * ([DET-EXIT-TRUST-001]). Disk-backed because the conjunction (EXIT + independent AR boarding
 * within the pair window) may only become decidable ticks — or a process death — later.
 */
interface ExitDeliveryRecords {

    fun record(geofenceId: String, deliveredAtMs: Long)

    /** Delivery timestamp for [geofenceId], or null when none is on record. */
    fun deliveredAt(geofenceId: String): Long?

    /** Whether ANY fence reported a delivered EXIT within [maxAgeMs] of [nowMs] — the cheap
     *  synchronous read the live half of the conjunction needs. */
    fun hasRecentDelivery(nowMs: Long, maxAgeMs: Long): Boolean
}

/**
 * [DET-BACKFILL-TAINT-001] The coordinator's latest NUDGE-ONLY arrival resolution (gap-anchor
 * abort): WHEN it was stamped + the arrival's last fix. ONE slot, latest wins — it describes THE
 * arrival in flight, not a geofence. The backfill defers its placement to the nudge while the
 * stamp is fresh and near; it expires by age (`arrivalResolutionWindowMs`), never by pruning.
 */
interface ArrivalResolutionRecord {

    fun stamp(atMs: Long, latitude: Double, longitude: Double)

    /** The latest stamp, or null when none was ever written / the record is unparseable. */
    fun latest(): ArrivalResolution?
}

data class ArrivalResolution(
    val atMs: Long,
    val latitude: Double,
    val longitude: Double,
)
