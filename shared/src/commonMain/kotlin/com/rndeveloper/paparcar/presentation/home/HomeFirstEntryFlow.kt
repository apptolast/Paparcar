package com.rndeveloper.paparcar.presentation.home

/**
 * The first Home visit has several independent owners for one-time prompts. Resolve their order
 * here so a system permission dialog, a Plus moment, the detection invitation, parking editor, and
 * advertising consent can never be composed over one another.
 */
/** Completing the initial offer in this visit does not make the ad question eligible yet. */
fun HomeState.canAskHomeAdConsent(isEligible: Boolean, leftHomeAfterCompletion: Boolean): Boolean =
    isEligible && firstHomeParkingFlowReady &&
        (firstHomeParkingFlowCompletedOnArrival || leftHomeAfterCompletion)

/**
 * How recent a stop at a pump must be for HOME to ask about it.
 * [DET-A-STOP-AT-A-PUMP-IS-NOT-A-PARKING-001]
 *
 * ⛔ The bound exists because [DET-A-STILL-PARKED-ASK-MUST-EARN-ITS-INTERRUPTION-001] settled that
 * an interruption is earned: a dialog about a tankful from three weeks ago is noise, and the user
 * cannot answer it honestly anyway. Past this, the question does not disappear — it waits in the
 * vehicle's history, where the user arrives by choice rather than being stopped on the way in.
 *
 * A day, because that is the span over which "the stop I just made" stays a single remembered
 * event: the drive home, the evening, the next morning's walk back to the car.
 */
const val REFUEL_ASK_MAX_AGE_MS: Long = 24L * 60L * 60L * 1000L

/** Whether a stop that ended at [endedAtMs] is still fresh enough for Home to ask about. A stop
 *  with no close instant (legacy rows) is not: nothing can date it. */
fun refuelAskIsFresh(endedAtMs: Long?, nowMs: Long): Boolean =
    endedAtMs != null && nowMs - endedAtMs in 0..REFUEL_ASK_MAX_AGE_MS

enum class HomeEntryPrompt {
    NONE,
    PLUS,
    FIRST_PARKING,
    DETECTION,
    REFUEL,
    AD_MEASUREMENT,
}

data class HomeEntryPromptInputs(
    val isHomeTabActive: Boolean,
    val isBrowse: Boolean,
    val isLocationRequestPending: Boolean,
    val isWaitingForPermissionRoute: Boolean,
    val plusMomentAvailable: Boolean,
    val detectionPitchPending: Boolean,
    val firstParkingOfferPending: Boolean,
    /** A fresh stop at a pump is waiting for its answer and the user has not waved it away.
     *  [DET-A-STOP-AT-A-PUMP-IS-NOT-A-PARKING-001] */
    val refuelAskPending: Boolean,
    val canAskAdMeasurementConsent: Boolean,
)

/**
 * Returns the single Paparcar-owned prompt that may occupy Home now.
 *
 * The OS location prompt owns its own window, so the app waits both before it opens and while the
 * answer is in flight. Plus is news and gets the first app-owned slot. The consent question stays
 * last because it is not needed to use Home.
 *
 * [ONB-A-WATCH-NEEDS-A-CAR-PARKED-FIRST-001] The parking editor outranks the detection pitch: the
 * pitch invites the user to watch their car automatically, and until a car is actually marked there
 * is nothing for that watch to point at. Asking first inverts the story — it sells a feature with no
 * subject yet, which is the same mistake [ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001] already
 * fixed one layer up (permissions before value). The pitch itself only arms once the parking is
 * actually confirmed (see [HomeViewModel.confirmAddParking]), never on a closed-without-saving
 * editor, so it is never offered without a real pin behind it.
 *
 * The refuel question sits between the onboarding prompts and the consent one: it is about
 * something the user just did — so it outranks housekeeping — but it must never cut in front of a
 * first visit that is still explaining what the app is.
 * [DET-A-STOP-AT-A-PUMP-IS-NOT-A-PARKING-001]
 */
fun resolveHomeEntryPrompt(
    inputs: HomeEntryPromptInputs,
    current: HomeEntryPrompt = HomeEntryPrompt.NONE,
): HomeEntryPrompt {
    if (!inputs.isHomeTabActive || !inputs.isBrowse ||
        inputs.isLocationRequestPending || inputs.isWaitingForPermissionRoute
    ) {
        return HomeEntryPrompt.NONE
    }

    // An asynchronous gift never interrupts a question the user is already answering.
    val currentStillPending = when (current) {
        HomeEntryPrompt.NONE -> false
        HomeEntryPrompt.PLUS -> inputs.plusMomentAvailable
        HomeEntryPrompt.FIRST_PARKING -> inputs.firstParkingOfferPending
        HomeEntryPrompt.DETECTION -> inputs.detectionPitchPending
        HomeEntryPrompt.REFUEL -> inputs.refuelAskPending
        HomeEntryPrompt.AD_MEASUREMENT -> inputs.canAskAdMeasurementConsent
    }
    if (currentStillPending) return current

    return when {
        inputs.plusMomentAvailable -> HomeEntryPrompt.PLUS
        inputs.firstParkingOfferPending -> HomeEntryPrompt.FIRST_PARKING
        inputs.detectionPitchPending -> HomeEntryPrompt.DETECTION
        inputs.refuelAskPending -> HomeEntryPrompt.REFUEL
        inputs.canAskAdMeasurementConsent -> HomeEntryPrompt.AD_MEASUREMENT
        else -> HomeEntryPrompt.NONE
    }
}
