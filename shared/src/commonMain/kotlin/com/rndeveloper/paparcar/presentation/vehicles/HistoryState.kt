@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.presentation.vehicles

import androidx.compose.runtime.Immutable
import com.rndeveloper.paparcar.domain.entitlement.gateHistoryToEntitlement
import com.rndeveloper.paparcar.domain.fuel.FuelPriceOrigin
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.stats.PeriodStats
import com.rndeveloper.paparcar.domain.usecase.fuel.ResolveFuelPriceUseCase
import com.rndeveloper.paparcar.domain.usecase.stats.ComputePeriodStatsUseCase
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
     * The vehicle's history as Room last told it. [sessions] is always the FULL list — never
     * scoped by the filter and never by the entitlement, so re-resolving from it loses nothing.
     * What the screen may SHOW is [visibleSessions]: the Plus gate applied ([MONEY-001] — free
     * sees the last 7 days, the data itself is untouched), with [hiddenOlderCount] feeding the
     * "Ver todo con Plus" footer. [filteredSessions] and [periodStats] derive from the visible
     * set so the chart, the figures and the list can never disagree about what exists.
     */
    data class Resolved(
        val sessions: List<UserParking>,
        val visibleSessions: List<UserParking>,
        val hiddenOlderCount: Int,
        val filteredSessions: List<UserParking>,
        /**
         * Everything the SELECTED PERIOD can say about itself, with its previous period attached
         * for the ▲/▼ comparisons — the single aggregate the figures card reads. [STATS-UI-001]
         *
         * Scoped to [filteredSessions], exactly like the chart above it: the two cards of the
         * Activity section answer to the same filter. Null when the period holds no session.
         */
        val periodStats: PeriodStats?,
        /**
         * Where the price behind [periodStats]'s cost came from, or null when there is no cost.
         * The card says it out loud: a spend computed from a national average and one computed
         * from what the user actually pays are not the same claim. [FUEL-PRICE-RESOLVER-001]
         */
        val costOrigin: FuelPriceOrigin?,
        /** Ended sessions of the car's WHOLE life — the footer line, and the only figures on the
         *  ficha that are not the period's. */
        val lifetimeParkings: Int,
        val lifetimeSpotsReleased: Int,
    ) : HistoryTimeline

    companion object {
        /**
         * Resolves [sessions] together with its derived views in ONE place, so no call site can pair
         * a session list with a filtered list or a figures block that disagrees with it.
         *
         * [isPlus] has NO default on purpose — the same reason the timeline has no default: a
         * silent answer here would quietly hand the full history to free or truncate it for Plus.
         *
         * [vehicle] is what turns kilometres into litres, kWh and CO₂ (its override, or the named
         * `FuelDefaults`). Null only where the caller genuinely has no vehicle in hand; then the
         * consumption figures are absent rather than guessed.
         */
        fun resolve(
            sessions: List<UserParking>,
            filter: HistoryFilter,
            isPlus: Boolean,
            vehicle: Vehicle? = null,
            nowMs: Long = Clock.System.now().toEpochMilliseconds(),
            /**
             * The price ladder to quote. [FUEL-NATIONAL-AVERAGE-FROM-OPENVAN-001]
             *
             * A PARAMETER since the national average stopped being a constant in the binary: the
             * production resolver reads a table that a refresh can replace, and it is Koin that
             * holds it. This used to be built here, which meant nothing could ever hand this
             * function a different one.
             *
             * The default is the offline resolver — the snapshot shipped in the binary — so tests,
             * previews and the state gallery still call `resolve(...)` with nothing extra and get
             * a real figure rather than a blank.
             */
            resolveFuelPrice: ResolveFuelPriceUseCase = OfflineFuelPrice,
        ): Resolved {
            val gated = gateHistoryToEntitlement(sessions, isPlus = isPlus, nowMs = nowMs)
            val filtered = VehicleHistoryCalculator.filter(gated.visible, filter, nowMs)
            // The equivalent window immediately before this one, decided by the SAME authority that
            // decided the current one — never a second date arithmetic. [STATS-CORE-001]
            val previousWindow = VehicleHistoryCalculator.previousWindow(filter, nowMs)
            val previousSessions = previousWindow?.let { window ->
                gated.visible.filter { it.location.timestamp in window }
            }
            // ⛔ From the FULL list, not the gated one: a line that says "desde siempre" has to
            // mean it, and for a free account the visible set is seven days. This discloses no
            // gated CONTENT — only two counts, and the Plus footer under the timeline already
            // states how many sessions lie beyond the window. [MONEY-001]
            val countryCode = filtered.countryCode()
            val litrePrice = vehicle?.let { resolveFuelPrice.litre(it, countryCode) }
            val kwhPrice = vehicle?.let { resolveFuelPrice.kwh(it, countryCode) }
            val endedEver = sessions.filter { !it.isActive }
            return Resolved(
                sessions = sessions,
                visibleSessions = gated.visible,
                hiddenOlderCount = gated.hiddenOlderCount,
                filteredSessions = filtered,
                periodStats = computePeriodStats(
                    sessions = filtered,
                    vehicles = listOfNotNull(vehicle),
                    previousSessions = previousSessions,
                    // The country comes from the parkings themselves — the reverse geocoder already
                    // stamps it on every one — so a price is resolved where the car actually was,
                    // and the user's country is never stored anywhere. [FUEL-PRICE-RESOLVER-001]
                    fuelPricePerLiter = litrePrice?.perUnit,
                    energyPricePerKwh = kwhPrice?.perUnit,
                ),
                // The two rungs cannot disagree — one resolver, one order — so either answers for
                // the label. The litre is asked first because most cars only have that one.
                costOrigin = (litrePrice ?: kwhPrice)?.source,
                lifetimeParkings = endedEver.size,
                lifetimeSpotsReleased = endedEver.count { it.publishedSpot },
            )
        }

        private val computePeriodStats = ComputePeriodStatsUseCase()

        /**
         * The resolver used when a caller names none: averages from the binary, no network.
         *
         * Named "offline" rather than "default" because that is the claim it makes — it is the
         * floor of the ladder, not a placeholder, and a screen that ends up on it is showing a real
         * (if unrefreshed) figure.
         */
        private val OfflineFuelPrice = ResolveFuelPriceUseCase()

        /**
         * Where this period happened, for pricing purposes: the country of the MOST RECENT parking
         * that knows its own. A period that straddles a border is priced where the driver ended up,
         * which is both the likelier place they refuel and a decision worth naming — the
         * alternative (pricing each session in its own country and summing) would produce a total
         * in mixed currencies, and the aggregate already refuses those. [FUEL-PRICE-RESOLVER-001]
         */
        private fun List<UserParking>.countryCode(): String? =
            maxByOrNull { it.location.timestamp }?.address?.countryCode
                ?: firstNotNullOfOrNull { it.address?.countryCode }
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
     * The selected period's figures, or null while unresolved / with nothing in the period. Null
     * already means "render nothing" for every consumer, so an unresolved history shows no metric
     * rather than a zero it cannot back.
     */
    val periodStats: PeriodStats?
        get() = (timeline as? HistoryTimeline.Resolved)?.periodStats
}
