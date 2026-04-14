package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.repository.SpotRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

class FakeSpotRepository : SpotRepository {

    private val _spots = MutableStateFlow<List<Spot>>(emptyList())

    var spots: List<Spot>
        get() = _spots.value
        set(value) { _spots.value = value }

    /** When non-null, [observeNearbySpots] throws this error instead of emitting spots. */
    var observeError: Throwable? = null

    var reportCallCount = 0
        private set
    var reportResult: Result<Unit> = Result.success(Unit)

    override suspend fun getNearbySpots(location: GpsPoint, radiusMeters: Double): Result<List<Spot>> =
        Result.success(_spots.value)

    override fun observeNearbySpots(location: GpsPoint, radiusMeters: Double): Flow<List<Spot>> {
        val error = observeError
        return if (error != null) flow { throw error } else _spots
    }

    override suspend fun reportSpotReleased(spot: Spot): Result<Unit> {
        reportCallCount++
        return reportResult
    }

    var signalCallCount = 0
        private set
    var lastSignalAccepted: Boolean? = null
        private set
    var signalResult: Result<Unit> = Result.success(Unit)

    override suspend fun sendSpotSignal(spotId: String, accepted: Boolean): Result<Unit> {
        signalCallCount++
        lastSignalAccepted = accepted
        return signalResult
    }
}
