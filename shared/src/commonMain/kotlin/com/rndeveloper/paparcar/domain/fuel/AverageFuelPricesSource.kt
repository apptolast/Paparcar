package com.rndeveloper.paparcar.domain.fuel

/**
 * Where a fresher [AverageFuelPriceTable] can be fetched from.
 * [FUEL-NATIONAL-AVERAGE-FROM-OPENVAN-001]
 *
 * An interface with one implementation today, and that is on purpose rather than by accident: the
 * embedded table is a national-average feed too, just a frozen one, and the day a second provider
 * turns up — or the day the licence of this one changes — the swap must be one line of DI, not a
 * rewrite of the ladder. It is also what lets the tests drive the whole refresh with a fake instead
 * of a socket.
 *
 * ⛔ Suspending, and never called from a calculation. See [AverageFuelPricesRepository].
 */
interface AverageFuelPricesSource {

    /** The latest table the provider can offer, or a failure. A failure is not an empty table. */
    suspend fun latest(): Result<AverageFuelPriceTable>
}
