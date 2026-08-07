package io.apptolast.paparcar.presentation.map

sealed class ParkingLocationIntent {
    data class OnSpotSelected(val spotId: String) : ParkingLocationIntent()
    data class SetFocusedSession(val sessionId: String) : ParkingLocationIntent()

    /** Step to the more-recent history entry (in-screen stepper). [HISTORY-DETAIL-001] */
    data object FocusPrevious : ParkingLocationIntent()

    /** Step to the older history entry (in-screen stepper). [HISTORY-DETAIL-001] */
    data object FocusNext : ParkingLocationIntent()
}
