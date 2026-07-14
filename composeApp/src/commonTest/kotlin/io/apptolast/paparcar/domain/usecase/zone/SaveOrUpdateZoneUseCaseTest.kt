package io.apptolast.paparcar.domain.usecase.zone

import io.apptolast.paparcar.domain.model.Zone
import io.apptolast.paparcar.fakes.FakeAuthRepository
import io.apptolast.paparcar.fakes.FakeZoneRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SaveOrUpdateZoneUseCaseTest {

    private val zoneRepo = FakeZoneRepository()
    private val useCase = SaveOrUpdateZoneUseCase(
        repository = zoneRepo,
        saveZone = SaveZoneUseCase(zoneRepo, FakeAuthRepository(FakeAuthRepository.authenticatedSession())),
    )

    private val existing = Zone(
        id = "zone-1",
        userId = "user-1",
        name = "Casa",
        lat = 40.0,
        lon = -3.0,
        iconKey = "home",
        createdAt = 1_000L,
        radiusMeters = 100f,
        isPrivate = false,
    )

    @Test
    fun should_create_a_new_zone_when_not_editing() = runTest {
        val result = useCase(
            editingZoneId = null,
            name = "  Trabajo  ",
            lat = 41.0,
            lon = -3.5,
            iconKey = "work",
            radiusMeters = 150f,
            isPrivate = true,
        )

        assertTrue(result.isSuccess)
        val saved = zoneRepo.savedZone!!
        assertEquals("Trabajo", saved.name) // trimmed
        assertEquals("work", saved.iconKey)
        assertEquals(true, saved.isPrivate)
    }

    @Test
    fun should_update_in_place_preserving_identity_when_editing() = runTest {
        zoneRepo.zones = listOf(existing)

        val result = useCase(
            editingZoneId = "zone-1",
            name = "Casa nueva",
            lat = 42.0,
            lon = -4.0,
            iconKey = "star",
            radiusMeters = 200f,
            isPrivate = true,
        )

        assertTrue(result.isSuccess)
        val saved = zoneRepo.savedZone!!
        assertEquals("zone-1", saved.id)
        assertEquals(1_000L, saved.createdAt) // identity + creation stamp preserved
        assertEquals("user-1", saved.userId)
        assertEquals("Casa nueva", saved.name)
        assertEquals(42.0, saved.lat)
        assertEquals(200f, saved.radiusMeters)
        assertTrue(saved.isPrivate)
    }

    @Test
    fun should_fail_when_the_edited_zone_vanished() = runTest {
        zoneRepo.zones = emptyList()

        val result = useCase(
            editingZoneId = "zone-1",
            name = "Casa",
            lat = 40.0,
            lon = -3.0,
            iconKey = "home",
            radiusMeters = 100f,
            isPrivate = false,
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun should_fail_when_the_name_is_blank() = runTest {
        val result = useCase(
            editingZoneId = null,
            name = "   ",
            lat = 40.0,
            lon = -3.0,
            iconKey = "home",
            radiusMeters = 100f,
            isPrivate = false,
        )

        assertTrue(result.isFailure)
    }
}
