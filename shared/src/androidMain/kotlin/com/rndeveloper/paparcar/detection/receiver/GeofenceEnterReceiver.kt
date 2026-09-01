package com.rndeveloper.paparcar.detection.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.rndeveloper.paparcar.detection.GeofenceManagerImpl
import com.rndeveloper.paparcar.detection.worker.ParkingSafetyNetWorker
import com.rndeveloper.paparcar.domain.util.PaparcarLogger

/**
 * Receives the twin ENTER fence transition — the user walked back to their parked car.
 * [DET-RETURN-ANCHOR-001]
 *
 * This is the missing half of the parked-session lifecycle: parking seals the anchor, but the
 * typical return happens HOURS later, when the anchor has expired and the EXIT fence may sit
 * poisoned OUTSIDE (a walking-EXIT consumed it and the re-entry went unobserved in Doze) — the
 * subsequent drive-away then emits nothing (field 2026-07-07, both devices). The re-entry itself
 * is the one observable moment that repairs both, and it rides a PendingIntent, so it fires even
 * with the process dead.
 *
 * Deliberately does almost nothing: enqueue the gated safety-net check. The check samples a FRESH
 * fix; landing inside the fence cures the geofence state INSIDE and re-seals the anchor
 * (time + step zero-point). No arming, no service, no decision here.
 */
class GeofenceEnterReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            PaparcarLogger.w(TAG, "geofencing error code=${event.errorCode}")
            return
        }
        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return
        val ids = event.triggeringGeofences?.joinToString { it.requestId } ?: "?"
        PaparcarLogger.d(TAG, "✓ re-entered own fence ($ids) → enqueueing anchor re-seal check")
        // [DET-A-RELEASED-PIN-TAKES-ITS-FENCES-WITH-IT-001] Carry the BASE ids of the fences that
        // fired: the check sweeps the ones with no session left (a released pin whose removal
        // failed in silence — field 2026-08-31, enter_d194668c firing 12 min after its release).
        // Still no decision HERE — the receiver stays the dumb relay it was built to be; the
        // worker owns the session read, and its fail-open on a broken read owns the 07-11 lesson.
        val baseIds = event.triggeringGeofences.orEmpty().map {
            it.requestId.removePrefix(GeofenceManagerImpl.ENTER_ID_PREFIX)
        }
        ParkingSafetyNetWorker.enqueueCheckNow(
            WorkManager.getInstance(context),
            source = ParkingSafetyNetWorker.SOURCE_GEOFENCE_ENTER,
            triggeringFenceIds = baseIds,
        )
    }

    private companion object {
        const val TAG = "PARKDIAG/FenceEnter"
    }
}
