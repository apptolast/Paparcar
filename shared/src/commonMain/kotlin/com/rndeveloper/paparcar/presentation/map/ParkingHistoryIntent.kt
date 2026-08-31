package com.rndeveloper.paparcar.presentation.map

sealed class ParkingHistoryIntent {
    // No spot intent: this screen renders no spots (it passes `spots = emptyList()` and an empty
    // click handler), so `OnSpotSelected` + its `NavigateToSpotDetails` effect were unreachable and
    // were removed rather than left as a door onto nothing.
    // [UI-HISTORY-DETAIL-MUST-NOT-SPEAK-BEFORE-IT-KNOWS-001]
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
