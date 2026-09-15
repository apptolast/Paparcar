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
import com.rndeveloper.paparcar.domain.usecase.entitlement.ObserveIsPlusUseCase
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import com.rndeveloper.paparcar.presentation.base.BaseViewModel
import com.rndeveloper.paparcar.presentation.vehicles.HistoryFilter
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
                filteredSessions = emptyList(),
                costPrice = null,
                lifetimeParkings = 0,
                lifetimeSpotsReleased = 0,
            )
        }
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
            filteredSessions = resolved.filteredSessions,
            costPrice = resolved.costPrice,
            lifetimeParkings = resolved.lifetimeParkings,
            lifetimeSpotsReleased = resolved.lifetimeSpotsReleased,
        )
    }

    /** One emission of everything the panel depends on. A `Triple` ran out of slots. */
    private data class StatsSnapshot(
        val vehicles: List<Vehicle>,
        val sessions: List<UserParking>,
        val isPlus: Boolean,
        val zones: List<Zone>,
    )

    private companion object {
        const val TAG = "StatsViewModel"
    }
}
