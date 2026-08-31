package com.rndeveloper.paparcar.presentation.map

import com.rndeveloper.paparcar.domain.error.PaparcarError

sealed class ParkingHistoryEffect {
    /** The focused parking was withdrawn: there is nothing left to read here, so the screen leaves.
     *  [PARK-A-HISTORIC-PARKING-CAN-BE-WITHDRAWN-001] */
    data object Withdrawn : ParkingHistoryEffect()

    data class ShowError(val error: PaparcarError) : ParkingHistoryEffect()
}
