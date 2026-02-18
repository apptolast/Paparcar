package io.apptolast.paparcar.data.repository

import io.apptolast.paparcar.data.datasource.local.LocalLocationDataSource
import io.apptolast.paparcar.data.datasource.local.room.LocationEntity
import io.apptolast.paparcar.data.datasource.platform.PlatformLocationDataSource
import io.apptolast.paparcar.domain.model.SpotLocation
import io.apptolast.paparcar.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow

class LocationRepositoryImpl(
    private val platformLocationDataSource: PlatformLocationDataSource,
    private val localLocationDataSource: LocalLocationDataSource
) : LocationRepository {

    override fun locationFlow(): Flow<SpotLocation> {
        return platformLocationDataSource.observeLocation()
    }

    override suspend fun saveLocation(location: SpotLocation): Result<Unit> = runCatching {
        localLocationDataSource.insert(
            LocationEntity(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy, // Corregido
                timestamp = location.timestamp
            )
        )
    }

    override suspend fun getStoredLocations(): Result<List<SpotLocation>> = runCatching {
        localLocationDataSource.getAll().map {
            SpotLocation(
                latitude = it.latitude,
                longitude = it.longitude,
                accuracy = it.accuracy, // Corregido
                timestamp = it.timestamp
            )
        }
    }

    override suspend fun clearLocations(): Result<Unit> = runCatching {
        localLocationDataSource.deleteAll()
    }
}
