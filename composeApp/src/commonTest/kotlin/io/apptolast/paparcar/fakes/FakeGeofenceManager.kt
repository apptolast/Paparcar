package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.service.GeofenceEvent
import io.apptolast.paparcar.domain.service.GeofenceManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class FakeGeofenceManager : GeofenceManager {

    var createGeofenceCallCount = 0
    var lastCreatedGeofenceId: String? = null
    var lastCreatedRadiusMeters: Float? = null
    var createResult: Result<Unit> = Result.success(Unit)
    /** Every registered geofence id, in order — for asserting active-only fence ownership. */
    val createdIds: MutableList<String> = mutableListOf()

    override suspend fun createGeofence(
        geofenceId: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float,
    ): Result<Unit> {
        createGeofenceCallCount++
        lastCreatedGeofenceId = geofenceId
        lastCreatedRadiusMeters = radiusMeters
        createdIds.add(geofenceId)
        return createResult
    }

    val removedIds: MutableList<String> = mutableListOf()
    var removeResult: Result<Unit> = Result.success(Unit)

    override suspend fun removeGeofence(geofenceId: String): Result<Unit> {
        removedIds.add(geofenceId)
        return removeResult
    }

    var removeAllCallCount = 0

    override suspend fun removeAllGeofences(): Result<Unit> {
        removeAllCallCount++
        return Result.success(Unit)
    }

    override fun getGeofenceEvents(): Flow<GeofenceEvent> = emptyFlow()
}
