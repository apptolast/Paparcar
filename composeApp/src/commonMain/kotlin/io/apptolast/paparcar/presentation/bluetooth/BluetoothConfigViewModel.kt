package io.apptolast.paparcar.presentation.bluetooth

import io.apptolast.paparcar.domain.bluetooth.BluetoothScanner
import io.apptolast.paparcar.domain.error.PaparcarError
import io.apptolast.paparcar.domain.model.displayName
import io.apptolast.paparcar.domain.permissions.PermissionManager
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
    private val permissionManager: PermissionManager,
) : BaseViewModel<BluetoothConfigState, BluetoothConfigIntent, BluetoothConfigEffect>() {

    override fun initState() = BluetoothConfigState()

    init {
        val id = vehicleId
        updateState { copy(vehicleId = id) }

        vehicleRepository.observeVehicles()
            .onEach { vehicles ->
                val vehicle = vehicles.find { it.id == vehicleId }
                val hasBtPermission = permissionManager.permissionState.value.hasBluetoothConnectPermission
                updateState {
                    copy(
                        vehicleName = vehicle?.displayName(fallback = vehicleId) ?: vehicleId,
                        currentDeviceAddress = vehicle?.bluetoothDeviceId,
                        selectedAddress = vehicle?.bluetoothDeviceId,
                        bondedDevices = if (hasBtPermission) bluetoothScanner.getBondedDevices() else emptyList(),
                        isBluetoothEnabled = bluetoothScanner.isBluetoothEnabled(),
                        hasBluetoothPermission = hasBtPermission,
                        isLoading = false,
                    )
                }
            }
            .catch { e ->
                PaparcarLogger.e(TAG, "Failed to load vehicle for BT config", e)
                updateState { copy(isLoading = false) }
            }
            .launchIn(viewModelScope)

        // React to permission changes (e.g., user grants BLUETOOTH_CONNECT from app settings)
        permissionManager.permissionState
            .onEach { perms ->
                val hasBtPermission = perms.hasBluetoothConnectPermission
                updateState {
                    copy(
                        hasBluetoothPermission = hasBtPermission,
                        bondedDevices = if (hasBtPermission) bluetoothScanner.getBondedDevices() else emptyList(),
                        isBluetoothEnabled = bluetoothScanner.isBluetoothEnabled(),
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    override fun handleIntent(intent: BluetoothConfigIntent) {
        when (intent) {
            is BluetoothConfigIntent.SelectDevice -> updateState { copy(selectedAddress = intent.address) }
            is BluetoothConfigIntent.Save -> save()
            is BluetoothConfigIntent.NavigateBack -> sendEffect(BluetoothConfigEffect.NavigateBack)
            is BluetoothConfigIntent.RefreshState -> refreshScannerState()
        }
    }

    private fun refreshScannerState() {
        permissionManager.refreshPermissions()
        val hasBtPermission = permissionManager.permissionState.value.hasBluetoothConnectPermission
        updateState {
            copy(
                hasBluetoothPermission = hasBtPermission,
                bondedDevices = if (hasBtPermission) bluetoothScanner.getBondedDevices() else emptyList(),
                isBluetoothEnabled = bluetoothScanner.isBluetoothEnabled(),
            )
        }
    }

    private fun save() {
        val address = state.value.selectedAddress
        updateState { copy(isSaving = true) }
        viewModelScope.launch {
            vehicleRepository.updateBluetoothDevice(vehicleId, address)
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
