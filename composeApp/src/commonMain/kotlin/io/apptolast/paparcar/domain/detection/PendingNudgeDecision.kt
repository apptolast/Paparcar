package io.apptolast.paparcar.domain.detection

/**
 * [DET-NEVER-SILENT-001] When the coordinator arms, the service persists a durable "pending
 * detection" (survives process death) and refreshes its heartbeat while the session is alive; any
 * terminal (confirm OR abort) clears it. A pending whose heartbeat has gone stale therefore means
 * the process was KILLED before the session could resolve — a park silently lost. The watchdog
 * (ParkingSafetyNetWorker) surfaces those with a "where did you park?" nudge.
 *
 * This is the pure decision of WHICH stale pending deserves the nudge:
 *  - **GEOFENCE_EXIT / MANUAL** → always: a departure from a known spot, or an explicit "I'm driving",
 *    is a real trip whose park we owe the user (leg chino→casa, field 2026-07-12).
 *  - **ARRIVAL_HANDOFF** → always, for a different reason [DET-HANDOFF-NOT-MANUAL-001]: the handoff
 *    only exists because the safety net ALREADY dispatched the departure — the session was released
 *    and the geofence removed before this arm. The user's car is therefore un-tracked whatever the
 *    truth was, so a pending that dies here costs the park exactly as a real departure would. (Once
 *    the departure stops committing before proof — §B.1 of the ticket — this collapses back into
 *    the [sawDriving] rule, because nothing will have been given up.)
 *  - **AR_VEHICLE_ENTER / SIGNIFICANT_MOTION** → only if the trip actually drove ([sawDriving], set once
 *    the session reached the park-evaluation phase). A bare boarding is falsifiable (bus/taxi); nudging
 *    a spurious ENTER that merely died would be a false "where did you park?".
 */
fun shouldNudgeForStalePending(trigger: String, sawDriving: Boolean): Boolean =
    trigger == DetectionTrigger.GEOFENCE_EXIT.name ||
        trigger == DetectionTrigger.MANUAL.name ||
        trigger == DetectionTrigger.ARRIVAL_HANDOFF.name ||
        sawDriving
