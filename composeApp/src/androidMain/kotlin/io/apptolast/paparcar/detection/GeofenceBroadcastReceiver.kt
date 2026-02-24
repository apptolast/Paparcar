package io.apptolast.paparcar.detection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.google.android.gms.location.GeofencingEvent
import io.apptolast.paparcar.detection.workers.ReportSpotWorker
import io.apptolast.paparcar.domain.service.GeofenceEvent
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Receives Android Geofencing API transitions.
 *
 * On GEOFENCE_EXIT:
 * 1. Emits a [GeofenceEvent.Exited] into [GeofenceManagerImpl]'s internal SharedFlow
 *    so any in-process observer (e.g. a ViewModel) can react immediately.
 * 2. Enqueues [ReportSpotWorker] via WorkManager for guaranteed delivery of the
 *    "spot released" report to Firebase, even if the process dies.
 *
 * Registration: AndroidManifest.xml (exported=false — system delivers via PendingIntent).
 */
@OptIn(ExperimentalTime::class)
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return

        if (geofencingEvent.hasError()) {
            GeofenceManagerImpl.emitEvent(
                GeofenceEvent.Error(
                    error = "GeofencingEvent error code: ${geofencingEvent.errorCode}",
                    timestamp = Clock.System.now().toEpochMilliseconds(),
                )
            )
            return
        }

        val triggeringGeofences = geofencingEvent.triggeringGeofences ?: return
        val now = Clock.System.now().toEpochMilliseconds()

        for (geofence in triggeringGeofences) {
            // 1 — Propagate to in-process observers (ViewModels, etc.)
            GeofenceManagerImpl.emitEvent(
                GeofenceEvent.Exited(
                    geofenceId = geofence.requestId,
                    timestamp = now,
                )
            )

            // 2 — Schedule guaranteed Firebase report via WorkManager.
            //     REPLACE: if the worker is already queued/running for this session, restart it.
            WorkManager.getInstance(context).enqueueUniqueWork(
                "${ReportSpotWorker.TAG}_${geofence.requestId}",
                ExistingWorkPolicy.REPLACE,
                ReportSpotWorker.buildRequest(),
            )
        }
    }
}
