package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.diagnostics.DetectionEvent
import com.rndeveloper.paparcar.domain.diagnostics.DetectionEventLogger
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.service.GeofenceManager
import com.rndeveloper.paparcar.domain.util.PaparcarLogger

/**
 * [DET-A-RELEASED-PIN-TAKES-ITS-FENCES-WITH-IT-001] **The ENTER lane's orphan-fence sweep** — the
 * mirror of the cleanup the EXIT lane has had since 2026-07-11.
 *
 * Fences are registered `NEVER_EXPIRE`, so a fence whose removal failed outlives its pin forever.
 * The EXIT lane self-heals (`orphanGeofenceIds` → remove + `OrphanCleaned`); the ENTER lane did
 * not: `GeofenceEnterReceiver` never asked whether the fence that fired still had a session, so an
 * orphaned `enter_` twin kept firing for good. Field 2026-08-31 (Oppo): pin `d194668c` released at
 * 21:22:44 with a silent removal, `enter_d194668c` still firing at 21:34:26.
 *
 * **Why the trigger is the only place this can live**: `removeGeofence` forgets the ledger entry
 * BEFORE asking GMS (deliberate — better to re-register a removed fence than skip a live one), so
 * after a failed removal no ledger remembers the fence and GMS exposes no list API. The orphan's
 * own firing is its one observable.
 *
 * **Fails OPEN by construction** — the 2026-07-11 lesson (a FAILED lookup classified a LIVE fence
 * as orphan): the caller only invokes this after a SUCCESSFUL active-sessions read; a read failure
 * returns upstream and no fence is touched.
 *
 * @param deliveredGeofenceIds BASE ids (prefix already stripped) of the fences whose ENTER fired.
 * @param activeSessions The successfully-read active sessions — the population witness.
 * @return the ids swept, for the caller's log line.
 */
suspend fun cleanOrphanEnterFences(
    deliveredGeofenceIds: List<String>,
    activeSessions: List<UserParking>,
    geofenceManager: GeofenceManager,
    detectionEventLogger: DetectionEventLogger?,
    nowMs: Long,
): List<String> {
    if (deliveredGeofenceIds.isEmpty()) return emptyList()
    val liveFenceIds = activeSessions.mapNotNull { it.geofenceId }.toSet()
    val orphans = deliveredGeofenceIds.filter { it !in liveFenceIds }
    for (id in orphans) {
        PaparcarLogger.w(TAG, "✗ ENTER from a fence with no session geof=${id.take(8)} — removing the orphan [DET-A-RELEASED-PIN-TAKES-ITS-FENCES-WITH-IT-001]")
        // Best-effort like the EXIT sweep: a failed removal stays registered and this same sweep
        // gets another chance on the fence's next firing — the retry loop is the trigger itself.
        geofenceManager.removeGeofence(id)
        detectionEventLogger?.log(DetectionEvent.OrphanCleaned(sessionId = id, timestampMs = nowMs))
    }
    return orphans
}

private const val TAG = "OrphanEnterFences"
