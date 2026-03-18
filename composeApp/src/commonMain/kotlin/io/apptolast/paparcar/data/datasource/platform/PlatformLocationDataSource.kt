package io.apptolast.paparcar.data.datasource.platform

import io.apptolast.paparcar.domain.model.GpsPoint
import kotlinx.coroutines.flow.Flow

interface PlatformLocationDataSource {

    fun observeHighAccuracyLocation(): Flow<GpsPoint>

    fun observeBalancedLocation(): Flow<GpsPoint>
}