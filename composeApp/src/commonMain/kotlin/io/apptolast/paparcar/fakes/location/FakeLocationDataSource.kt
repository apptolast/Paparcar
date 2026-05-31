@file:OptIn(ExperimentalTime::class)

package io.apptolast.paparcar.data.datasource

import io.apptolast.paparcar.domain.location.LocationDataSource
import io.apptolast.paparcar.domain.model.GpsPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

class FakeLocationDataSource : LocationDataSource {
    private val mockLocation = GpsPoint(
        latitude = 36.5928,
        longitude = -6.2319,
        accuracy = 8.5f,
        timestamp = Clock.System.now().toEpochMilliseconds(),
        speed = 0f
    )

    override fun observeBalancedLocation(): Flow<GpsPoint> = flow {
        while (true) {
            emit(mockLocation.copy(timestamp = Clock.System.now().toEpochMilliseconds()))
            delay(3.seconds)
        }
    }

    override fun observeHighAccuracyLocation(): Flow<GpsPoint> = flow {
        while (true) {
            emit(mockLocation.copy(timestamp = Clock.System.now().toEpochMilliseconds()))
            delay(1.seconds)
        }
    }
}
