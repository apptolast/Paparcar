package com.rndeveloper.paparcar.domain.fuel

import com.rndeveloper.paparcar.domain.model.Money

/**
 * What one pump charges, and **when it said so**.
 * [FUEL-FRANCE-A-PRICE-CARRIES-ITS-OWN-HOUR-001]
 *
 * The hour is inside the price, not beside the response, because in France it genuinely belongs to
 * the price. Measured on 100 Paris stations (305 prices) on 10-09-2026:
 * - age of a price: p50 = **2,2 h**, p90 = **111,8 h**, max = **9.800 h — thirteen months**
 * - spread WITHIN one station: p50 = 0,00 h, p90 = 10,5 h, max = 9.799 h
 *
 * With that tail there is no honest single stamp for a response, and the two candidates fail in
 * opposite directions: the **oldest** brands a mostly-fresh list (p50 = 2,2 h) as thirteen months
 * old, and the **newest** hides exactly the stale price the driver needs to discard. This whole
 * feature exists because a proxy of the Spanish feed ran twelve hours behind and disagreed with 30%
 * of stations — a thirteen-month tail is that same failure, larger.
 *
 * So the invariant lives in one place and cannot drift: **you cannot hold a price without its
 * hour.** A parallel `Map<FuelType, ...>` of timestamps would be the version that silently
 * misaligns. It is also the box Italy's `isSelf` goes in — 33.483 station+fuel pairs there carry
 * `self` and `servito` prices 0,36 €/L apart, which makes it part of the price's identity too.
 */
data class StationPrice(
    /** Cost of one litre at this pump. Named like [FuelPrice.perUnit] so the two read as siblings —
     *  this one is what a specific forecourt charges, that one is what the app bills a trip at. */
    val perUnit: Money,
    /**
     * Epoch millis of the moment this price was reported, or null when the source publishes none.
     *
     * Millis rather than the feed's own wording because the two feeds word it differently —
     * `"08/09/2026 23:52:46"` in Spain, `"2026-09-10T00:01:00+00:00"` in France — and showing
     * either verbatim leaks the source's mechanics into copy. As an instant it becomes an AGE,
     * which is the only thing the reader actually wants and which the app already says in nine
     * locales.
     *
     * Null is a real answer, never a zero: an unparseable date costs the stamp, not the price.
     */
    val reportedAtMs: Long? = null,
)
