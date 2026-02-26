package io.apptolast.paparcar.domain.repository

import io.apptolast.paparcar.domain.model.SpotLocation
import kotlinx.coroutines.flow.Flow

interface LocationRepository {

    fun observeBalancedLocationFlow(): Flow<SpotLocation>

    fun observeHighAccuracyLocationFlow(): Flow<SpotLocation>

    suspend fun getHighAccuracyLocation(): SpotLocation?

    suspend fun saveLocation(location: SpotLocation): Result<Unit>

    suspend fun getStoredLocations(): Result<List<SpotLocation>>

    suspend fun clearLocations(): Result<Unit>
}
