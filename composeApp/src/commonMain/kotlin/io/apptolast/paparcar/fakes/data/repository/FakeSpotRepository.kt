@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.fakes.data.repository

import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.PlaceCategory
import io.apptolast.paparcar.domain.model.PlaceInfo
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.SpotType
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.repository.SpotRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock

class FakeSpotRepository : SpotRepository {
    private val initTime = Clock.System.now().toEpochMilliseconds()

    private val mockSpots = listOf(
        Spot(
            id = "spot_mock_001",
            location = GpsPoint(36.5935, -6.2301, 5f, initTime - 3 * 60_000L, 0f), // 3 min ago
            reportedBy = "user_abc",
            type = SpotType.AUTO_DETECTED,
            confidence = 0.92f, // ALTA (Verde)
            enRouteCount = 2,
            address = AddressInfo(street = "Calle Larga 14", city = "Jerez de la Frontera", region = "Andalucía", country = "España"),
            sizeCategory = VehicleSize.MEDIUM,
        ),
        Spot(
            id = "spot_mock_002",
            location = GpsPoint(36.5910, -6.2340, 5f, initTime - 12 * 60_000L, 0f), // 12 min ago
            reportedBy = "user_def",
            type = SpotType.MANUAL_REPORT,
            confidence = 0.65f, // MEDIA (Ámbar)
            enRouteCount = 1,
            address = AddressInfo(street = "Av. Álvaro Domecq 2", city = "Jerez de la Frontera", region = "Andalucía", country = "España"),
            placeInfo = PlaceInfo("Mercadona Álvaro Domecq", PlaceCategory.SUPERMARKET),
            sizeCategory = VehicleSize.SMALL,
        ),
        Spot(
            id = "spot_mock_003",
            location = GpsPoint(36.5950, -6.2280, 5f, initTime - 45 * 60_000L, 0f), // 45 min ago
            reportedBy = "user_ghi",
            type = SpotType.AUTO_DETECTED,
            confidence = 0.45f, // BAJA (Rojo)
            enRouteCount = 0,
            address = AddressInfo(street = "Plaza del Arenal 1", city = "Jerez de la Frontera", region = "Andalucía", country = "España"),
            // intentionally no sizeCategory — exercises the "unknown" CompatibilityRow state
        ),
        Spot(
            id = "spot_mock_004",
            location = GpsPoint(36.5895, -6.2360, 5f, initTime - 7 * 60_000L, 0f), // 7 min ago
            reportedBy = "user_jkl",
            type = SpotType.AUTO_DETECTED,
            confidence = 0.85f, // ALTA (Verde)
            enRouteCount = 0,
            address = AddressInfo(street = "Calle Corredera 8", city = "Jerez de la Frontera", region = "Andalucía", country = "España"),
            sizeCategory = VehicleSize.LARGE,
        ),
        Spot(
            id = "spot_mock_005",
            location = GpsPoint(36.5965, -6.2310, 5f, initTime - 2 * 60_000L, 0f), // 2 min ago
            reportedBy = "user_mno",
            type = SpotType.MANUAL_REPORT,
            confidence = 1.0f, // FIABLE (Verde intenso)
            enRouteCount = 3,
            address = AddressInfo(street = "Calle Consistorio 12", city = "Jerez de la Frontera", region = "Andalucía", country = "España"),
            placeInfo = PlaceInfo("Repsol Consistorio", PlaceCategory.FUEL),
            sizeCategory = VehicleSize.VAN,
        ),
    )

    private val spotsFlow = MutableStateFlow(mockSpots)

    override fun observeNearbySpots(location: GpsPoint, radiusMeters: Double): Flow<List<Spot>> =
        spotsFlow.asStateFlow()

    override suspend fun reportSpotReleased(spot: Spot): Result<Unit> =
        Result.success(Unit)

    override suspend fun sendSpotSignal(spotId: String, accepted: Boolean): Result<Unit> =
        Result.success(Unit)

    override suspend fun clearCache(): Result<Unit> =
        Result.success(Unit)
}
