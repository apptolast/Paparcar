package io.apptolast.paparcar.detection

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import io.apptolast.paparcar.detection.service.CoordinatorDetectionService
import io.apptolast.paparcar.domain.service.GeofenceEvent
import io.apptolast.paparcar.domain.service.GeofenceEventBus
import io.apptolast.paparcar.domain.service.GeofenceManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

class GeofenceManagerImpl(
    private val context: Context,
    private val geofencingClient: GeofencingClient,
    private val geofenceEventBus: GeofenceEventBus,
) : GeofenceManager {

    @SuppressLint("MissingPermission")
    override suspend fun createGeofence(
        geofenceId: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float,
    ): Result<Unit> = runCatching {
        val geofence = Geofence.Builder()
            .setRequestId(geofenceId)
            .setCircularRegion(latitude, longitude, radiusMeters)
            .setExpirationDuration(GEOFENCE_TTL_MS)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
            .setLoiteringDelay(LOITERING_DELAY_MS)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(NO_INITIAL_TRIGGER)
            .addGeofence(geofence)
            .build()

        geofencingClient.addGeofences(request, buildPendingIntent()).await()
    }

    override suspend fun removeGeofence(geofenceId: String): Result<Unit> = runCatching {
        geofencingClient.removeGeofences(listOf(geofenceId)).await()
    }

    override fun getGeofenceEvents(): Flow<GeofenceEvent> = geofenceEventBus.events

    private fun buildPendingIntent(): PendingIntent {
        // [DET-G-01] Deliver the geofence transition DIRECTLY to the detection service via
        // getForegroundService, so Play Services grants the privileged FGS start (the same mechanism
        // the AR IN_VEHICLE path uses — see ActivityRecognitionManagerImpl / BUG-FGS-001). This is
        // what lets the geofence exit BOTH dispatch departure AND arm the next detection: a
        // background BroadcastReceiver/Worker cannot legally start an FGS on Android 12+.
        // GeofenceBroadcastReceiver is kept as a one-line-revertible fallback (swap back to getBroadcast).
        val intent = Intent(context, CoordinatorDetectionService::class.java).apply {
            action = CoordinatorDetectionService.ACTION_GEOFENCE_EXIT
        }
        // FLAG_MUTABLE is required: Play Services fills GeofencingEvent extras into the intent at
        // delivery time. FLAG_IMMUTABLE blocks this on Android 12+ — triggeringGeofences would be null.
        return PendingIntent.getForegroundService(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val REQUEST_CODE = 9100
        const val LOITERING_DELAY_MS = 60_000
        /** Suppress the initial dwell trigger when registering a geofence. */
        const val NO_INITIAL_TRIGGER = 0
        /** Geofences self-destruct after 24 h. GeofenceJanitorWorker re-registers any that
         *  are still needed (active parking session), preventing orphan accumulation. [GEOF-001] */
        const val GEOFENCE_TTL_MS = 24L * 60 * 60 * 1_000
    }
}
