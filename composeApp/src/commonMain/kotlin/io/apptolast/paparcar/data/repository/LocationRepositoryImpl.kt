package io.apptolast.paparcar.data.repository

import io.apptolast.paparcar.data.datasource.platform.PlatformLocationDataSource
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow

class LocationRepositoryImpl(
    private val platformLocationDataSource: PlatformLocationDataSource,
) : LocationRepository {

    override fun observeBalancedLocationFlow(): Flow<GpsPoint> =
        platformLocationDataSource.observeBalancedLocation()

    override fun observeHighAccuracyLocationFlow(): Flow<GpsPoint> =
        platformLocationDataSource.observeHighAccuracyLocation()
}