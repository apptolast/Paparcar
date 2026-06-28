@file:OptIn(ExperimentalTime::class)

package io.apptolast.paparcar.data.datasource

import io.apptolast.paparcar.domain.location.LocationDataSource
import io.apptolast.paparcar.domain.location.UserLocationUi
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

    override fun observeUiLocation(): Flow<UserLocationUi> = flow {
        var bearing = 0f
        while (true) {
            emit(
                UserLocationUi(
                    latitude = mockLocation.latitude,
                    longitude = mockLocation.longitude,
                    accuracy = mockLocation.accuracy,
                    speed = 8f,
                    bearingDegrees = bearing,
                ),
            )
            bearing = (bearing + 15f) % 360f // slow spin so the puck rotation is visible in mocks
            delay(1.seconds)
        }
    }

    override suspend fun getLastKnownLocation(): GpsPoint =
        mockLocation.copy(timestamp = Clock.System.now().toEpochMilliseconds())
}
