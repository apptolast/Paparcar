package com.rndeveloper.paparcar.domain.usecase.fuel

import com.rndeveloper.paparcar.domain.analytics.AnalyticsEvent
import com.rndeveloper.paparcar.fakes.FakeAnalyticsTracker
import com.rndeveloper.paparcar.fakes.FakeFuelWatchRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001]
 *  [ANALYTICS-FUEL-HISTORY-ALERTS-COVERAGE-001] */
class ToggleFollowFuelStationUseCaseTest {

    @Test
    fun should_reportFollowedTrue_when_aNewStationIsFollowed() = runTest {
        val repo = FakeFuelWatchRepository()
        val tracker = FakeAnalyticsTracker()
        val useCase = ToggleFollowFuelStationUseCase(repository = repo, analyticsTracker = tracker)

        val outcome = useCase(
            stationKey = "osm:1",
            source = "osm",
            id = "1",
            name = "Repsol",
            latitude = 40.0,
            longitude = -3.0,
        )

        assertEquals(FollowToggleOutcome.FOLLOWED, outcome.getOrThrow())
        val event = tracker.events.filterIsInstance<AnalyticsEvent.FuelStationFollowToggled>().single()
        assertEquals(true, event.followed)
        assertEquals(1, tracker.events.size)
    }

    @Test
    fun should_reportFollowedFalse_when_anAlreadyFollowedStationIsUnfollowed() = runTest {
        val repo = FakeFuelWatchRepository()
        val tracker = FakeAnalyticsTracker()
        val useCase = ToggleFollowFuelStationUseCase(repository = repo, analyticsTracker = tracker)
        useCase(stationKey = "osm:1", source = "osm", id = "1", name = "Repsol", latitude = 40.0, longitude = -3.0)
        tracker.events.clear()

        val outcome = useCase(
            stationKey = "osm:1",
            source = "osm",
            id = "1",
            name = "Repsol",
            latitude = 40.0,
            longitude = -3.0,
        )

        assertEquals(FollowToggleOutcome.UNFOLLOWED, outcome.getOrThrow())
        val event = tracker.events.filterIsInstance<AnalyticsEvent.FuelStationFollowToggled>().single()
        assertEquals(false, event.followed)
        assertEquals(1, tracker.events.size)
    }
}
