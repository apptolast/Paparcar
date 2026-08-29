package com.rndeveloper.paparcar.presentation.map

sealed class ParkingHistoryIntent {
    data class OnSpotSelected(val spotId: String) : ParkingHistoryIntent()
    data class SetFocusedSession(val sessionId: String) : ParkingHistoryIntent()

    /** Step back in time to the OLDER history entry — the left ‹ chevron. [HISTORY-DETAIL-002] */
    data object FocusOlder : ParkingHistoryIntent()

    /** Step forward in time to the NEWER history entry — the right › chevron. [HISTORY-DETAIL-002] */
    data object FocusNewer : ParkingHistoryIntent()

    /** The user's verdict on the route's road-inferred stretches — the answer to the
     *  "did you drive this way?" card. [ROUTE-GAP-HONEST-001] */
    data class ResolveInferredRoute(
        val sessionId: String,
        val confirmed: Boolean,
    ) : ParkingHistoryIntent()
}
