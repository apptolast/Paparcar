package io.apptolast.paparcar.presentation.vehicles

import io.apptolast.paparcar.domain.error.PaparcarError
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.util.PaparcarLogger
import io.apptolast.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class VehiclesViewModel(
    private val vehicleRepository: VehicleRepository,
) : BaseViewModel<VehiclesState, VehiclesIntent, VehiclesEffect>() {

    override fun initState(): VehiclesState = VehiclesState()

    init {
        vehicleRepository.observeVehicles()
            .onEach { vehicles -> updateState { copy(vehicles = vehicles, isLoading = false) } }
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
            is VehiclesIntent.RequestDeleteVehicle ->
                updateState { copy(pendingDeleteVehicleId = intent.vehicleId) }
            is VehiclesIntent.DismissDeleteConfirmation ->
                updateState { copy(pendingDeleteVehicleId = null) }
            is VehiclesIntent.ConfirmDeleteVehicle -> {
                updateState { copy(pendingDeleteVehicleId = null) }
                deleteVehicle(intent.vehicleId)
            }
            is VehiclesIntent.EditVehicle ->
                sendEffect(VehiclesEffect.NavigateToEditVehicle(intent.vehicleId))
            is VehiclesIntent.AddVehicle -> sendEffect(VehiclesEffect.NavigateToAddVehicle)
            is VehiclesIntent.ViewHistory ->
                sendEffect(VehiclesEffect.NavigateToHistory(intent.vehicleId))
        }
    }

    private fun setActiveVehicle(vehicleId: String) {
        viewModelScope.launch {
            runCatching { vehicleRepository.setDefaultVehicle(vehicleId) }
                .onFailure { e ->
                    PaparcarLogger.e(TAG, "Failed to set default vehicle", e)
                    sendEffect(VehiclesEffect.ShowError(PaparcarError.Database.Unknown(e.message ?: "")))
                }
        }
    }

    private fun deleteVehicle(vehicleId: String) {
        viewModelScope.launch {
            runCatching { vehicleRepository.deleteVehicle(vehicleId) }
                .onFailure { e ->
                    PaparcarLogger.e(TAG, "Failed to delete vehicle", e)
                    sendEffect(VehiclesEffect.ShowError(PaparcarError.Database.Unknown(e.message ?: "")))
                }
        }
    }

    private companion object {
        const val TAG = "VehiclesViewModel"
    }
}
