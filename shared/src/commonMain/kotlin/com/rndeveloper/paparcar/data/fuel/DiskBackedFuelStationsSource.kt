package com.rndeveloper.paparcar.data.fuel

import com.rndeveloper.paparcar.domain.fuel.FuelStationsQuery
import com.rndeveloper.paparcar.domain.fuel.FuelStationsResult
import com.rndeveloper.paparcar.domain.fuel.FuelStationsSource

/**
 * L2 for the fuel lane: what there is instead of nothing.
 * [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001]
 *
 * ⛔ **Read only when the live answer fails, never ahead of it.** Twin of
 * [com.rndeveloper.paparcar.data.parkings.DiskBackedPublicParkingsSource], and it sits in the same
 * place for the same reason: below the in-memory cache that asks "do I ask again?", above the
 * network, answering only "what do I show when I cannot ask?".
 *
 * It wraps the REGISTRY rather than any one feed, so it inherits whichever source answered — and
 * that source names itself in [FuelStationsResult.source], which is what keeps Spain's station ids
 * from colliding with OSM's in one shared table.
 */
class DiskBackedFuelStationsSource(
    private val delegate: FuelStationsSource,
    private val disk: RoomFuelStationsCache,
) : FuelStationsSource {

    override fun covers(countryCode: String): Boolean = delegate.covers(countryCode)

    override suspend fun stationsNear(query: FuelStationsQuery): Result<FuelStationsResult> {
        val live = delegate.stationsNear(query)

        live.onSuccess { fresh ->
            disk.put(query, fresh)
            return live
        }

        // ⛔ A miss stays the ORIGINAL failure. "We could not ask" must never become "there are no
        // stations near you" — the sentence the section is built to keep apart.
        val remembered = disk.get(query) ?: return live
        return Result.success(
            FuelStationsResult(
                stations = remembered.stations,
                // ⛔ Null: `reportedAt` is the FEED's own stamp for a response, and this response
                // never happened. Quoting the old feed hour here would date a list by an event that
                // did not occur. Each price still carries its own hour, which is the honest part.
                reportedAt = null,
                source = FuelStationsResult.UNKNOWN_SOURCE,
                rememberedAtMs = remembered.fetchedAt,
            )
        )
    }
}
