package com.rndeveloper.paparcar.presentation.home.model

import com.rndeveloper.paparcar.domain.onboarding.FirstStep
import com.rndeveloper.paparcar.presentation.home.HomeIntent
import com.rndeveloper.paparcar.presentation.home.HomeSelection
import com.rndeveloper.paparcar.presentation.home.sections.sheet.HomeSheetAction

/**
 * Where a checklist step POINTS the user, instead of opening the feature for them.
 * [FIRST-STEPS-SHOW-WHERE-NOT-OPEN-001]
 *
 * A step that opened the report flow or picked a lane on the user's behalf taught them where the
 * button of the tutorial is, not where the button of the app is — and the tutorial is gone next
 * week. So a step now scrolls or collapses Home to the real control and highlights it for a few
 * seconds; using it stays the user's own tap.
 *
 * UI state, not product state: it lives in `HomeContent` for as long as the highlight is on screen.
 */
enum class FirstStepGuide(
    /**
     * The step that points here. Declared, not inferred: the switch that turns a highlight off when
     * its step stops asking needs to know WHICH step asked, and reading it off a `!=` in the screen
     * is how three of these four ended up never switching off at all.
     * [ONB-A-GUIDE-MUST-NOT-OUTLIVE-THE-STEP-THAT-ASKED-FOR-IT-001]
     */
    val step: FirstStep,

    /**
     * Whether the step stays open AFTER the tap that lit the highlight.
     *
     * ⛔ This is the whole subtlety, and getting it wrong breaks the guide in the opposite
     * direction. `FIND_SPOT` and `EXPLORE_NEARBY` close **on the very tap** that lights the
     * highlight — knowing where the lane is IS the learning, so the checklist banks it at once. So
     * "turn the highlight off when the step is no longer current" fires in the same frame and the
     * highlight is never seen. Only `PRIVATE_ZONE` outlives its tap: it needs a zone that really
     * exists, or an explicit "I have no garage".
     */
    val stepOutlivesTheTap: Boolean,
) {
    /** The free-spots tab and the list under it. */
    SPOTS_LANE(step = FirstStep.FIND_SPOT, stepOutlivesTheTap = false),

    /** The "Report a free spot" card at the bottom of the spots lane. */
    REPORT_SPOT_CARD(step = FirstStep.FIND_SPOT, stepOutlivesTheTap = false),

    /** The car parks and fuel tabs of the nearby lane — highlighted, never selected for the user. */
    NEARBY_COMPLEMENT_TABS(step = FirstStep.EXPLORE_NEARBY, stepOutlivesTheTap = false),

    /** The add-zone chip in the floating header over the map (the sheet collapses to reveal it). */
    ADD_ZONE_CHIP(step = FirstStep.PRIVATE_ZONE, stepOutlivesTheTap = true),
    ;

    /**
     * Did the user just USE the control this points at? That is what turns the highlight off, and
     * it is the switch the KDoc of `HomeScreen` has promised all along while only one of the three
     * actually worked. [ONB-A-GUIDE-MUST-NOT-OUTLIVE-THE-STEP-THAT-ASKED-FOR-IT-001]
     *
     * ONE rule for the four destinations, asked of the intent the control already emits — so no
     * callback has to be threaded down four component trees, and no destination gets a rule of its
     * own. A rule written inside the single lane that uses it is not a rule of the system; that is
     * exactly how this bug was born, and the same lesson as
     * [DET-A-SAVED-CARD-MUST-NOT-OUTLIVE-ITS-TRIP-001].
     */
    fun isUsedBy(intent: HomeIntent): Boolean = when (this) {
        // The tab AND the list under it: both are what this points at, so someone who taps a row
        // has plainly found the lane.
        SPOTS_LANE ->
            (intent is HomeIntent.SetNearbyLens && intent.lens == HomeNearbyLens.SPOTS) ||
                (intent is HomeIntent.SelectItem && intent.selection is HomeSelection.Spot)
        NEARBY_COMPLEMENT_TABS ->
            intent is HomeIntent.SetNearbyLens &&
                intent.lens in setOf(HomeNearbyLens.PARKINGS, HomeNearbyLens.FUEL)
        ADD_ZONE_CHIP -> intent is HomeIntent.EnterAddZoneMode
        REPORT_SPOT_CARD -> false // it is not an intent — see the overload below
    }

    /**
     * The same rule over the other funnel. The report card is the one control of the four that
     * acts through [HomeSheetAction] instead of [HomeIntent], so the rule needs both doors — and
     * asking it twice is still ONE rule, where four `if`s scattered across four component trees
     * would not be.
     */
    fun isUsedBy(action: HomeSheetAction): Boolean = when (this) {
        REPORT_SPOT_CARD -> action is HomeSheetAction.RequestReportMode
        SPOTS_LANE, NEARBY_COMPLEMENT_TABS, ADD_ZONE_CHIP -> false // they act through HomeIntent
    }
}
