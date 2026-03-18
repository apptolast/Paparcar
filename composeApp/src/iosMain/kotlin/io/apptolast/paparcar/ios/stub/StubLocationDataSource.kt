package io.apptolast.paparcar.ios.stub

import io.apptolast.paparcar.domain.location.LocationDataSource
import io.apptolast.paparcar.domain.model.GpsPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class StubLocationDataSource : LocationDataSource {
    override fun observeBalancedLocation(): Flow<GpsPoint> = emptyFlow()
    override fun observeHighAccuracyLocation(): Flow<GpsPoint> = emptyFlow()
}