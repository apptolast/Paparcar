package com.rndeveloper.paparcar.presentation.map

import com.rndeveloper.paparcar.domain.error.PaparcarError

sealed class ParkingHistoryEffect {
    data class ShowError(val error: PaparcarError) : ParkingHistoryEffect()
}
