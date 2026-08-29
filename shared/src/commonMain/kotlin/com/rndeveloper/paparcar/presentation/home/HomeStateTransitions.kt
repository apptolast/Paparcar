package com.rndeveloper.paparcar.presentation.home

import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.Zone
import com.rndeveloper.paparcar.domain.model.ZoneIcon

// ─────────────────────────────────────────────────────────────────────────────
// HomeStateTransitions — the PURE `HomeState → HomeState` transitions of Home's
// mode machine. No coroutines, no collaborators: just state algebra, which makes
// the mode↔selection invariant below directly unit-testable without a ViewModel
// harness. The async orchestration (confirm/save/release flows) stays in
// HomeViewModel — these are the building blocks it applies. [HOMEVM-CTRL-004]
// ─────────────────────────────────────────────────────────────────────────────

// ── Mode invariant ────────────────────────────────────────────────────────────
//
// Selection ([HomeState.selection]) and add-modes (Reporting / AddingZone /
// AddingParking) are mutually exclusive:
//   mode != Browse      ⇒  selection == null
//   selection != null   ⇒  mode == Browse
//
// Enforcement sites:
//   • EnterReportMode / EnterAddParkingMode / EnterAddZoneMode / EnterEditZoneMode
//     all clear `selection` on entry.
//   • SelectItem calls [clearedModeFields] before applying the new selection,
//     so picking a marker silently exits any active add-mode. (selectZone only
//     moves the camera — a zone is not a selection.)
//
// Use this helper for any new transition from a non-Browse mode back to Browse
// — it wipes every field that belongs to a non-Browse mode in one place, so
// the invariant cannot drift as new mode-scoped fields are added.

/**
 * Returns a copy of this state reset to [HomeMode.Browse], clearing every
 * field that is owned by a non-Browse mode (pin coords, camera-moving flag,
 * report/zone/parking form fields, editing IDs) AND the selection field
 * ([HomeState.selection]). Callers that need to set a selection or re-enter a mode
 * apply their fields via `.copy(...)` on top of this base.
 *
 * In-flight booleans (isReporting / isSavingZone / isSavingParking /
 * isReleasingParking) are intentionally left alone: they reflect a running
 * operation, not the user-facing mode.
 *
 * **Invariant enforced here:** `mode != Browse ⇒ selection == null`.
 * Every Enter*Mode / SelectItem path goes through this helper so the
 * invariant cannot drift as new mode-scoped fields are added. [BUG-5]
 */
internal fun HomeState.clearedModeFields(): HomeState = copy(
    mode = HomeMode.Browse,
    selection = null,
    pinCameraLat = null,
    pinCameraLon = null,
    isCameraMoving = false,
    reportingSize = null,
    addingZoneName = "",
    addingZoneIconKey = ZoneIcon.DEFAULT,
    addingZoneRadius = Zone.DEFAULT_RADIUS_METERS,
    addingZoneIsPrivate = false,
    editingZoneId = null,
    editingParkingId = null,
    addingParkingVehicleId = null,
    addingParkingFromDetectionNudge = false,
)

/**
 * Applies the freshly-fetched nearby spots and prunes the selection if the
 * selected item is no longer either an active session or one of the visible
 * spots. Keeps the selection logic adjacent to the data update without
 * inlining it inside the flow operator. [A1]
 */
internal fun HomeState.applyNewSpots(spots: List<Spot>): HomeState {
    val cur = selection
    // [UI-PROVISIONAL-SPOT-IS-NOT-ITS-SESSION-001] Each kind is checked against ITS OWN list. The
    // old shape accepted a spot selection because a SESSION happened to share the id, which is how
    // a selection could survive its own spot disappearing.
    val selectionStillValid = when (cur) {
        null -> true
        is HomeSelection.Parking -> activeSessions.any { it.id == cur.id }
        is HomeSelection.Spot -> spots.any { it.id == cur.id }
    }
    return copy(
        nearbySpots = spots,
        selection = if (selectionStillValid) cur else null,
    )
}

/** Wipes every search-related field. Used by SelectSearchResult + ClearSearch. */
internal fun HomeState.resetSearch(): HomeState =
    copy(
        searchQuery = "",
        searchResults = emptyList(),
        isSearchActive = false,
        isSearching = false,
        searchNoResults = false,
    )

/**
 * The settled pin-mode coordinate, or null when the active mode never captured
 * one (GPS unavailable on entry and the camera never moved). The single guard
 * shared by every pin-mode confirm — report / parking / zone. [HOME-ATOMIZE-001 F4]
 */
internal fun HomeState.pinCoordinates(): Pair<Double, Double>? {
    val lat = pinCameraLat ?: return null
    val lon = pinCameraLon ?: return null
    return lat to lon
}
