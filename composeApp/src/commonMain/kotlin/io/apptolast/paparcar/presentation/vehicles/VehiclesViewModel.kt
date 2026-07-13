@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.vehicles

import io.apptolast.paparcar.domain.error.PaparcarError
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.VehicleWithStats
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.util.PaparcarLogger
import io.apptolast.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Clock

class VehiclesViewModel(
    private val vehicleRepository: VehicleRepository,
    private val userParkingRepository: UserParkingRepository,
) : BaseViewModel<VehiclesState, VehiclesIntent, VehiclesEffect>() {

    override fun initState(): VehiclesState = VehiclesState()

    init {
        observeVehicles()
    }

    private fun observeVehicles() {
        combine(
            vehicleRepository.observeVehicles(),
            userParkingRepository.observeAllSessions(),
        ) { vehicles, allSessions ->
            vehicles to allSessions
        }
            .onEach { (vehicles, allSessions) ->
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
                        vId to HistoryState(
                            sessions = vSessions,
                            activeFilter = existingFilter,
                            filteredSessions = VehicleHistoryCalculator.filter(vSessions, existingFilter, nowMs),
                            statsData = VehicleHistoryCalculator.computeStats(vSessions, nowMs),
                        )
                    }
                    copy(
                        vehicles = vehiclesWithStats,
                        isLoading = false,
                        selectedVehicleIndex = clampedIndex,
                        historyCache = updatedCache,
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
            is VehiclesIntent.AddVehicle -> sendEffect(VehiclesEffect.NavigateToAddVehicle)
            is VehiclesIntent.SetHistoryFilter -> {
                val vehicleId = state.value.currentVehicleId ?: return
                val currentHistory = state.value.historyCache[vehicleId] ?: return
                val nowMs = Clock.System.now().toEpochMilliseconds()
                val filtered = VehicleHistoryCalculator.filter(currentHistory.sessions, intent.filter, nowMs)
                updateState {
                    val updated = currentHistory.copy(activeFilter = intent.filter, filteredSessions = filtered)
                    copy(historyCache = historyCache + (vehicleId to updated))
                }
            }
            is VehiclesIntent.ViewOnMap ->
                sendEffect(VehiclesEffect.NavigateToMap(intent.lat, intent.lon, intent.sessionId))
        }
    }

    private fun setActiveVehicle(vehicleId: String) {
        if (state.value.settingActiveVehicleId != null) return
        updateState { copy(settingActiveVehicleId = vehicleId) }
        viewModelScope.launch {
            vehicleRepository.setActiveVehicle(vehicleId)
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
