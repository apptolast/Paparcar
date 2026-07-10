package io.apptolast.paparcar.detection.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import io.apptolast.paparcar.detection.GeofenceManagerImpl
import io.apptolast.paparcar.domain.util.PaparcarLogger

/**
 * Pure-logging witness of every geofence EXIT emission. [DET-EXIT-WITNESS-001]
 *
 * The main EXIT is delivered straight to the detection service via
 * `PendingIntent.getForegroundService` (the BUG-FGS-001 mechanism: Play Services grants the
 * privileged start a background receiver legally cannot). That lane has one blind spot: if the
 * OS ever rejects the privileged start, the event vanishes with no trace on our side — field
 * forensics then cannot distinguish "Play Services never emitted" (Oppo 2026-07-09 12:55 trip)
 * from "emitted but swallowed at the handoff".
 *
 * This receiver rides a twin witness fence (same region, same EXIT transition, broadcast
 * delivery) and does exactly ONE thing: leave a line in parkdiag. Reading the field log:
 *  - witness line + Service `onStartCommand ACTION_GEOFENCE_EXIT` within seconds → lane healthy.
 *  - witness line ALONE → the privileged service start was swallowed: the receiver-first
 *    delivery refactor earns its cost.
 *  - neither → Play Services never emitted; no app-side plumbing would have helped.
 *
 * Decision-free BY CONTRACT: no bus, no workers, no repository, no service start (the last one
 * both by design and by law — starting an FGS here is BUG-FGS-001's crash).
 */
class GeofenceExitWitnessReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            PaparcarLogger.w(TAG, "⚠ geofencing error code=${event.errorCode}")
            return
        }
        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_EXIT) return
        val loc = event.triggeringLocation
        val ids = event.triggeringGeofences
            ?.joinToString { it.requestId.removePrefix(GeofenceManagerImpl.WITNESS_ID_PREFIX).take(8) }
            ?: "?"
        PaparcarLogger.d(
            TAG,
            "⚑ EXIT emitted geof=$ids loc=${loc?.latitude ?: "?"},${loc?.longitude ?: "?"} " +
                "acc=${loc?.accuracy ?: "?"}m fixAge=${loc?.let { System.currentTimeMillis() - it.time } ?: "?"}ms",
        )
    }

    private companion object {
        const val TAG = "PARKDIAG/ExitWitness"
    }
}
