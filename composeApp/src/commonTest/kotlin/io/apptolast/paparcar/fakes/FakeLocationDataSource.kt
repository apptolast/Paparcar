package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.location.LocationDataSource
import io.apptolast.paparcar.domain.model.GpsPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeLocationDataSource : LocationDataSource {

    private val _balanced = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
    private val _highAccuracy = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)

    override fun observeBalancedLocation(): Flow<GpsPoint> = _balanced
    override fun observeHighAccuracyLocation(): Flow<GpsPoint> = _highAccuracy

    suspend fun emitBalanced(point: GpsPoint) = _balanced.emit(point)
    suspend fun emitHighAccuracy(point: GpsPoint) = _highAccuracy.emit(point)
}
