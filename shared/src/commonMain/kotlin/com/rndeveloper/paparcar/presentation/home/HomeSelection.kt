package com.rndeveloper.paparcar.presentation.home

import androidx.compose.runtime.Immutable

/**
 * What the user has tapped on Home — a community spot or one of their own parked sessions.
 * [UI-PROVISIONAL-SPOT-IS-NOT-ITS-SESSION-001]
 *
 * **Why this is a type and not a String.** Home used to hold a bare `selectedItemId` and recover the
 * KIND by asking each list whether it contained that id, with the session winning ties. The KDoc
 * that licensed it said *"both share the same UUID space so equality resolves the type"* — and that
 * is false by construction, not by accident: a freed spot deliberately REUSES its session's id
 * (`ProcessConfirmedDepartureUseCase`, `ReleaseActiveParkingSessionUseCase`) so that a republish
 * rewrites the same document instead of duplicating it.
 *
 * The collision therefore happens on every deduced departure, and it happened in the field on
 * 2026-08-21 23:46: with the session deliberately kept alive beside its provisional spot, the spot
 * was unselectable — tapping it opened the car's peek and lit the car's marker.
 *
 * The kind is known for free at every tap site (a spot marker, a spot row, a vehicle card). Carrying
 * it costs nothing and removes the ambiguity instead of arbitrating it.
 */
@Immutable
sealed interface HomeSelection {
    val id: String

    /** A community spot from the nearby feed. */
    data class Spot(override val id: String) : HomeSelection

    /** One of the user's own active parking sessions. */
    data class Parking(override val id: String) : HomeSelection
}
