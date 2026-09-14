package com.rndeveloper.paparcar.presentation.home.model

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
enum class FirstStepGuide {
    /** The free-spots tab and the list under it. */
    SPOTS_LANE,

    /** The "Report a free spot" card at the bottom of the spots lane. */
    REPORT_SPOT_CARD,

    /** The car parks and fuel tabs of the nearby lane — highlighted, never selected for the user. */
    NEARBY_COMPLEMENT_TABS,

    /** The add-zone chip in the floating header over the map (the sheet collapses to reveal it). */
    ADD_ZONE_CHIP,
}
