package io.apptolast.paparcar.presentation.bluetooth

import io.apptolast.paparcar.domain.bluetooth.BluetoothScanner
import io.apptolast.paparcar.domain.error.PaparcarError
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.util.PaparcarLogger
import io.apptolast.paparcar.presentation.base.BaseViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class BluetoothConfigViewModel(
    private val vehicleId: String,
    private val bluetoothScanner: BluetoothScanner,
    private val vehicleRepository: VehicleRepository,
) : BaseViewModel<BluetoothConfigState, BluetoothConfigIntent, BluetoothConfigEffect>() {

    override fun initState() = BluetoothConfigState(vehicleId = vehicleId)

    init {
        // Load the current vehicle's BT device and bonded device list
        vehicleRepository.observeVehicles()
            .onEach { vehicles ->
                val vehicle = vehicles.find { it.id == vehicleId }
                updateState {
                    copy(
                        vehicleName = listOfNotNull(vehicle?.brand, vehicle?.model)
                            .joinToString(" ")
                            .ifBlank { vehicleId },
                        currentDeviceAddress = vehicle?.bluetoothDeviceId,
                        selectedAddress = vehicle?.bluetoothDeviceId,
                        bondedDevices = bluetoothScanner.getBondedDevices(),
                        isBluetoothEnabled = bluetoothScanner.isBluetoothEnabled(),
                        isLoading = false,
                    )
                }
            }
            .catch { e ->
                PaparcarLogger.e(TAG, "Failed to load vehicle for BT config", e)
                updateState { copy(isLoading = false) }
            }
            .launchIn(viewModelScope)
    }

    override fun handleIntent(intent: BluetoothConfigIntent) {
        when (intent) {
            is BluetoothConfigIntent.SelectDevice -> updateState { copy(selectedAddress = intent.address) }
            is BluetoothConfigIntent.Save -> save()
            is BluetoothConfigIntent.NavigateBack -> sendEffect(BluetoothConfigEffect.NavigateBack)
        }
    }

    private fun save() {
        val address = state.value.selectedAddress
        updateState { copy(isSaving = true) }
        viewModelScope.launch {
            runCatching { vehicleRepository.updateBluetoothDevice(vehicleId, address) }
                .onSuccess {
                    updateState { copy(isSaving = false, currentDeviceAddress = address) }
                    sendEffect(BluetoothConfigEffect.SavedSuccessfully)
                }
                .onFailure { e ->
                    PaparcarLogger.e(TAG, "Failed to update BT device", e)
                    updateState { copy(isSaving = false) }
                    sendEffect(BluetoothConfigEffect.ShowError(PaparcarError.Database.Unknown(e.message ?: "")))
                }
        }
    }

    private companion object {
        const val TAG = "BluetoothConfigViewModel"
    }
}
