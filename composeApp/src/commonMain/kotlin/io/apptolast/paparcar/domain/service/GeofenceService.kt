package io.apptolast.paparcar.domain.service

import kotlinx.coroutines.flow.Flow

interface GeofenceService {
    suspend fun createGeofence(
        geofenceId: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float,
    ): Result<Unit>

    suspend fun removeGeofence(geofenceId: String): Result<Unit>

    fun getGeofenceEvents(): Flow<GeofenceEvent>
}

sealed class GeofenceEvent {
    data class Exited(val geofenceId: String, val timestamp: Long) : GeofenceEvent()
    data class Error(val error: String, val timestamp: Long) : GeofenceEvent()
}
