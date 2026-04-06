package io.apptolast.paparcar.presentation.bluetooth

import io.apptolast.paparcar.domain.error.PaparcarError

sealed class BluetoothConfigEffect {
    data object NavigateBack : BluetoothConfigEffect()
    data object SavedSuccessfully : BluetoothConfigEffect()
    data class ShowError(val error: PaparcarError) : BluetoothConfigEffect()
}
