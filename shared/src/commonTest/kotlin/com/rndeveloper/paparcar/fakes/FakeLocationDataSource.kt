package com.rndeveloper.paparcar.fakes

import com.rndeveloper.paparcar.domain.location.LocationDataSource
import com.rndeveloper.paparcar.domain.location.UserLocationUi
import com.rndeveloper.paparcar.domain.model.GpsPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeLocationDataSource : LocationDataSource {

    private val _balanced = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
    private val _highAccuracy = MutableSharedFlow<GpsPoint>(extraBufferCapacity = 64)
    private val _uiLocation = MutableSharedFlow<UserLocationUi>(extraBufferCapacity = 64)

    override fun observeBalancedLocation(): Flow<GpsPoint> = _balanced
    override fun observeHighAccuracyLocation(): Flow<GpsPoint> = _highAccuracy
    override fun observeUiLocation(): Flow<UserLocationUi> = _uiLocation
    override fun observePassiveLocation(): Flow<GpsPoint> = emptyFlow()

    /** Settable cached fix returned by [getLastKnownLocation]; null by default. */
    var lastKnown: GpsPoint? = null

    override suspend fun getLastKnownLocation(): GpsPoint? = lastKnown

    suspend fun emitBalanced(point: GpsPoint) = _balanced.emit(point)
    suspend fun emitHighAccuracy(point: GpsPoint) = _highAccuracy.emit(point)
    suspend fun emitUi(location: UserLocationUi) = _uiLocation.emit(location)
}
