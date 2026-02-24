package io.apptolast.paparcar.data.datasource.platform

import io.apptolast.paparcar.domain.model.SpotLocation
import kotlinx.coroutines.flow.Flow

interface PlatformLocationDataSource {

    fun observeHighAccuracyLocation(): Flow<SpotLocation>

    fun observeBalancedLocation(): Flow<SpotLocation>

    suspend fun getHighAccuracyLocation(): SpotLocation?
}