package com.rndeveloper.paparcar.fakes

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.repository.SpotRepository
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

    override fun observeNearbySpots(location: GpsPoint, radiusMeters: Double): Flow<List<Spot>> {
        val error = observeError
        return if (error != null) flow { throw error } else _spots
    }

    override suspend fun reportSpotReleased(spot: Spot): Result<Unit> {
        reportCallCount++
        return reportResult
    }

    /** [DET-HANDOFF-NOT-MANUAL-001 §B.3] Spot ids withdrawn through [retractSpot], in order. */
    val retractedSpotIds = mutableListOf<String>()
    var retractResult: Result<Unit> = Result.success(Unit)

    override suspend fun retractSpot(spotId: String): Result<Unit> {
        retractedSpotIds += spotId
        return retractResult
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

    var clearCacheCallCount = 0
        private set
    var clearCacheResult: Result<Unit> = Result.success(Unit)

    override suspend fun clearCache(): Result<Unit> {
        clearCacheCallCount++
        if (clearCacheResult.isSuccess) _spots.value = emptyList()
        return clearCacheResult
    }
}
