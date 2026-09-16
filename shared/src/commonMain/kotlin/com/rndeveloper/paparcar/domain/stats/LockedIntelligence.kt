package com.rndeveloper.paparcar.domain.stats

/**
 * What the panel has to SHOW AS LOCKED, card by card.
 * [MONEY-A-BLUR-IS-ONLY-HONEST-WHEN-THERE-IS-SOMETHING-BEHIND-IT-001]
 *
 * The conclusions layer — last month, what your parkings say, your places, when you park, where
 * this is heading — is what the plan sells: *free asks and the app answers; Plus watches and
 * REMEMBERS*. Over a rolling seven-day window almost none of it exists, so a free account used to
 * get the worst of both: the little that did exist was given away, and the rest was absent with
 * nothing anywhere saying it had ever been possible.
 *
 * ⛔ **A blur with nothing behind it is advertising, not a gate.** Every flag here answers one
 * question — *would this card have something to say if the plan stopped cutting the history?* — and
 * a card whose answer is no does not compose at all, exactly as today. That is why this returns
 * five booleans and not one.
 *
 * ⛔ **Booleans, never values.** `gateHistoryToEntitlement` stays the only door deciding what is
 * SHOWN; knowing whether there is anything to sell means looking over it, and what comes back is a
 * yes/no, which is nobody's figure. The same licence the lifetime counters already take.
 */
data class LockedIntelligence(
    /** The month that just closed. [STATS-PLUS-REMEMBERS-AND-REPORTS-001] */
    val recap: Boolean = false,
    /** What the parkings say. [STATS-AN-INSIGHT-IS-A-VERDICT-WITH-ITS-EVIDENCE-001] */
    val insights: Boolean = false,
    /** Where this car repeats. [STATS-A-USUAL-PLACE-IS-A-CLUSTER-NOT-A-STREET-001] */
    val places: Boolean = false,
    /** The day x band grid. [STATS-THE-EXPENSIVE-HOUR-IS-MEASURED-001] */
    val heatmap: Boolean = false,
    /** The projection to the period's close. [STATS-AN-ESTIMATE-SAYS-IT-IS-ONE-001] */
    val pace: Boolean = false,
) {
    companion object {
        /** Nothing to lock — a Plus account, or a car with no history beyond the free window. */
        val NONE = LockedIntelligence()
    }
}

/**
 * The verdict, over figures already computed as if the plan did not cut anything.
 *
 * @param isPlus someone who SEES these cards has nothing locked — the short-circuit is here and not
 *   at the call sites, so no screen can forget it.
 * @param allTime the car's whole life, for the two cards that describe habits rather than a period.
 * @param insightCount conclusions derived over that same whole life.
 * @param heatmapHasData whether the day x band grid has a single parking to draw.
 * @param thisMonth the current month, which is the period [pace] would project. ⛔ Not the whole
 *   life: "all time" has no close to forecast, so asking it would hide the pace card forever.
 * @param closedMonth the month that just closed, already gated by freshness upstream — the recap is
 *   news for a week and a memory afterwards, and a locked card must not outlive the real one.
 */
fun lockedIntelligence(
    isPlus: Boolean,
    allTime: PeriodStats?,
    insightCount: Int,
    heatmapHasData: Boolean,
    thisMonth: PeriodStats?,
    closedMonth: PeriodStats?,
): LockedIntelligence {
    if (isPlus) return LockedIntelligence.NONE
    return LockedIntelligence(
        recap = closedMonth != null,
        insights = insightCount > 0,
        // ⛔ NAMED places, which is the same filter the card itself applies. Counting every cluster
        // put a locked "Tus sitios" over a card that would have opened on its own empty state —
        // a blur with nothing behind it, the one thing this file exists to prevent. A cluster the
        // app cannot put a name to helps nobody, gate or no gate.
        // [STATS-A-USUAL-PLACE-IS-A-CLUSTER-NOT-A-STREET-001]
        places = allTime?.places?.any { !it.name.isNullOrBlank() } == true,
        heatmap = heatmapHasData,
        // The same three magnitudes the pace card projects, and the same reason: a month with no
        // measured spend, distance or search time has no forecast to withhold.
        pace = thisMonth != null &&
            (thisMonth.costEstimate != null || thisMonth.distance != null || thisMonth.searchTime != null),
    )
}
