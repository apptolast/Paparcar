@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.presentation.stats

import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.entitlement.PaywallReason
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.Zone
import com.rndeveloper.paparcar.domain.model.VehicleWithStats
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.repository.ZoneRepository
import com.rndeveloper.paparcar.domain.repository.VehicleRepository
import com.rndeveloper.paparcar.domain.usecase.fuel.ResolveFuelPriceUseCase
import com.rndeveloper.paparcar.domain.entitlement.historyVisibleFromMs
import com.rndeveloper.paparcar.domain.stats.closedMonthIsFullyVisible
import com.rndeveloper.paparcar.domain.stats.isRecapFresh
import com.rndeveloper.paparcar.domain.usecase.stats.DeriveStatsInsightsUseCase
import com.rndeveloper.paparcar.domain.usecase.entitlement.ObserveIsPlusUseCase
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import com.rndeveloper.paparcar.presentation.base.BaseViewModel
import com.rndeveloper.paparcar.presentation.vehicles.HistoryFilter
import com.rndeveloper.paparcar.presentation.vehicles.availableFilters
import com.rndeveloper.paparcar.presentation.vehicles.resolveChartMetric
import com.rndeveloper.paparcar.presentation.vehicles.HistoryTimeline
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.time.Clock

/**
 * The stats panel. [STATS-PANEL-001]
 *
 * It computes nothing of its own: the period window belongs to `VehicleHistoryCalculator` and the
 * figures to `ComputePeriodStatsUseCase`, both reached through the SAME `HistoryTimeline.resolve`
 * the vehicle ficha uses. That is deliberate — two screens showing the same car and the same month
 * must not be able to disagree, and the only way to guarantee that is to have one resolution.
 */
