package com.rndeveloper.paparcar.presentation.home

import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.SpotReportGate
import com.rndeveloper.paparcar.domain.model.SpotReportPolicy
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Zone
import com.rndeveloper.paparcar.domain.model.ZoneIcon
import com.rndeveloper.paparcar.presentation.util.distanceMeters

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
 * In-flight markers (isReporting / isSavingZone / savingParkingAsNewSession /
 * releasingReason) are intentionally left alone: they reflect a running
 * operation, not the user-facing mode. The last two name WHICH action is running rather than
 * merely that one is, so the spinner can sit on the button that was pressed.
 * [UI-A-SPINNER-LIVES-IN-THE-BUTTON-THAT-WAS-PRESSED-001]
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
    addingZoneNotify = false,
    editingZoneId = null,
    editingParkingId = null,
    editingStillParkedQuestion = null,
    parkingCorrectionSessionId = null,
    addingParkingVehicleId = null,
    addingParkingNudgeSource = null,
)

/**
 * Applies the active sessions and closes the release dialog when the session it asks about is no
 * longer active — closed by another path (detected departure, safety net, another device) while the
 * user was looking at it. Releasing a session that no longer exists can only fail, so the dialog has
 * nothing left to offer.
 *
 * Not while a release is in flight: that release clears the session from Room itself before it
 * finishes, and its outcome is what closes the dialog. [PARK-RELEASE-THE-SCREEN-NEVER-SAW-001]
 */
internal fun HomeState.withActiveSessions(sessions: List<UserParking>): HomeState {
    val dialogSessionId = releaseDialogSessionId
    val dialogTargetGone = dialogSessionId != null &&
        !isReleasingParking &&
        sessions.none { it.id == dialogSessionId }
    val editingTargetGone = editingParkingId != null && sessions.none { it.id == editingParkingId } &&
        correctingParking == null && !isSavingParking && !isReleasingParking
    val next = if (editingTargetGone) clearedModeFields() else this
    return next.copy(
        activeSessions = sessions,
        releaseDialogSessionId = if (dialogTargetGone) null else dialogSessionId,
    )
}

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
        // A spots refresh says nothing about fuel stations — their peek survives it untouched.
        // [FUEL-STATIONS-MAP-AND-PEEK-001]
        is HomeSelection.FuelStation -> true
        // Nor about car parks. [PARKINGS-ON-THE-MAP-001]
        is HomeSelection.PublicParking -> true
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

/**
 * How far the report pin sits from the measured fix, or null when either end is missing.
 *
 * Measured HERE, from the state that owns both the fix and the pin, for the same reason the vote
 * measures itself in the ViewModel: a gate whose input is passed in by the call site is a gate the
 * next call site forgets. [SPOT-A-REPORT-YOU-CANNOT-SEE-IS-NOT-A-REPORT-001]
 */
internal fun HomeState.pinDistanceFromUserMeters(): Double? {
    val (lat, lon) = pinCoordinates() ?: return null
    val me = userGpsPoint ?: return null
    return distanceMeters(me.latitude, me.longitude, lat, lon).toDouble()
}

/** Whether the spot under the report pin may be published at all. */
internal fun HomeState.reportGate(): SpotReportGate =
    SpotReportPolicy.gateFor(pinDistanceFromUserMeters())
