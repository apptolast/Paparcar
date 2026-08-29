package com.rndeveloper.paparcar.presentation.bluetooth

import com.rndeveloper.paparcar.domain.error.PaparcarError

sealed class BluetoothConfigEffect {
    data object NavigateBack : BluetoothConfigEffect()
    data object SavedSuccessfully : BluetoothConfigEffect()
    data class ShowError(val error: PaparcarError) : BluetoothConfigEffect()
}