class StatsViewModel(
    private val vehicleRepository: VehicleRepository,
    private val userParkingRepository: UserParkingRepository,
    private val observeIsPlus: ObserveIsPlusUseCase,
    /** The user's named places — see the ficha's. [STATS-A-USUAL-PLACE-IS-A-CLUSTER-NOT-A-STREET-001] */
    private val zoneRepository: ZoneRepository,
    /**
     * The price ladder behind the cost tile. [FUEL-NATIONAL-AVERAGE-FROM-OPENVAN-001]
     *
     * Injected rather than built inside `HistoryTimeline.resolve`, because the national average is
     * no longer a constant compiled into the app: it is a table a refresh can replace, held by one
     * repository so every screen quotes the same one.
     */
    private val resolveFuelPrice: ResolveFuelPriceUseCase,
    /** Read for the detection toggle only. [STATS-FREE-SEES-EVERY-FIGURE-FOR-7-DAYS-001] */
    private val appPreferences: AppPreferences,
    /** Which car the ficha was showing when the panel was opened; null = the active one. */
    private val initialVehicleId: String? = null,
) : BaseViewModel<StatsState, StatsIntent, StatsEffect>() {

    override fun initState(): StatsState = StatsState()

    /** Kept so a filter or vehicle change can re-resolve without waiting for a new emission. */
    private var sessionsByVehicle: Map<String?, List<UserParking>> = emptyMap()

    /** Same reason, for the names of the places. [STATS-A-USUAL-PLACE-IS-A-CLUSTER-NOT-A-STREET-001] */
    private var zones: List<Zone> = emptyList()

    init {
        observeData()
        appPreferences.observeAutoDetectParking()
            .onEach { on -> updateState { copy(isAutoDetectionOn = on) } }
            .catch { e -> PaparcarLogger.e(TAG, "Failed to observe the detection toggle", e) }
            .launchIn(viewModelScope)
    }

    private fun observeData() {
        combine(
            vehicleRepository.observeVehicles(),
            userParkingRepository.observeAllSessions(),
            observeIsPlus(),
            // In the combine, not on the side: zones land after the sessions do, and a place that
            // only takes its real name on the next unrelated emission reads as a stale screen.
            zoneRepository.observeZones(),
        ) { vehicles, sessions, isPlus, zones -> StatsSnapshot(vehicles, sessions, isPlus, zones) }
            .onEach { (vehicles, allSessions, isPlus, observedZones) ->
                sessionsByVehicle = allSessions.groupBy { it.vehicleId }
                zones = observedZones
                val withStats = vehicles.map { vehicle ->
                    val vehicleSessions = sessionsByVehicle[vehicle.id].orEmpty()
                    VehicleWithStats(
                        vehicle = vehicle,
                        sessionCount = vehicleSessions.size,
                        lastSession = vehicleSessions.firstOrNull(),
                    )
                }
                updateState {
                    // The opening car wins the first time and only the first time; after that the
                    // user's own selection outranks it, so a Room re-emission cannot yank the
                    // panel back to another vehicle mid-read.
                    val isFirstResolution = this.vehicles == null
                    val index = if (isFirstResolution) {
                        withStats.indexOfFirst { it.vehicle.id == initialVehicleId }
                            .takeIf { it >= 0 }
                            ?: withStats.indexOfFirst { it.vehicle.isActive }.takeIf { it >= 0 }
                            ?: 0
                    } else {
                        selectedVehicleIndex
                    }.coerceIn(0, (withStats.size - 1).coerceAtLeast(0))

                    copy(vehicles = withStats, selectedVehicleIndex = index, isPlus = isPlus)
                        .withResolvedStats(withStats.getOrNull(index)?.vehicle, filter, isPlus)
                }
            }
            .catch { e -> PaparcarLogger.e(TAG, "Failed to observe stats sources", e) }
            .launchIn(viewModelScope)
    }

    override fun handleIntent(intent: StatsIntent) {
        when (intent) {
            is StatsIntent.SelectVehicle -> updateState {
                val index = intent.index.coerceIn(0, ((vehicles?.size ?: 1) - 1).coerceAtLeast(0))
                copy(selectedVehicleIndex = index)
                    .withResolvedStats(vehicles?.getOrNull(index)?.vehicle, filter, isPlus)
            }
            is StatsIntent.SetFilter -> updateState {
                copy(filter = intent.filter)
                    .withResolvedStats(selectedVehicle?.vehicle, intent.filter, isPlus)
            }
            // The period can take the data out from under a metric (a week with no measured
            // route), so the choice is re-resolved against what the period HAS before it is stored.
            is StatsIntent.SetChartMetric -> updateState {
                copy(chartMetric = resolveChartMetric(intent.metric, filteredSessions))
            }
            // No re-resolution: the sessions, the window and the figures are the same. What moves
            // is which of their numbers the grid paints. [STATS-THE-EXPENSIVE-HOUR-IS-MEASURED-001]
            is StatsIntent.SetHeatMeasure -> updateState { copy(heatMeasure = intent.measure) }
            is StatsIntent.OpenPaywall ->
                sendEffect(StatsEffect.NavigateToPaywall(PaywallReason.STATS))
        }
    }

    /**
     * Re-derives the figures through the one shared resolution. Everything the panel shows comes
     * out of here, so a period change can never move the chart without moving the tiles.
     */
    private fun StatsState.withResolvedStats(
        vehicle: Vehicle?,
        filter: HistoryFilter,
        isPlus: Boolean,
    ): StatsState {
        if (vehicle == null) {
            return copy(
                periodStats = null,
                statsPeriod = filter,
                filteredSessions = emptyList(),
                costPrice = null,
                // ⛔ Cleared with the rest. This one used to survive the reset and kept showing the
                // PREVIOUS car's month under a screen that had no car at all.
                closedMonth = null,
                closedMonthPrice = null,
                lifetimeParkings = 0,
                lifetimeSpotsReleased = 0,
            )
        }
        val allSessions = sessionsByVehicle[vehicle.id].orEmpty()
        val recap = closedMonthRecap(vehicle, isPlus)
        val resolved = HistoryTimeline.resolve(
            sessions = sessionsByVehicle[vehicle.id].orEmpty(),
            filter = filter,
            isPlus = isPlus,
            vehicle = vehicle,
            zones = zones,
            nowMs = Clock.System.now().toEpochMilliseconds(),
            resolveFuelPrice = resolveFuelPrice,
        )
        return copy(
            periodStats = resolved.periodStats,
            statsPeriod = resolved.statsPeriod,
            // The conclusions come from the SAME resolution as the figures under them: an insight
            // that disagreed with the card proving it would be worse than no insight.
            // [STATS-AN-INSIGHT-IS-A-VERDICT-WITH-ITS-EVIDENCE-001]
            insights = deriveInsights(resolved.filteredSessions, resolved.periodStats),
            closedMonth = recap?.periodStats,
            closedMonthPrice = recap?.costPrice,
            filteredSessions = resolved.filteredSessions,
            // Over the FULL list: what the row may OFFER is a question about the data, not about the
            // plan. [STATS-A-FILTER-THAT-CANNOT-ANSWER-IS-NOT-OFFERED-001]
            periodFilters = availableFilters(allSessions),
            costPrice = resolved.costPrice,
            lifetimeParkings = resolved.lifetimeParkings,
            lifetimeSpotsReleased = resolved.lifetimeSpotsReleased,
        )
    }

    /**
     * The month that just closed — over a window the filter does not decide.
     * [STATS-PLUS-REMEMBERS-AND-REPORTS-001]
     *
     * Two conditions, both about whether there is a month to talk about at all: it has to still be
     * NEWS (the first week of the new month), and the user has to be able to SEE the whole of it —
     * which is the Plus gate, and which this function did not have.
     * [STATS-A-PACE-ROW-NAMES-THE-PERIOD-IT-PROJECTS-001]
     *
     * ⛔ **Through the same resolution as every other figure on this screen**, with the period
     * pinned to `LastMonth` instead of the chip's. `windowStartMs`/`windowEndMs`/`previousWindow`
     * for that filter are the SAME dates `closedMonthWindow` computes — verified line by line — so
     * this recomputes nothing and no figure moves; what it gains is the entitlement gate, the fuel
     * price and the previous window from the one authority that owns them. The hand-rolled version
     * passed no price, which is why the card's spend row had never once been composed.
     */
    private fun closedMonthRecap(vehicle: Vehicle, isPlus: Boolean): HistoryTimeline.Resolved? {
        val nowMs = Clock.System.now().toEpochMilliseconds()
        if (!isRecapFresh(nowMs)) return null
        if (!closedMonthIsFullyVisible(historyVisibleFromMs(isPlus, nowMs), nowMs)) return null
        return HistoryTimeline.resolve(
            sessions = sessionsByVehicle[vehicle.id].orEmpty(),
            filter = HistoryFilter.LastMonth,
            isPlus = isPlus,
            vehicle = vehicle,
            zones = zones,
            nowMs = nowMs,
            resolveFuelPrice = resolveFuelPrice,
        ).takeIf {
            // ⛔ And only if the resolution actually MEASURED the month asked for. A plan whose
            // figures fall back to a rolling window would otherwise be summarised under the words
            // "last month" — the card's title is a fixed string and cannot relabel itself the way
            // the panel's does. Unreachable while the free window is seven days; a structural
            // answer instead of a promise that it will stay that way.
            // [STATS-A-PACE-ROW-NAMES-THE-PERIOD-IT-PROJECTS-001]
            it.periodStats != null && it.statsPeriod == HistoryFilter.LastMonth
        }
    }

    /** One emission of everything the panel depends on. A `Triple` ran out of slots. */
    private data class StatsSnapshot(
        val vehicles: List<Vehicle>,
        val sessions: List<UserParking>,
        val isPlus: Boolean,
        val zones: List<Zone>,
    )

    private companion object {
        private val deriveInsights = DeriveStatsInsightsUseCase()

        // The recap's own aggregator is gone with it: it now goes through `HistoryTimeline.resolve`
        // like everything else, which holds the ONE `ComputePeriodStatsUseCase` this screen uses.
        const val TAG = "StatsViewModel"
    }
}
