package com.rndeveloper.paparcar.data.parkings

import com.rndeveloper.paparcar.domain.parkings.PublicParkingsQuery
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsResult
import com.rndeveloper.paparcar.domain.parkings.PublicParkingsSource

/**
 * L2: what there is instead of nothing. [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001]
 *
 * ⛔ **Today's answer wins whenever there is one.** The cheap version of this class checks the disk
 * first "to save a request", and it would quietly turn a live map into a stale one:
 * [parkingsNear] therefore always asks, and only falls back when the ask failed.
 *
 * ⚠️ That rule used to be written as *"the disk is read only when the live answer fails"*, which was
 * one sentence doing two jobs. The second job — *never LOOK at the disk before the network* — bought
 * nothing and cost the whole wait, so [remembered] now exposes the saved disc to callers that want
 * something on screen while they ask. See its own note.
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
        return remembered(query)?.let { Result.success(it) } ?: live
    }

    /**
     * The disc the disk can answer for, with the age of the weakest claim in it.
     * [PARKINGS-A-REMEMBERED-MAP-ANSWERS-BEFORE-THE-NETWORK-001]
     *
     * ⛔ **This does not weaken the rule above it.** *"The disk is read only when the live answer
     * fails"* was one sentence doing two jobs, and only one of them was ever true:
     * - *today's answer WINS whenever there is one* — untouched. [parkingsNear] still returns the
     *   live result, and the disk never overrides it.
     * - *the disk may not be LOOKED AT before the network* — that half bought nothing and cost the
     *   whole wait. A list that is already on this phone is not made truer by being withheld for
     *   eight seconds.
     *
     * The caller is what keeps the two honest: it paints this while the ask is in flight and
     * replaces it with whatever [parkingsNear] says. And the answer carries `rememberedAtMs`, so
     * nothing downstream can mistake it for something that just came off the wire.
     */
    override suspend fun remembered(query: PublicParkingsQuery): PublicParkingsResult? =
        disk.get(query)?.let {
            PublicParkingsResult(parkings = it.parkings, rememberedAtMs = it.fetchedAt)
        }
}
