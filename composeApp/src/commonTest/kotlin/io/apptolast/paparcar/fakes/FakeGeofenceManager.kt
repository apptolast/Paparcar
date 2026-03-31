package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.service.GeofenceEvent
import io.apptolast.paparcar.domain.service.GeofenceManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class FakeGeofenceManager : GeofenceManager {

    var createGeofenceCallCount = 0
    var lastCreatedGeofenceId: String? = null
    var createResult: Result<Unit> = Result.success(Unit)

    override suspend fun createGeofence(
        geofenceId: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float,
    ): Result<Unit> {
        createGeofenceCallCount++
        lastCreatedGeofenceId = geofenceId
        return createResult
    }

    override suspend fun removeGeofence(geofenceId: String): Result<Unit> =
        Result.success(Unit)

    override fun getGeofenceEvents(): Flow<GeofenceEvent> = emptyFlow()
}
