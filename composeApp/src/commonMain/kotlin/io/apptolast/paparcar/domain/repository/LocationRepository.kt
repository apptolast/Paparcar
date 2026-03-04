package io.apptolast.paparcar.domain.repository

import io.apptolast.paparcar.domain.model.GpsPoint
import kotlinx.coroutines.flow.Flow

interface LocationRepository {

    fun observeBalancedLocationFlow(): Flow<GpsPoint>

    fun observeHighAccuracyLocationFlow(): Flow<GpsPoint>

    suspend fun getHighAccuracyLocation(): GpsPoint?

    suspend fun saveLocation(location: GpsPoint): Result<Unit>

    suspend fun getStoredLocations(): Result<List<GpsPoint>>

    suspend fun clearLocations(): Result<Unit>
}
