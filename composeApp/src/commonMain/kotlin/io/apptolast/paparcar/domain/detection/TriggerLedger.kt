package io.apptolast.paparcar.domain.detection

/**
 * [DET-EVERY-TRIGGER-LEAVES-A-TRACE-001] What happened to a trigger the moment it reached the
 * service — the vocabulary that makes *«todo trigger dispara SIEMPRE»* checkable instead of
 * merely asserted.
 *
 * Until this existed, only the arms were observable: a `SessionStarted` said a trigger got through,
 * and **every way of dying was silent in remote** (04 §2). A field session where "detection never
 * started" looked identical whether the OEM ate the broadcast, the strategy gate stood the
 * coordinator down, permissions had been revoked, or a re-arm guard did exactly its job. The
 * provenance rule exists to prevent that confusion [DET-PIN-PROVENANCE-001]; this is it, applied
 * one step earlier — to the trigger rather than to the pin.
 *
 * Each constant names the branch it replaces, so the mapping to 04 §2 stays checkable.
 */
enum class TriggerDisposition {

    /** The trigger armed a coordinator session. Its `SessionStarted` follows. */
    ARMED,

    /** [DET-STRATEGY-GATE-001] `coordinatorMayArm` said another strategy owns detection (BT-paired
     *  vehicle). Field 2026-08-01 — the Kamiq's trips pinned on the Focus — would have been read
     *  off this event instead of reconstructed. (04 §2.2) */
    REFUSED_STRATEGY,

    /** The trigger arrived and died on missing location permission. The user revoking background
     *  location was previously visible only in logcat. (04 §2.5) */
    REFUSED_PERMISSIONS,

    /** [DET-STOP-BUTTON-001] The quiet period the user opened by tapping "Parar detección" is
     *  still running. Previously emitted as an ad-hoc `Decision`; it joins the lane here. */
    SUPPRESSED_USER_STOP,

    /** [DET-AR-REARM-001] A session was already running in the same area and this trigger did not
     *  supersede it — correct behaviour, previously invisible. The supersede branch DOES trace
     *  (`SessionSuperseded`); only the suppression was mute. (04 §2.3) */
    SUPPRESSED_REARM,

    /** [DET-AR-FIRST-001] The AR ENTER evaluator declined to arm (`NoSession` / `StaleEnter` /
     *  `NoFix` / `TickOnly`). A re-delivered ENTER correctly discarded left no evidence it ever
     *  arrived — which is exactly what an OEM-eaten trigger also looks like. (04 §2.4) */
    NOT_ARMABLE,

    /** The geofence EXIT's session lookup FAILED (indeterminate, never treated as orphan — field
     *  2026-07-11 00:38). Mute until now; `OrphanCleaned` only covers the confirmed-orphan
     *  case. (04 §2.6) */
    LOOKUP_FAILED,

    /** The EXIT belonged to a fence with no session behind it. The fence removal already traces as
     *  `OrphanCleaned`; this records the other half — that a trigger arrived and died here. */
    ORPHAN,
}

private const val MS_PER_DAY = 24L * 60L * 60L * 1_000L

/**
 * [DET-EVERY-TRIGGER-LEAVES-A-TRACE-001] The session id every [TriggerDisposition] event is filed
 * under: one **daily ledger per device**, not one synthetic id per event.
 *
 * This is a correctness requirement, not tidiness. The remote store's retention sweep queries
 * `sessions` by `startedAt`, so events written under a session document that was never created are
 * **unreachable and never deleted** (`FirestoreDetectionEventLogger`, DIAG-RETENTION-001 —
 * the KDoc there already admits the leak for the departure lane). The pre-existing
 * `ARM_SUPPRESSED_USER_STOP` trace leaks exactly one such orphan per suppression. Filing seven
 * dispositions the same way would have multiplied that leak by every refused trigger on the device,
 * forever — a telemetry ticket quietly creating a storage defect.
 *
 * A day bucket gives the lane a real parent (one header write per day per process), makes the whole
 * thing collectable by the sweep that already exists, and happens to be the shape the data wants:
 * one document per device-day holding every trigger and what became of it.
 */
fun triggerLedgerSessionId(timestampMs: Long): String = "triggers_${timestampMs / MS_PER_DAY}"

/** Start-of-bucket epoch-ms for [timestampMs] — the ledger header's `startedAt`, which is the field
 *  the retention sweep compares against its cutoff. */
fun triggerLedgerStartedAtMs(timestampMs: Long): Long = (timestampMs / MS_PER_DAY) * MS_PER_DAY
