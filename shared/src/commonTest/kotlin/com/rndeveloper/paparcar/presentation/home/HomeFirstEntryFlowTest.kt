package com.rndeveloper.paparcar.presentation.home

import kotlin.test.Test
import kotlin.test.assertEquals

class HomeFirstEntryFlowTest {
    private val pending = HomeEntryPromptInputs(
        isHomeTabActive = true, isBrowse = true,
        isLocationRequestPending = false, isWaitingForPermissionRoute = false,
        plusMomentAvailable = true, detectionPitchPending = true,
        firstParkingOfferPending = true, canAskAdMeasurementConsent = false,
    )

    @Test
    fun should_deferMeta_when_initialEditorHasJustBeenHandled() {
        val state = HomeState(firstHomeParkingFlowReady = true, firstHomeParkingFlowCompleted = true)
        assertEquals(false, state.canAskHomeAdConsent(isEligible = true, leftHomeAfterCompletion = false))
        assertEquals(true, state.canAskHomeAdConsent(isEligible = true, leftHomeAfterCompletion = true))
    }

    @Test
    fun should_allowMetaOnlyWithAnAdLane_when_returningAfterTheInitialVisit() {
        val state = HomeState(firstHomeParkingFlowReady = true, firstHomeParkingFlowCompletedOnArrival = true)
        assertEquals(true, state.canAskHomeAdConsent(isEligible = true, leftHomeAfterCompletion = false))
        assertEquals(false, state.canAskHomeAdConsent(isEligible = false, leftHomeAfterCompletion = false))
        assertEquals(false, state.copy(firstHomeParkingFlowReady = false).canAskHomeAdConsent(true, false))
    }

    @Test
    fun should_offerConfirmedGiftBeforeDetection_when_allCandidatesAreReady() {
        assertEquals(HomeEntryPrompt.PLUS, resolveHomeEntryPrompt(pending))
    }

    @Test
    fun should_continueWithoutWaitingForRevenueCat_when_giftIsUnavailable() {
        assertEquals(HomeEntryPrompt.DETECTION, resolveHomeEntryPrompt(pending.copy(plusMomentAvailable = false)))
    }

    @Test
    fun should_keepDetectionVisible_when_giftArrivesWhileUserAnswers() {
        assertEquals(HomeEntryPrompt.DETECTION, resolveHomeEntryPrompt(pending, HomeEntryPrompt.DETECTION))
        assertEquals(HomeEntryPrompt.PLUS, resolveHomeEntryPrompt(pending.copy(detectionPitchPending = false), HomeEntryPrompt.DETECTION))
    }

    @Test
    fun should_waitForBrowse_when_giftArrivesWhilePlacingPin() {
        assertEquals(HomeEntryPrompt.NONE, resolveHomeEntryPrompt(pending.copy(isBrowse = false)))
        assertEquals(HomeEntryPrompt.PLUS, resolveHomeEntryPrompt(pending.copy(firstParkingOfferPending = false)))
    }

    @Test
    fun should_offerParkingBeforeLaterConsent_when_initialPromptsAreHandled() {
        val inputs = pending.copy(plusMomentAvailable = false, detectionPitchPending = false, canAskAdMeasurementConsent = true)
        assertEquals(HomeEntryPrompt.FIRST_PARKING, resolveHomeEntryPrompt(inputs))
        assertEquals(HomeEntryPrompt.AD_MEASUREMENT, resolveHomeEntryPrompt(inputs.copy(firstParkingOfferPending = false)))
    }

    @Test
    fun should_keepConsentVisible_when_giftArrivesOnLaterVisit() {
        assertEquals(HomeEntryPrompt.AD_MEASUREMENT, resolveHomeEntryPrompt(pending.copy(canAskAdMeasurementConsent = true), HomeEntryPrompt.AD_MEASUREMENT))
    }

    @Test
    fun should_pauseAllAppPrompts_when_anotherSurfaceOwnsHome() {
        listOf(
            pending.copy(isLocationRequestPending = true),
            pending.copy(isWaitingForPermissionRoute = true),
            pending.copy(isHomeTabActive = false),
            pending.copy(isBrowse = false),
        ).forEach { assertEquals(HomeEntryPrompt.NONE, resolveHomeEntryPrompt(it, HomeEntryPrompt.PLUS)) }
    }

    @Test
    fun should_leaveBrowseAvailable_when_noCandidateIsEligible() {
        assertEquals(HomeEntryPrompt.NONE, resolveHomeEntryPrompt(pending.copy(
            plusMomentAvailable = false, detectionPitchPending = false, firstParkingOfferPending = false,
        )))
    }
}
