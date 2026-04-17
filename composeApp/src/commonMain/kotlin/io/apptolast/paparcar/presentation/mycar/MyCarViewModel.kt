package io.apptolast.paparcar.presentation.mycar

import io.apptolast.paparcar.domain.error.PaparcarError
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.util.PaparcarLogger
import io.apptolast.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class MyCarViewModel(
    private val vehicleRepository: VehicleRepository,
) : BaseViewModel<MyCarState, MyCarIntent, MyCarEffect>() {

    override fun initState(): MyCarState = MyCarState()

    init {
        vehicleRepository.observeVehicles()
            .onEach { vehicles -> updateState { copy(vehicles = vehicles, isLoading = false) } }
            .catch { e ->
                PaparcarLogger.e(TAG, "Failed to observe vehicles", e)
                updateState { copy(isLoading = false) }
                sendEffect(MyCarEffect.ShowError(PaparcarError.Database.Unknown(e.message ?: "")))
            }
            .launchIn(viewModelScope)
    }

    override fun handleIntent(intent: MyCarIntent) {
        when (intent) {
            is MyCarIntent.SetActiveVehicle -> setActiveVehicle(intent.vehicleId)
            is MyCarIntent.RequestDeleteVehicle ->
                updateState { copy(pendingDeleteVehicleId = intent.vehicleId) }
            is MyCarIntent.DismissDeleteConfirmation ->
                updateState { copy(pendingDeleteVehicleId = null) }
            is MyCarIntent.ConfirmDeleteVehicle -> {
                updateState { copy(pendingDeleteVehicleId = null) }
                deleteVehicle(intent.vehicleId)
            }
            is MyCarIntent.EditVehicle ->
                sendEffect(MyCarEffect.NavigateToEditVehicle(intent.vehicleId))
            is MyCarIntent.AddVehicle -> sendEffect(MyCarEffect.NavigateToAddVehicle)
        }
    }

    private fun setActiveVehicle(vehicleId: String) {
        viewModelScope.launch {
            runCatching { vehicleRepository.setDefaultVehicle(vehicleId) }
                .onFailure { e ->
                    PaparcarLogger.e(TAG, "Failed to set default vehicle", e)
                    sendEffect(MyCarEffect.ShowError(PaparcarError.Database.Unknown(e.message ?: "")))
                }
        }
    }

    private fun deleteVehicle(vehicleId: String) {
        viewModelScope.launch {
            runCatching { vehicleRepository.deleteVehicle(vehicleId) }
                .onFailure { e ->
                    PaparcarLogger.e(TAG, "Failed to delete vehicle", e)
                    sendEffect(MyCarEffect.ShowError(PaparcarError.Database.Unknown(e.message ?: "")))
                }
        }
    }

    private companion object {
        const val TAG = "MyCarViewModel"
    }
}