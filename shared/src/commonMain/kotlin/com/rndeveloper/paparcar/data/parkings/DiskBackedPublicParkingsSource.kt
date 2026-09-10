package com.rndeveloper.paparcar.data.parkings

import com.rndeveloper.paparcar.domain.parkings.PublicParkingsQuery
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsResult
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsSource

/**
 * L2: what there is instead of nothing. [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001]
 *
 * ⛔ **The disk is read only when the live answer fails, never ahead of it.** Today's answer wins
 * whenever there is one — this layer changes what happens when there is not, and nothing else. The
 * cheap version of this class checks the disk first "to save a request", and it would quietly turn a
 * live map into a stale one.
 *
 * Sitting between the in-memory cache and the network is deliberate, and the two are not the same
 * thing wearing different clothes:
 * - `CachingPublicParkingsSource` answers **"do I ask again?"** and is measured in minutes.
 * - This one answers **"what do I show when I cannot ask?"** and is measured in weeks.
 *
 * Reusing one TTL for both questions is the mistake this split exists to avoid.
 */
class DiskBackedPublicParkingsSource(
    private val delegate: PublicParkingsSource,
    private val disk: RoomPublicParkingsCache,
) : PublicParkingsSource {

    override fun covers(countryCode: String): Boolean = delegate.covers(countryCode)

    override suspend fun parkingsNear(query: PublicParkingsQuery): Result<PublicParkingsResult> {
        val live = delegate.parkingsNear(query)

        live.onSuccess { fresh ->
            // A fresh answer is the truth about its whole disc — arrivals, survivors and, above all,
            // departures. `put` is what lets a car park removed upstream stop being served.
            disk.put(query, fresh.parkings)
            return live
        }

        // ⛔ Only now. And a miss stays the ORIGINAL failure: falling back to "no car parks here"
        // would answer a question nobody could ask.
        val remembered = disk.get(query) ?: return live
        return Result.success(
            PublicParkingsResult(
                parkings = remembered.parkings,
                rememberedAtMs = remembered.fetchedAt,
            )
        )
    }
}
