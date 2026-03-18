package io.apptolast.paparcar.ios.stub

import io.apptolast.paparcar.data.datasource.platform.PlatformLocationDataSource
import io.apptolast.paparcar.domain.model.GpsPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class StubLocationDataSource : PlatformLocationDataSource {
    override fun observeHighAccuracyLocation(): Flow<GpsPoint> = emptyFlow()
    override fun observeBalancedLocation(): Flow<GpsPoint> = emptyFlow()
}