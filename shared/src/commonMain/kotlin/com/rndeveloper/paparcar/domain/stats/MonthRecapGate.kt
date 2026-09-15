@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.stats

import kotlinx.datetime.TimeZone

/**
 * Whether this tick is the one that tells the user how their month went.
 * [STATS-PLUS-THE-RECAP-KNOCKS-001]
 *
 * ⛔ **Pure, and separate from the use case that reads the repositories.** All the rules live here —
 * the window, the once-a-month, the "is there anything to say" — so they can be tested without
 * standing up a repository, which is the same shape `SentryWakeCooldown` and the rest of
 * `domain/detection` already use. The use case is left as what it should be: plumbing.
 */
sealed interface MonthRecapGate {
    /** Not now, with the reason a diagnostic can quote. */
    data class Hold(val reason: String) : MonthRecapGate

    /** Tell it, and mark [yearMonth] as told. */
    data class Tell(val yearMonth: Int) : MonthRecapGate
}

fun monthRecapGate(
    nowMs: Long,
    lastAnnouncedYearMonth: Int?,
    parkingsInClosedMonth: Int,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): MonthRecapGate {
    // Outside the first days of the new month this is not news, it is a memory.
    if (!isRecapFresh(nowMs, timeZone)) return MonthRecapGate.Hold("stale")

    val closed = closedMonthWindow(nowMs, timeZone)
    val yearMonth = closed.year * YEAR_SHIFT + closed.monthNumber
    // ⛔ Once per month, whatever the worker's cadence: the app can wake seven days running inside
    // the window, and seven identical knocks is not a reminder, it is nagging.
    if (lastAnnouncedYearMonth == yearMonth) return MonthRecapGate.Hold("said")

    if (parkingsInClosedMonth <= 0) return MonthRecapGate.Hold("nothing")
    return MonthRecapGate.Tell(yearMonth)
}

private const val YEAR_SHIFT = 100
