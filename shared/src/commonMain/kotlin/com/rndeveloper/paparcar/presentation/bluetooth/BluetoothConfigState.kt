package com.rndeveloper.paparcar.presentation.bluetooth

import com.rndeveloper.paparcar.domain.model.bluetooth.BluetoothDeviceInfo

data class BluetoothConfigState(
    val vehicleId: String = "",
    val vehicleName: String = "",
    val bondedDevices: List<BluetoothDeviceInfo> = emptyList(),
    val currentDeviceAddress: String? = null,
    val selectedAddress: String? = null,
    val isBluetoothEnabled: Boolean = true,
    val hasBluetoothPermission: Boolean = true,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
) {
    val hasChanges: Boolean get() = selectedAddress != currentDeviceAddress
}
