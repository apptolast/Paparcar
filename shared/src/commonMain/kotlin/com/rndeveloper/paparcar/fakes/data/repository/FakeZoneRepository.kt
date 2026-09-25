package com.rndeveloper.paparcar.fakes.data.repository

import com.rndeveloper.paparcar.domain.model.Zone
import com.rndeveloper.paparcar.domain.repository.ZoneRepository
import com.rndeveloper.paparcar.fakes.MockScenario
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * Mock-flavor zones.
 *
 * [SPOT-A-PRIVATE-ZONE-DECIDES-THE-PUBLICATION-NOT-A-BUTTON-001] It used to be a hard `emptyList()`
 * on every member, which made the private-zone half of the app **unreachable in the entire mock
 * set**: the release dialog could never show that leaving publishes nothing, and the history detail
 * could never show its private-zone row. A fake that can only answer one way is not a fake of a
 * repository, it is a constant — and a state nobody can reach in the mock app is a state nobody
 * checks. Scenario-aware now, like the parking and vehicle fakes.
 *
 * @param scenario null (tests, plain construction) keeps the old behaviour exactly: no zones.
 */
class FakeZoneRepository(
    private val scenario: MockScenario? = null,
) : ZoneRepository {

    /** Zones created from inside the mock app — saving one really adds it, so the chips, the
     *  editor and the private-zone gate all play the real loop. */
    private val saved = MutableStateFlow<List<Zone>>(emptyList())

    private val deleted = MutableStateFlow<Set<String>>(emptySet())

    private fun seeded(): List<Zone> =
        if (scenario?.parkedInPrivateZone?.value == true) listOf(GARAGE_SEED) else emptyList()

    private fun current(): List<Zone> =
        (seeded() + saved.value).filterNot { it.id in deleted.value }

    override fun observeZones(): Flow<List<Zone>> {
        val scenarioFlow = scenario?.parkedInPrivateZone ?: MutableStateFlow(false).asStateFlow()
        return kotlinx.coroutines.flow.combine(scenarioFlow, saved, deleted) { _, _, _ -> current() }
            .map { it }
    }

    override suspend fun syncFromRemote(userId: String): Result<Unit> = Result.success(Unit)

    override suspend fun saveZone(zone: Zone): Result<Unit> {
        saved.value = saved.value.filterNot { it.id == zone.id } + zone
        deleted.value = deleted.value - zone.id
        return Result.success(Unit)
    }

    override suspend fun deleteZone(id: String): Result<Unit> {
        deleted.value = deleted.value + id
        saved.value = saved.value.filterNot { it.id == id }
        return Result.success(Unit)
    }

    override suspend fun getPrivateZonesSnapshot(): List<Zone> = current().filter { it.isPrivate }

    override suspend fun deleteAllData(userId: String): Result<Unit> {
        saved.value = emptyList()
        return Result.success(Unit)
    }

    override suspend fun pushPendingZones(): Result<Unit> = Result.success(Unit)

    private companion object {
        /** Drawn over `FakeUserParkingRepository.ownParkedSeed` — Av. de la Concepción — so the
         *  seeded own parking falls inside it and the whole private-zone story is one toggle away:
         *  the release dialog says no plaza goes out, and the history detail names the zone. */
        val GARAGE_SEED = Zone(
            id = "mock_zone_garage",
            userId = "mock_user_001",
            name = "Home garage",
            lat = 53.3522,
            lon = -6.2622,
            iconKey = "HOME",
            createdAt = 0L,
            radiusMeters = 60f,
            isPrivate = true,
        )
    }
}
