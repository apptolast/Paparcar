package io.apptolast.paparcar.presentation.home

import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.Zone
import io.apptolast.paparcar.domain.model.ZoneIcon

// ─────────────────────────────────────────────────────────────────────────────
// HomeStateTransitions — the PURE `HomeState → HomeState` transitions of Home's
// mode machine. No coroutines, no collaborators: just state algebra, which makes
// the mode↔selection invariant below directly unit-testable without a ViewModel
// harness. The async orchestration (confirm/save/release flows) stays in
// HomeViewModel — these are the building blocks it applies. [HOMEVM-CTRL-004]
// ─────────────────────────────────────────────────────────────────────────────

// ── Mode invariant ────────────────────────────────────────────────────────────
//
// Selection (selectedItemId) and add-modes (Reporting / AddingZone /
// AddingParking) are mutually exclusive:
//   mode != Browse         ⇒  selectedItemId == null
//   selectedItemId != null ⇒  mode == Browse
//
// Enforcement sites:
//   • EnterReportMode / EnterAddParkingMode / EnterAddZoneMode / EnterEditZoneMode
//     all clear `selectedItemId` on entry.
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
 * (selectedItemId). Callers that need to set a selection or re-enter a mode
 * apply their fields via `.copy(...)` on top of this base.
 *
 * In-flight booleans (isReporting / isSavingZone / isSavingParking /
 * isReleasingParking) are intentionally left alone: they reflect a running
 * operation, not the user-facing mode.
 *
 * **Invariant enforced here:** `mode != Browse ⇒ selectedItemId == null`.
 * Every Enter*Mode / SelectItem path goes through this helper so the
 * invariant cannot drift as new mode-scoped fields are added. [BUG-5]
 */
internal fun HomeState.clearedModeFields(): HomeState = copy(
    mode = HomeMode.Browse,
    selectedItemId = null,
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
)

/**
 * Applies the freshly-fetched nearby spots and prunes the selection if the
 * selected item is no longer either an active session or one of the visible
 * spots. Keeps the selection logic adjacent to the data update without
 * inlining it inside the flow operator. [A1]
 */
internal fun HomeState.applyNewSpots(spots: List<Spot>): HomeState {
    val cur = selectedItemId
    val selectionStillValid = cur == null ||
        activeSessions.any { it.id == cur } ||
        spots.any { it.id == cur }
    return copy(
        nearbySpots = spots,
        selectedItemId = if (selectionStillValid) cur else null,
    )
}

/** Wipes every search-related field. Used by SelectSearchResult + ClearSearch. */
internal fun HomeState.resetSearch(): HomeState =
    copy(searchQuery = "", searchResults = emptyList(), isSearchActive = false, isSearching = false)
