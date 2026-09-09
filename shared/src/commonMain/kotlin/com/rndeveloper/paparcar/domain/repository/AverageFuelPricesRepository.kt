package com.rndeveloper.paparcar.domain.repository

import com.rndeveloper.paparcar.domain.fuel.AverageFuelPriceTable

/**
 * The national-average fuel prices the app is currently quoting.
 * [FUEL-NATIONAL-AVERAGE-FROM-OPENVAN-001]
 *
 * **The split between these two methods IS the F5a invariant**, which is why it lives in the type
 * and not in a comment somewhere in data:
 *
 *  * [current] is what a calculation calls. Synchronous, infallible, always an answer — because the
 *    spend figure is computed from thirteen static call sites and a table that had to be awaited
 *    would have turned all of them asynchronous to buy nothing.
 *  * [refresh] is what the app calls when it has a moment. Suspending and failable, and never on
 *    the path of a calculation.
 *
 * No rung of the price ladder needs the network at the moment it is asked. That is the whole rule,
 * and a repository that only exposed a suspending read would have broken it silently.
 */
interface AverageFuelPricesRepository {

    /** The table to quote right now. Never null: the snapshot in the binary is the floor. */
    fun current(): AverageFuelPriceTable

    /**
     * Fetches a fresher table if the held one has aged out, and returns whatever is in force
     * afterwards — on failure, simply the table already held.
     *
     * There is nothing for the caller to handle. A stale average is not an error state, it is the
     * previous answer, and blanking the cost tile because a refresh timed out would be strictly
     * worse than the number it replaced.
     */
    suspend fun refresh(): AverageFuelPriceTable
}
