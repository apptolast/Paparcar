package io.apptolast.paparcar.fakes.data.repository

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.SpotType
import io.apptolast.paparcar.domain.repository.SpotRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSpotRepository : SpotRepository {
    private val mockSpots = listOf(
        Spot(
            id = "spot_mock_001",
            location = GpsPoint(36.5935, -6.2301, 5f, 0L, 0f),
            reportedBy = "user_abc",
            type = SpotType.AUTO_DETECTED,
            confidence = 0.92f, // ALTA (Verde)
            enRouteCount = 3
        ),
        Spot(
            id = "spot_mock_002",
            location = GpsPoint(36.5910, -6.2340, 5f, 0L, 0f),
            reportedBy = "user_def",
            type = SpotType.MANUAL_REPORT,
            confidence = 0.65f, // MEDIA (Ámbar)
            enRouteCount = 1
        ),
        Spot(
            id = "spot_mock_003",
            location = GpsPoint(36.5950, -6.2280, 5f, 0L, 0f),
            reportedBy = "user_ghi",
            type = SpotType.AUTO_DETECTED,
            confidence = 0.45f, // BAJA (Rojo)
            enRouteCount = 5
        ),
        Spot(
            id = "spot_mock_004",
            location = GpsPoint(36.5895, -6.2360, 5f, 0L, 0f),
            reportedBy = "user_jkl",
            type = SpotType.AUTO_DETECTED,
            confidence = 0.85f, // ALTA (Verde)
            enRouteCount = 0
        ),
        Spot(
            id = "spot_mock_005",
            location = GpsPoint(36.5965, -6.2310, 5f, 0L, 0f),
            reportedBy = "user_mno",
            type = SpotType.MANUAL_REPORT,
            confidence = 1.0f, // FIABLE (Verde intenso)
            enRouteCount = 0
        )
    )

    override fun observeNearbySpots(location: GpsPoint, radiusMeters: Double): Flow<List<Spot>> =
        MutableStateFlow(mockSpots).asStateFlow()

    override suspend fun reportSpotReleased(spot: Spot): Result<Unit> =
        Result.success(Unit)

    override suspend fun sendSpotSignal(spotId: String, accepted: Boolean): Result<Unit> =
        Result.success(Unit)

    override suspend fun clearCache(): Result<Unit> =
        Result.success(Unit)
}
