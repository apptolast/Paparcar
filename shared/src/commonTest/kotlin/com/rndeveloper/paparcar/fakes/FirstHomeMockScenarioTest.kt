package com.rndeveloper.paparcar.fakes

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.fakes.data.repository.FakeUserParkingRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FirstHomeMockScenarioTest {
    @Test
    fun should_haveNoParkedCarsOrHistory_when_firstHomePresetIsSelected() = runTest {
        val scenario = MockScenario().apply { firstHomeEntry.value = true }
        val repository = FakeUserParkingRepository(scenario = scenario)
        assertTrue(repository.observeActiveSessions().first().isEmpty())
        assertTrue(repository.observeAllSessions().first().isEmpty())
        assertTrue(repository.getSessionsPaged(10, 0).isEmpty())

        val session = UserParking(
            id = "first-session", vehicleId = "first-car",
            location = GpsPoint(40.0, -3.0, 5f, 0L, 0f), isActive = true,
        )
        repository.saveNewParkingSession(session)
        assertEquals(listOf(session), repository.observeActiveSessions().first())
    }

    @Test
    fun should_keepTheExistingDemoFleet_when_firstHomePresetIsNotSelected() = runTest {
        val repository = FakeUserParkingRepository(scenario = MockScenario())
        assertTrue(repository.observeActiveSessions().first().isNotEmpty())
        assertTrue(repository.observeAllSessions().first().size > 1)
    }
}
