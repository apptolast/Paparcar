package io.apptolast.paparcar.presentation.bluetooth

sealed class BluetoothConfigIntent {
    data class SelectDevice(val address: String?) : BluetoothConfigIntent()
    data object Save : BluetoothConfigIntent()
    data object NavigateBack : BluetoothConfigIntent()
}
