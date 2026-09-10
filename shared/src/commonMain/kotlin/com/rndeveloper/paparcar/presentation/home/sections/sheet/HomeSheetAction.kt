package com.rndeveloper.paparcar.presentation.home.sections.sheet

import com.rndeveloper.paparcar.domain.onboarding.FirstStep
import com.rndeveloper.paparcar.presentation.home.model.HomeNearbyLens

/**
 * The single UI-action channel of the bottom sheet. [HOME-ATOMIZE-001 F3]
 *
 * Everything the sheet needs that is NOT a plain ViewModel intent — local UI
 * orchestration (sheet motion, dialogs, list expansion), camera moves and
 * navigation — flows through `onAction: (HomeSheetAction) -> Unit`, translated
 * in ONE place (HomeSheetSection in HomeScreen.kt). Actions that are 1:1 with a
 * [com.rndeveloper.paparcar.presentation.home.HomeIntent] are NOT mirrored here:
 * the sheet emits those directly via its `onIntent` channel.
 */
sealed interface HomeSheetAction {
    /** Tap on the peek header — toggle between peek and the adjacent snap. */
    data object ToggleSheet : HomeSheetAction

    /**
     * Step the peek to another pin: open [spotId] exactly as tapping ITS MARKER on the map does —
     * select + fly the camera + settle the sheet. Wired to the very lambda the map uses, so a
     * stepper press and a marker tap can't drift apart. [UI-PEEK-STEPS-BETWEEN-PINS-001]
     */
    data class SelectSpot(val spotId: String) : HomeSheetAction

    /** A fuel-station row or capsule was chosen: select it, fly the camera, open its peek.
     *  [FUEL-STATIONS-MAP-AND-PEEK-001] */
    data class SelectFuelStation(val stationId: String) : HomeSheetAction

    /** Same gesture for a car park row. [PARKINGS-ON-THE-MAP-001] */
    data class SelectPublicParking(val parkingId: String) : HomeSheetAction

    /**
     * Step the car lane of the peek to [vehicleId] — EVERY registered vehicle is a stop, and each
     * one resolves to ITS modal: with an active session it opens that session's peek exactly as
     * tapping its marker; without one it opens the add-parking peek exactly as tapping the
     * vehicle's "Aparcar" chip. [UI-PEEK-STEPS-WALK-VEHICLES-NOT-SESSIONS-001]
     */
    data class StepToVehicle(val vehicleId: String) : HomeSheetAction

    /**
     * "Me voy" on the parking peek — open the release dialog (publish / delete-only)
     * for [sessionId], the session shown in that peek. The id travels so the release
     * targets THIS card, not whichever session ranks first. [VEH-ACTIVE-FENCE-001]
     */
    data class RequestRelease(val sessionId: String) : HomeSheetAction

    /** "Report a free spot" CTA — enter Reporting mode centred on the current camera. */
    data object RequestReportMode : HomeSheetAction

    /** Fly the map camera to a coordinate (row tap, parking row tap…). */
    data class MoveCamera(val lat: Double, val lon: Double) : HomeSheetAction

    /** Launch the platform's external navigation (Google/Apple Maps). */
    data class NavigateExternal(val lat: Double, val lon: Double, val walking: Boolean) : HomeSheetAction

    /** CORE blocker CTA / detection surface — open the permission flow focused on core. */
    data object OpenCorePermissions : HomeSheetAction

    /** Detection surface — navigate to vehicle registration. */
    data object AddVehicle : HomeSheetAction

    /**
     * Tap on a guided first-step row — open its explainer sheet.
     * [ONBOARDING-A-SPOT-IS-BORN-TWO-WAYS-001]
     *
     * An ACTION and not a [com.rndeveloper.paparcar.presentation.home.HomeIntent] because opening
     * the sheet changes nothing the ViewModel owns: no progress is banked by reading, and a step
     * completes on measured state either way. It is exactly what this channel is for — local UI
     * orchestration, translated in one place.
     */
    data class OpenFirstStepExplainer(val step: FirstStep) : HomeSheetAction

    /**
     * Take the user to a lane of this very sheet — point the lane at [lens], expand the sheet and
     * scroll to the tab row. Fired by the checklist: the SEE_NEARBY face of [FirstStep.FIND_SPOT]
     * asks for the spots, [FirstStep.EXPLORE_NEARBY] asks for the car parks.
     * [ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001]
     * [ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001]
     *
     * It was `RevealFreeSpots`, with the lane hard-coded to spots. "Take me to a lane" was always the
     * gesture; naming one lane in the action meant the second caller had to copy its three moves
     * (point the lane · expand · scroll) instead of asking for the same thing with another argument.
     *
     * An action, not an intent, for the same reason as the explainer above: it moves this sheet, and
     * the ViewModel owns nothing about where the sheet is resting. ⛔ The lane itself IS a ViewModel
     * concern, so the translation fires `HomeIntent.SetNearbyLens` — never a toggle, so this cannot
     * flip a lane the user already chose into something else.
     */
    data class RevealNearbyLane(val lens: HomeNearbyLens) : HomeSheetAction

    /**
     * Link [vehicleId] to one of the phone's already-paired Bluetooth devices — the deep link into
     * the car-Bluetooth screen, the same destination Settings uses.
     * [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001]
     */
    data class LinkVehicleBluetooth(val vehicleId: String) : HomeSheetAction
}
