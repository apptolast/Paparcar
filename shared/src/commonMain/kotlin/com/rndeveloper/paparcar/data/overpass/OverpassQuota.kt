package com.rndeveloper.paparcar.data.overpass

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The one thing every Overpass lane in the app is really sharing: **our slot at the server**.
 * [OVERPASS-A-MIRROR-WALK-MUST-OUTLIVE-THE-SERVER-IT-ASKS-001]
 *
 * `overpass-api.de/api/status` says it plainly: `Rate limit: 2`. Measured against it on 14-09-2026
 * from a single IP:
 * - six asks in a row → **two answers and four 429s**, each taking 7–8,5 s to arrive;
 * - four asks **at the same time** → one 429 and **three 504s at 14,1 s**, for a query that answers
 *   in 1,4 s when it is asked alone.
 *
 * ⛔ **The limit is per IP, and an IP is not a user.** Mobile carriers put thousands of subscribers
 * behind one public address, so the budget this class guards is not even ours alone. That is why the
 * ceiling is [CONCURRENT_ASKS] and not the 2 the server advertises: taking both leaves nothing for
 * everyone else sharing the address, and the 504 that comes back costs ten times what the answer
 * would have.
 *
 * ⛔ **A process-wide singleton on purpose, not a Koin `single`.** This models a physical fact about
 * the phone — one process, one egress address, one budget — and the failure it exists to prevent is
 * five lanes each holding their own idea of the budget. A dependency five constructors must remember
 * to be handed is a dependency that will be forgotten again; this one cannot be. Tests that need an
 * isolated budget construct their own instance and pass it in.
 */
class OverpassQuota(
    permits: Int = CONCURRENT_ASKS,
    private val waitCeilingMs: Long = WAIT_CEILING_MS,
) {

    private val slots = Semaphore(permits)

    /**
     * Runs [block] holding one of our slots, or returns null when the wait itself became the
     * problem.
     *
     * Null rather than an exception because "somebody else is asking" is not a failure of this ask —
     * the caller turns it into the failure IT wants to report. And bounded rather than patient
     * because the lane that is holding the slot may be in its bad path: piling a second walk behind
     * a first one that is already timing out is how a 30 s failure becomes a 60 s one.
     */
    suspend fun <T> withSlot(block: suspend () -> T): T? {
        // `Semaphore.acquire` is atomic under cancellation: if the timeout wins, no permit was
        // taken. That is what makes the timeout safe to put here rather than around the whole block.
        if (withTimeoutOrNull(waitCeilingMs) { slots.acquire() } == null) return null
        return try {
            block()
        } finally {
            slots.release()
        }
    }

    companion object {
        /**
         * One. The server grants 2 per address and we are not the only ones at this address — see
         * the class note. One ask at a time also means a lane never competes with itself: the map
         * matcher fetches roads **during a trip** (`HomeTripController`), which is exactly when the
         * driver opens the car-parks lane on arrival.
         */
        const val CONCURRENT_ASKS = 1

        /**
         * How long a lane waits its turn before giving up.
         *
         * A whole walk is bounded by [OverpassWalkPatience.WALK_CEILING_MS] (30 s), so a lane that
         * has not got a turn in 10 s is queued behind one that is failing. Waiting the full walk out
         * would just move the failure later and make it twice as long.
         */
        const val WAIT_CEILING_MS = 10_000L
    }
}
