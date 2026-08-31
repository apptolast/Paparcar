@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.presentation.vehicles

import androidx.compose.runtime.Immutable
import com.rndeveloper.paparcar.domain.model.UserParking
import kotlin.time.Clock

/**
 * Whether this vehicle's history has ARRIVED yet — the question a reader must answer before it is
 * allowed to claim there is nothing to show.
 * [UI-HISTORY-A-LOADING-LIST-MUST-NOT-CLAIM-TO-BE-EMPTY-001]
 *
 * "Todavía no lo sé" and "no hay nada" used to be the SAME value (`sessions = emptyList()`), told
 * apart only by an `isLoading` flag that defaulted to `false`. Every call site that forgot the flag
 * therefore answered "ya cargó" by omission — and none of the three ever set it — so while Room
 * resolved, the timeline stated the user's car had no history at all. Same shape as
 * [DET-A-DOUBT-FIELD-MUST-NOT-DEFAULT-TO-CERTAINTY-001]: a default is a permanent silent answer.
 *
 * There is no default here on purpose. The type asks; the compiler makes every call site answer.
 */
@Immutable
sealed interface HistoryTimeline {

    /** No emission for this vehicle yet. Renders as a skeleton — never as an empty history. */
    data object Unresolved : HistoryTimeline

    /**
     * The vehicle's history as Room last told it. [sessions] is always the FULL list — the timeline
     * is never scoped by the filter — while [filteredSessions] and [statsData] are the derived views
     * the Activity card reads.
     */
    data class Resolved(
        val sessions: List<UserParking>,
        val filteredSessions: List<UserParking>,
        val statsData: HistoryStatsData?,
    ) : HistoryTimeline

    companion object {
        /**
         * Resolves [sessions] together with its derived views in ONE place, so no call site can pair
         * a session list with a filtered list or a stats block that disagrees with it.
         */
        fun resolve(
            sessions: List<UserParking>,
            filter: HistoryFilter,
            nowMs: Long = Clock.System.now().toEpochMilliseconds(),
        ): Resolved = Resolved(
            sessions = sessions,
            filteredSessions = VehicleHistoryCalculator.filter(sessions, filter, nowMs),
            statsData = VehicleHistoryCalculator.computeStats(sessions),
        )
    }
}

@Immutable
data class HistoryState(
    /** No default — see [HistoryTimeline]. A state that cannot say "aún no lo sé" ends up lying. */
    val timeline: HistoryTimeline,
    /** The user's filter choice, which outlives any single emission, so it sits outside the timeline. */
    val activeFilter: HistoryFilter = HistoryFilter.All,
) {
    /**
     * Stats of the resolved history, or null while unresolved. Null already means "render nothing"
     * for every consumer (see [VehicleHistoryCalculator.computeStats]), so an unresolved history
     * shows no metric rather than a zero it cannot back.
     */
    val statsData: HistoryStatsData?
        get() = (timeline as? HistoryTimeline.Resolved)?.statsData
}
