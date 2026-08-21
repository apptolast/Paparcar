package io.apptolast.paparcar.presentation.home

import com.swmansion.kmpmaps.core.MapType
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.ParkingReleaseReason
import io.apptolast.paparcar.domain.model.SearchResult
import io.apptolast.paparcar.domain.model.VehicleSize

sealed class HomeIntent {

    // ── Map & navigation ──────────────────────────────────────────────────────

    data class CameraPositionChanged(val lat: Double, val lon: Double) : HomeIntent()
    /** Reset the spot query centre to the user's GPS and move the camera there. */
    data object RecenterSpots : HomeIntent()
    data class SetMapType(val type: MapType) : HomeIntent()
    /** Map foreground (RESUMED) state — gates the high-accuracy user-location request so it only
     *  runs while the map is on screen (battery bound). [UI-LOC-FOREGROUND-001] */
    data class SetMapForeground(val active: Boolean) : HomeIntent()

    // ── Spot interactions ─────────────────────────────────────────────────────

    data object LoadNearbySpots : HomeIntent()
    /** null clears the selection; a spot or session id sets it. */
    /** [UI-PROVISIONAL-SPOT-IS-NOT-ITS-SESSION-001] Carries WHAT was tapped, not just its id — the
     *  kind is free at every tap site and a spot may legitimately share its session's id. */
    data class SelectItem(val selection: HomeSelection?) : HomeIntent()
    /** null clears the active size filter; non-null restricts to that size. */
    data class SetSizeFilter(val size: VehicleSize?) : HomeIntent()
    /** "Still there" (accepted = true) / "Gone" (accepted = false) community signal. */
    data class SendSpotSignal(val spotId: String, val accepted: Boolean) : HomeIntent()

    // ── Reporting mode ────────────────────────────────────────────────────────

    /** Enter reporting mode anchored at the map's current centre. */
    data class EnterReportMode(val lat: Double, val lon: Double) : HomeIntent()
    data object ExitReportMode : HomeIntent()
    data object ConfirmReportSpot : HomeIntent()
    /** Toggle the size selection for a manual spot report; passing null clears it. */
    data class SetReportingSize(val size: VehicleSize?) : HomeIntent()

    // ── Detection lifecycle ───────────────────────────────────────────────────

    /** Detection pipeline signals a parking event — shows the confirmation sheet. */
    data class ShowParkingConfirmation(val gps: GpsPoint) : HomeIntent()
    data object ConfirmDetectedParking : HomeIntent()
    data object DismissConfirmation : HomeIntent()

    /**
     * Cold-start "I'm driving" — start automatic detection now so it catches where the user parks.
     * [vehicleId] is the car the user declares they are driving: if it isn't already the active
     * vehicle it is made active first, so the parking is attributed to the right car (the active
     * vehicle IS the declaration). Null only if there is no resolvable vehicle. [DET-G-01b] [VEH-ACTIVE-FENCE-001]
     */
    data class StartDrivingDetection(val vehicleId: String?) : HomeIntent()

    /**
     * "Parar detección" on the live-trip row — the user says this trip is not theirs to follow.
     * Ends the session without planting a spot and without asking, and stays quiet for a while so
     * the automatic triggers do not restart it on their own. Not the Settings toggle: detection
     * itself stays on. [DET-STOP-BUTTON-001]
     */
    data object StopDetection : HomeIntent()

    /**
     * [DET-ASK-STATE-001] The user answered the in-app "did you park?" row. Sent to the very
     * commands the tray notification's two buttons send, so answering here and answering there are
     * the same event as far as detection is concerned.
     *
     * @param parked true = "yes, I parked"; false = "no, still driving".
     */
    data class AnswerParkingPrompt(val parked: Boolean) : HomeIntent()

    /** "Activate detection" from the Home banner — flips the Settings auto-detect flag back on
     *  (independent of permissions). [DET-TOGGLE-001] */
    data object EnableAutoDetection : HomeIntent()

    /** "Activate now" on the battery nudge — request the OS battery-optimization exemption (and the
     *  OEM foreground/autostart page) so the detection service survives on aggressive OEMs.
     *  [DET-BATTERY-EXEMPTION-NUDGE-001] */
    data object RequestBatteryExemption : HomeIntent()

