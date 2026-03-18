package io.apptolast.paparcar.domain.repository

import io.apptolast.paparcar.domain.model.GpsPoint
import kotlinx.coroutines.flow.Flow

interface LocationRepository {

    fun observeBalancedLocationFlow(): Flow<GpsPoint>

    fun observeHighAccuracyLocationFlow(): Flow<GpsPoint>
}