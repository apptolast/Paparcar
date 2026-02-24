package io.apptolast.paparcar.detection

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import io.apptolast.paparcar.domain.service.GeofenceEvent
import io.apptolast.paparcar.domain.service.GeofenceService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class GeofenceManagerImpl(
    private val context: Context,
    private val geofencingClient: GeofencingClient,
) : GeofenceService {

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
            .setExpirationDuration(TimeUnit.HOURS.toMillis(24))
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
            .setLoiteringDelay(60_000)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0) // no initial trigger on dwell
            .addGeofence(geofence)
            .build()

        geofencingClient.addGeofences(request, buildPendingIntent()).await()
    }

    override suspend fun removeGeofence(geofenceId: String): Result<Unit> = runCatching {
        geofencingClient.removeGeofences(listOf(geofenceId)).await()
    }

    override fun getGeofenceEvents(): Flow<GeofenceEvent> = _events.asSharedFlow()

    private fun buildPendingIntent(): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        private const val REQUEST_CODE = 9100
        private val _events = MutableSharedFlow<GeofenceEvent>(extraBufferCapacity = 8)

        /** Called by [GeofenceBroadcastReceiver] to forward events into the flow. */
        fun emitEvent(event: GeofenceEvent) {
            _events.tryEmit(event)
        }
    }
}