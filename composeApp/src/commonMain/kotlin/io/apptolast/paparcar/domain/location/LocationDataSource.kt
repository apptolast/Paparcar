package io.apptolast.paparcar.domain.location

import io.apptolast.paparcar.domain.model.GpsPoint
import kotlinx.coroutines.flow.Flow

interface LocationDataSource {

    fun observeBalancedLocation(): Flow<GpsPoint>

    fun observeHighAccuracyLocation(): Flow<GpsPoint>
}