    /**
     * "Reactivate" on the interrupted-watch row — rebuild the departure watcher the OS killed. This
     * used to fire [RequestBatteryExemption], which asks for a permission instead of restarting
     * anything: the row never cleared, and once the exemption was granted the OS dialog stopped
     * appearing, so the button went mute. The exemption belongs to the FRAGILE row, not this one.
     * [DET-WATCH-REACTIVATE-001]
     */
    data object ResumeWatch : HomeIntent()

    /** Explicitly dismiss the "where did you leave your car?" row — the user declines to mark;
     *  clears the durable nudge record AND its tray notification. [DET-NUDGE-PERSIST-001] */
    data object DismissParkNudge : HomeIntent()

    // ── Parking lifecycle ─────────────────────────────────────────────────────

    /**
     * Release the parking session identified by [sessionId] — the id of the card the
     * user tapped, never a ranked fallback. If no active session matches, the release
     * is a visible no-op (an inactive car's fence must not release the active car's
     * spot). The release location is the session's own parking location.
     *
     * [reason] says WHY the session closes and owns what follows — publishing the plaza and
     * declaring the car active. It is required on purpose: "I'm leaving" and "this record was
     * wrong" look identical from here, and only the first one declares which car you drive.
     * [VEH-ACTIVE-FENCE-001] [PEEK-ACTIONS-001] [PARK-DELETE-NO-DECLARE-001]
     */
    data class ReleaseParking(
        val sessionId: String,
        val reason: ParkingReleaseReason,
    ) : HomeIntent()

    /**
     * Enter manual parked-car positioning mode.
     * - [editingParkingId] null → create session for [targetVehicleId] (or default vehicle).
     * - [editingParkingId] non-null → move the existing session; [targetVehicleId] is ignored.
     * [MULTI-PARKING-001]
     */
    data class EnterAddParkingMode(
        val initialGps: GpsPoint?,
        val editingParkingId: String? = null,
        val targetVehicleId: String? = null,
        /** True when a DETECTION nudge opened this pin mode — the confirmed pin keeps detection
         *  provenance instead of being stamped a manual report. [DET-NUDGE-PIN-PROVENANCE-001] */
        val fromDetectionNudge: Boolean = false,
    ) : HomeIntent()

    data object ExitAddParkingMode : HomeIntent()
    /**
     * Confirm the parked-car position at the current camera centre (falls back to user GPS).
     * @param asNewSession from EDIT mode: `true` = "I parked somewhere else" → ignore the edited
     *   session's id and create a NEW session for its vehicle (the model supersedes the old one
     *   silently). `false` = correct THIS session's pin in place. [UX-PARKED-STATE-001]
     */
    data class ConfirmAddParking(val asNewSession: Boolean = false) : HomeIntent()

    // ── Zone management ───────────────────────────────────────────────────────

    /** Enter zone-placement mode anchored at the map's current centre. */
    data class EnterAddZoneMode(val lat: Double, val lon: Double) : HomeIntent()
    data object ExitAddZoneMode : HomeIntent()
    data object ConfirmAddZone : HomeIntent()
    data class UpdateAddingZoneName(val name: String) : HomeIntent()
    data class UpdateAddingZoneIcon(val iconKey: String) : HomeIntent()
    data class SetZoneRadius(val radius: Float) : HomeIntent()
    data class SetZoneIsPrivate(val isPrivate: Boolean) : HomeIntent()
    data class SelectZone(val zoneId: String) : HomeIntent()
    data class DeleteZone(val zoneId: String) : HomeIntent()
    data class EnterEditZoneMode(val zoneId: String) : HomeIntent()

    // ── Search ────────────────────────────────────────────────────────────────

    data class SearchQueryChanged(val query: String) : HomeIntent()
    data class SelectSearchResult(val result: SearchResult) : HomeIntent()
    data object ClearSearch : HomeIntent()

    // ── Debug ─────────────────────────────────────────────────────────────────

    data object ReportTestSpot : HomeIntent()
}
