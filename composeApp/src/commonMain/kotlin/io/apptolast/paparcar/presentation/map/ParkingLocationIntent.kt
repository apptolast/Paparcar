package io.apptolast.paparcar.presentation.map

sealed class ParkingLocationIntent {
    data class OnSpotSelected(val spotId: String) : ParkingLocationIntent()
    data class SetFocusedSession(val sessionId: String) : ParkingLocationIntent()

    /** Step back in time to the OLDER history entry — the left ‹ chevron. [HISTORY-DETAIL-002] */
    data object FocusOlder : ParkingLocationIntent()

    /** Step forward in time to the NEWER history entry — the right › chevron. [HISTORY-DETAIL-002] */
    data object FocusNewer : ParkingLocationIntent()

    /** The user's verdict on the route's road-inferred stretches — the answer to the
     *  "did you drive this way?" card. [ROUTE-GAP-HONEST-001] */
    data class ResolveInferredRoute(
        val sessionId: String,
        val confirmed: Boolean,
    ) : ParkingLocationIntent()
}
