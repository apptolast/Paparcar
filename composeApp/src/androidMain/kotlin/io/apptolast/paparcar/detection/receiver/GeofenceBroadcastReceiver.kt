package io.apptolast.paparcar.detection.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.google.android.gms.location.GeofencingEvent
import io.apptolast.paparcar.detection.worker.DepartureDetectionWorker
import io.apptolast.paparcar.domain.service.GeofenceEvent
import io.apptolast.paparcar.domain.service.GeofenceEventBus
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Receives Android Geofencing API transitions.
 *
 * On GEOFENCE_EXIT:
 * 1. Emits a [GeofenceEvent.Exited] into [GeofenceEventBus] so any in-process
 *    observer (e.g. a ViewModel) can react immediately.
 * 2. Enqueues [DepartureDetectionWorker] via WorkManager to decide — combining the
 *    geofence-exit signal with the IN_VEHICLE_ENTER signal from [ActivityTransitionReceiver]
 *    and a live speed reading — whether the user actually drove away in their own car.
 *    Only if confirmed does [DepartureDetectionWorker] enqueue [ReportSpotWorker].
 *
 * Registration: AndroidManifest.xml (exported=false — system delivers via PendingIntent).
 */
@OptIn(ExperimentalTime::class)
class GeofenceBroadcastReceiver : BroadcastReceiver(), KoinComponent {

    private val geofenceEventBus: GeofenceEventBus by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return

        if (geofencingEvent.hasError()) {
            geofenceEventBus.emit(
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
            geofenceEventBus.emit(
                GeofenceEvent.Exited(
                    geofenceId = geofence.requestId,
                    timestamp = now,
                )
            )

            // 2 — Enqueue departure check. DepartureDetectionWorker combines this exit event
            //     with the IN_VEHICLE_ENTER signal to decide whether to publish the spot.
            WorkManager.getInstance(context).enqueueUniqueWork(
                "${DepartureDetectionWorker.TAG}_${geofence.requestId}",
                ExistingWorkPolicy.REPLACE,
                DepartureDetectionWorker.buildRequest(
                    geofenceId = geofence.requestId,
                    exitTimestampMs = now,
                ),
            )
        }
    }
}