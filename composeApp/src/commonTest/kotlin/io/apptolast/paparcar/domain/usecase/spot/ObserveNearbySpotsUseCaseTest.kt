package io.apptolast.paparcar.domain.usecase.spot

import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.SpotType
import io.apptolast.paparcar.fakes.FakeSpotRepository
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ObserveNearbySpotsUseCaseTest {

    private val location = GpsPoint(
        latitude = 40.416775,
        longitude = -3.703790,
        accuracy = 8f,
        timestamp = 0L,
        speed = 0f,
    )

    @Test
    fun `should_emitSpots_from_repository`() = runTest {
        val repo = FakeSpotRepository()
        val useCase = ObserveNearbySpotsUseCase(repo)
        val spot = buildSpot("spot-1")
        repo.spots = listOf(spot)

        val result = useCase(location, ObserveNearbySpotsUseCase.DEFAULT_SEARCH_RADIUS_METERS).first()

        assertEquals(listOf(spot), result)
    }

    @Test
    fun `should_emitEmptyList_when_repositoryHasNoSpots`() = runTest {
        val repo = FakeSpotRepository()
        val useCase = ObserveNearbySpotsUseCase(repo)

        val result = useCase(location, ObserveNearbySpotsUseCase.DEFAULT_SEARCH_RADIUS_METERS).first()

        assertEquals(emptyList(), result)
    }

    @Test
    fun `should_reflectRepositoryUpdates`() = runTest {
        val repo = FakeSpotRepository()
        val useCase = ObserveNearbySpotsUseCase(repo)
        val spot = buildSpot("spot-1")
        val flow = useCase(location, ObserveNearbySpotsUseCase.DEFAULT_SEARCH_RADIUS_METERS)

        assertEquals(emptyList(), flow.first())
        repo.spots = listOf(spot)
        assertEquals(listOf(spot), flow.first())
    }

    @Test
    fun `should_propagateRepositoryError_to_caller`() = runTest {
        val repo = FakeSpotRepository().apply {
            observeError = RuntimeException("Network unavailable")
        }
        val useCase = ObserveNearbySpotsUseCase(repo)

        var caughtError: Throwable? = null
        useCase(location, ObserveNearbySpotsUseCase.DEFAULT_SEARCH_RADIUS_METERS)
            .catch { caughtError = it }
            .toList()

        assertNotNull(caughtError)
        assertEquals("Network unavailable", caughtError!!.message)
    }

    private fun buildSpot(id: String) = Spot(
        id = id,
        location = location,
        reportedBy = "user-1",
        type = SpotType.AUTO_DETECTED,
        confidence = 1f,
    )
}
