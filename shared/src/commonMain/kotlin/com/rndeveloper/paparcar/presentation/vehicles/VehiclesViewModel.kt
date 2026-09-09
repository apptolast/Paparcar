@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.presentation.vehicles

import com.rndeveloper.paparcar.domain.entitlement.PaywallReason
import com.rndeveloper.paparcar.domain.entitlement.canAddOwnVehicle
import com.rndeveloper.paparcar.domain.error.PaparcarError
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.VehicleWithStats
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.repository.VehicleRepository
import com.rndeveloper.paparcar.domain.usecase.fuel.ResolveFuelPriceUseCase
import com.rndeveloper.paparcar.domain.usecase.entitlement.ObserveIsPlusUseCase
import com.rndeveloper.paparcar.domain.usecase.vehicle.DeclareActiveVehicleUseCase
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import com.rndeveloper.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Clock

class VehiclesViewModel(
    private val vehicleRepository: VehicleRepository,
    private val userParkingRepository: UserParkingRepository,
    // Declaring the active vehicle also swaps the OS geofences to it. [VEH-ACTIVE-FENCE-001]
    private val declareActiveVehicle: DeclareActiveVehicleUseCase,
    // The Plus gate over the history window — observed LIVE so losing or regaining Plus
    // re-resolves every timeline in place, no restart needed. [MONEY-001]
    private val observeIsPlus: ObserveIsPlusUseCase,
    /**
     * The price ladder behind the cost figures. [FUEL-NATIONAL-AVERAGE-FROM-OPENVAN-001]
     *
     * Injected rather than built inside `HistoryTimeline.resolve`, because the national average is
     * no longer a constant compiled into the app: it is a table a refresh can replace, held by one
     * repository so every screen quotes the same one.
     */
    private val resolveFuelPrice: ResolveFuelPriceUseCase,
) : BaseViewModel<VehiclesState, VehiclesIntent, VehiclesEffect>() {

    override fun initState(): VehiclesState = VehiclesState()

    init {
        observeVehicles()
    }

    private fun observeVehicles() {
        combine(
            vehicleRepository.observeVehicles(),
            userParkingRepository.observeAllSessions(),
            observeIsPlus(),
        ) { vehicles, allSessions, isPlus ->
            Triple(vehicles, allSessions, isPlus)
        }
            .onEach { (vehicles, allSessions, isPlus) ->
                val sessionsByVehicle = allSessions.groupBy { it.vehicleId }
                val nowMs = Clock.System.now().toEpochMilliseconds()
                val vehiclesWithStats = vehicles.map { vehicle ->
                    val vehicleSessions = sessionsByVehicle[vehicle.id].orEmpty()
                    VehicleWithStats(
                        vehicle = vehicle,
                        sessionCount = vehicleSessions.size,
                        lastSession = vehicleSessions.firstOrNull(),
                    )
                }
                updateState {
                    val clampedIndex = selectedVehicleIndex
                        .coerceIn(0, (vehiclesWithStats.size - 1).coerceAtLeast(0))
                    val updatedCache = vehiclesWithStats.associate { vws ->
                        val vId = vws.vehicle.id
                        val vSessions = sessionsByVehicle[vId].orEmpty()
                        val existingFilter = historyCache[vId]?.activeFilter ?: HistoryFilter.All
                        // This emission IS the answer to "¿ha llegado el historial?" — resolving it
                        // here is what keeps the skeleton from being replaced by a false "no hay
                        // nada". [UI-HISTORY-A-LOADING-LIST-MUST-NOT-CLAIM-TO-BE-EMPTY-001]
                        vId to HistoryState(
                            timeline = HistoryTimeline.resolve(
                                sessions = vSessions,
                                filter = existingFilter,
                                isPlus = isPlus,
                                // Consumption, cost and CO₂ are computed with THIS car's figures
                                // (its override, or the named FuelDefaults). [STATS-UI-001]
                                vehicle = vws.vehicle,
                                nowMs = nowMs,
                                resolveFuelPrice = resolveFuelPrice,
                            ),
                            activeFilter = existingFilter,
                        )
                    }
                    copy(
                        vehicles = vehiclesWithStats,
                        isLoading = false,
                        selectedVehicleIndex = clampedIndex,
                        historyCache = updatedCache,
                        isPlus = isPlus,
                    )
                }
            }
            .catch { e ->
                PaparcarLogger.e(TAG, "Failed to observe vehicles", e)
                updateState { copy(isLoading = false) }
                sendEffect(VehiclesEffect.ShowError(PaparcarError.Database.Unknown(e.message ?: "")))
            }
            .launchIn(viewModelScope)
    }

    override fun handleIntent(intent: VehiclesIntent) {
        when (intent) {
            is VehiclesIntent.SetActiveVehicle -> setActiveVehicle(intent.vehicleId)
            is VehiclesIntent.BluetoothVehicleConnected ->
                updateState { copy(bluetoothConnectedVehicleId = intent.vehicleId) }
            is VehiclesIntent.SelectVehicle -> updateState {
                val clamped = intent.index.coerceIn(0, (vehicles.size - 1).coerceAtLeast(0))
                copy(selectedVehicleIndex = clamped)
            }
            is VehiclesIntent.EditVehicle ->
                sendEffect(VehiclesEffect.NavigateToEditVehicle(intent.vehicleId))
            // [FREE-LIMITS-001] Soft gate at the ONE chokepoint (FAB and pill send this same
            // intent): free with a car already → paywall. Nothing existing is ever touched.
            is VehiclesIntent.AddVehicle ->
                if (canAddOwnVehicle(ownedCount = state.value.vehicles.size, isPlus = state.value.isPlus)) {
                    sendEffect(VehiclesEffect.NavigateToAddVehicle)
                } else {
                    sendEffect(VehiclesEffect.NavigateToPaywall(PaywallReason.VEHICLES))
                }
            is VehiclesIntent.SetHistoryFilter -> {
                val vehicleId = state.value.currentVehicleId ?: return
                val currentHistory = state.value.historyCache[vehicleId] ?: return
                // Nothing to re-scope until the history has arrived: an unresolved timeline has no
                // sessions to filter, and must not be turned into a resolved-but-empty one.
                val resolved = currentHistory.timeline as? HistoryTimeline.Resolved ?: return
                val nowMs = Clock.System.now().toEpochMilliseconds()
                updateState {
                    val updated = currentHistory.copy(
                        // From the FULL list, never the visible one: re-gating a gated list would
                        // report zero hidden and quietly erase the footer. [MONEY-001]
                        timeline = HistoryTimeline.resolve(
                            sessions = resolved.sessions,
                            filter = intent.filter,
                            isPlus = isPlus,
                            vehicle = vehicles.firstOrNull { it.vehicle.id == vehicleId }?.vehicle,
                            nowMs = nowMs,
                            resolveFuelPrice = resolveFuelPrice,
                        ),
                        activeFilter = intent.filter,
                    )
                    copy(historyCache = historyCache + (vehicleId to updated))
                }
            }
            is VehiclesIntent.ViewOnMap ->
                sendEffect(VehiclesEffect.NavigateToMap(intent.lat, intent.lon, intent.sessionId))
            is VehiclesIntent.OpenPlusPaywall -> sendEffect(VehiclesEffect.NavigateToPaywall(PaywallReason.HISTORY))
            is VehiclesIntent.OpenStatsPaywall -> sendEffect(VehiclesEffect.NavigateToPaywall(PaywallReason.STATS))
            is VehiclesIntent.OpenStatsPanel -> sendEffect(VehiclesEffect.NavigateToStats(intent.vehicleId))
        }
    }

    private fun setActiveVehicle(vehicleId: String) {
        if (state.value.settingActiveVehicleId != null) return
        updateState { copy(settingActiveVehicleId = vehicleId) }
        viewModelScope.launch {
            declareActiveVehicle(vehicleId)
                .onSuccess { updateState { copy(settingActiveVehicleId = null) } }
                .onFailure { e ->
                    PaparcarLogger.e(TAG, "Failed to set default vehicle", e)
                    updateState { copy(settingActiveVehicleId = null) }
                    sendEffect(VehiclesEffect.ShowError(PaparcarError.Database.Unknown(e.message ?: "")))
                }
        }
    }
    private companion object {
        const val TAG = "VehiclesViewModel"
    }
}
