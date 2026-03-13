package io.apptolast.paparcar.ios.stub

import io.apptolast.paparcar.domain.service.GeofenceEvent
import io.apptolast.paparcar.domain.service.GeofenceManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class StubGeofenceManager : GeofenceManager {
    override suspend fun createGeofence(
        geofenceId: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun removeGeofence(geofenceId: String): Result<Unit> = Result.success(Unit)

    override fun getGeofenceEvents(): Flow<GeofenceEvent> = emptyFlow()
}
