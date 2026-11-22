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

enum class HomeEntryPrompt {
    NONE,
    PLUS,
    DETECTION,
    FIRST_PARKING,
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
    val canAskAdMeasurementConsent: Boolean,
)

/**
 * Returns the single Paparcar-owned prompt that may occupy Home now.
 *
 * The OS location prompt owns its own window, so the app waits both before it opens and while the
 * answer is in flight. Plus is news and gets the first app-owned slot. The consent question stays
 * last because it is not needed to use Home.
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
        HomeEntryPrompt.DETECTION -> inputs.detectionPitchPending
        HomeEntryPrompt.FIRST_PARKING -> inputs.firstParkingOfferPending
        HomeEntryPrompt.AD_MEASUREMENT -> inputs.canAskAdMeasurementConsent
    }
    if (currentStillPending) return current

    return when {
        inputs.plusMomentAvailable -> HomeEntryPrompt.PLUS
        inputs.detectionPitchPending -> HomeEntryPrompt.DETECTION
        inputs.firstParkingOfferPending -> HomeEntryPrompt.FIRST_PARKING
        inputs.canAskAdMeasurementConsent -> HomeEntryPrompt.AD_MEASUREMENT
        else -> HomeEntryPrompt.NONE
    }
}
