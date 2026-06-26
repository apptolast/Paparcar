package io.apptolast.paparcar.domain.service

import kotlinx.coroutines.flow.Flow

interface GeofenceManager {
    suspend fun createGeofence(
        geofenceId: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float,
    ): Result<Unit>

    suspend fun removeGeofence(geofenceId: String): Result<Unit>

    /**
     * Deregisters every geofence this app has registered with the OS, regardless of id.
     *
     * Geofences live in Play Services / CoreLocation, not in Room, so wiping local storage
     * (sign-out, account switch) does not remove them — they would keep monitoring and could
     * fire an exit transition under the next user's session. This is the single teardown hook
     * that guarantees session isolation at the OS level. [SESSION-ISOLATION-001]
     */
    suspend fun removeAllGeofences(): Result<Unit>

    fun getGeofenceEvents(): Flow<GeofenceEvent>
}

sealed class GeofenceEvent {
    data class Exited(val geofenceId: String, val timestamp: Long) : GeofenceEvent()
    data class Error(val error: String, val timestamp: Long) : GeofenceEvent()
}
