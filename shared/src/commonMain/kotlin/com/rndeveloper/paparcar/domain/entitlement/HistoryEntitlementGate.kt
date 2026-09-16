package com.rndeveloper.paparcar.domain.entitlement

import com.rndeveloper.paparcar.domain.model.UserParking

/**
 * What the free tier may see of a parking history, decided in ONE place. [MONEY-001]
 *
 * The product rule: free sees the last [FREE_HISTORY_WINDOW_DAYS] days; Plus sees everything.
 * The data is NEVER deleted or mutated — losing Plus only narrows this window, and regaining
 * it widens the window back over the same rows. Both history surfaces (the per-vehicle list
 * and the map detail's prev/next stepper) must call this same function: a stepper that walks
 * where the list cannot see would be a paywall with a hole in it.
 */
data class GatedHistory(
    val visible: List<UserParking>,
    /** How many sessions the window hid — what the "Ver todo con Plus" footer counts. */
    val hiddenOlderCount: Int,
)

/**
 * The oldest instant this user may see, or null when they may see everything. [MONEY-001]
 *
 * Public because the window is a QUESTION other surfaces have to ask, not just a step inside
 * [gateHistoryToEntitlement]: a recap of a closed month may only be shown when the user can see
 * that whole month, and answering that by re-deriving `now - 7 days` somewhere else would be the
 * second arithmetic this file exists to prevent.
 * [STATS-A-PACE-ROW-NAMES-THE-PERIOD-IT-PROJECTS-001]
 */
fun historyVisibleFromMs(isPlus: Boolean, nowMs: Long): Long? =
    if (isPlus) null else nowMs - FREE_HISTORY_WINDOW_DAYS * MS_PER_DAY

/**
 * Whether a period STARTING at [periodStartMs] falls entirely inside what this user may see —
 * the one question every surface that offers a period has to ask before it answers one.
 * [STATS-A-CHIP-YOU-CANNOT-MEASURE-MUST-NOT-REDRAW-001]
 *
 * [periodStartMs] null means "since forever" (the `All` filter), which only a user with no window
 * at all can cover. [visibleFromMs] is [historyVisibleFromMs]: null = Plus, sees everything.
 *
 * ⛔ **Coverage of the START, not overlap.** A period that merely OVERLAPS the window is the bug
 * this exists to stop: "this month" read through a seven-day window is not this month, it is a
 * fragment wearing the month's name — and the figures, the chart axis and the forecast all then
 * describe a window nobody measured.
 *
 * ⛔ And it is a question about the PLAN, never about whether the period would be offered: which
 * chips the row shows is a question about the DATA
 * [STATS-A-FILTER-THAT-CANNOT-ANSWER-IS-NOT-OFFERED-001]. Every chip stays on screen; this only
 * decides whether tapping one answers or opens the paywall [MONEY-001].
 */
fun periodIsFullyVisible(periodStartMs: Long?, visibleFromMs: Long?): Boolean = when {
    visibleFromMs == null -> true
    periodStartMs == null -> false
    else -> periodStartMs >= visibleFromMs
}

fun gateHistoryToEntitlement(
    sessions: List<UserParking>,
    isPlus: Boolean,
    nowMs: Long,
): GatedHistory {
    val windowStartMs = historyVisibleFromMs(isPlus, nowMs)
        ?: return GatedHistory(visible = sessions, hiddenOlderCount = 0)
    // An ACTIVE session is the user's car parked right now — that is "the present", not
    // history, and hiding someone's own parked car behind a paywall is never on the table,
    // however long ago they parked it.
    val visible = sessions.filter { it.isActive || it.location.timestamp >= windowStartMs }
    return GatedHistory(
        visible = visible,
        hiddenOlderCount = sessions.size - visible.size,
    )
}

// 30 → 7 on 2026-09-07: the product review narrowed the free window (plus-roadmap-001 supersedes
// MONEY-001 here). Everything downstream reads this constant, so the pie/stepper follow alone.
const val FREE_HISTORY_WINDOW_DAYS = 7
private const val MS_PER_DAY = 86_400_000L
