package com.rndeveloper.paparcar.presentation.home

import kotlin.test.Test
import kotlin.test.assertEquals

class HomeFirstEntryFlowTest {
    private val pending = HomeEntryPromptInputs(
        isHomeTabActive = true, isBrowse = true,
        isLocationRequestPending = false, isWaitingForPermissionRoute = false,
        plusMomentAvailable = true, detectionPitchPending = true,
        firstParkingOfferPending = true, refuelAskPending = true,
        canAskAdMeasurementConsent = false,
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
        assertEquals(HomeEntryPrompt.FIRST_PARKING, resolveHomeEntryPrompt(pending.copy(plusMomentAvailable = false)))
    }

    /** [ONB-A-WATCH-NEEDS-A-CAR-PARKED-FIRST-001] The detection pitch sells watching a car that is
     *  not marked yet unless a pin exists first — so the parking editor always outranks it. */
    @Test
    fun should_offerParkingBeforeDetection_when_bothAreEligible() {
        val inputs = pending.copy(plusMomentAvailable = false)
        assertEquals(HomeEntryPrompt.FIRST_PARKING, resolveHomeEntryPrompt(inputs))
        assertEquals(
            HomeEntryPrompt.DETECTION,
            resolveHomeEntryPrompt(inputs.copy(firstParkingOfferPending = false)),
        )
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
        val inputs = pending.copy(
            plusMomentAvailable = false, detectionPitchPending = false,
            refuelAskPending = false, canAskAdMeasurementConsent = true,
        )
        assertEquals(HomeEntryPrompt.FIRST_PARKING, resolveHomeEntryPrompt(inputs))
        assertEquals(HomeEntryPrompt.AD_MEASUREMENT, resolveHomeEntryPrompt(inputs.copy(firstParkingOfferPending = false)))
    }

    /** [DET-A-STOP-AT-A-PUMP-IS-NOT-A-PARKING-001] Una pregunta sobre algo que el usuario acaba de
     *  hacer va por delante del trámite del consentimiento, y por detrás de la primera visita. */
    @Test
    fun should_askAboutTheRefuelBeforeConsent_when_onboardingIsDone() {
        val inputs = pending.copy(
            plusMomentAvailable = false, detectionPitchPending = false,
            firstParkingOfferPending = false, canAskAdMeasurementConsent = true,
        )
        assertEquals(HomeEntryPrompt.REFUEL, resolveHomeEntryPrompt(inputs))
        assertEquals(HomeEntryPrompt.AD_MEASUREMENT, resolveHomeEntryPrompt(inputs.copy(refuelAskPending = false)))
    }

    /** La invitación de la primera visita no se adelanta por una parada en el surtidor. */
    @Test
    fun should_notCutInFrontOfOnboarding_when_aRefuelIsWaiting() {
        val inputs = pending.copy(plusMomentAvailable = false, detectionPitchPending = false)
        assertEquals(HomeEntryPrompt.FIRST_PARKING, resolveHomeEntryPrompt(inputs))
        assertEquals(
            HomeEntryPrompt.REFUEL,
            resolveHomeEntryPrompt(inputs.copy(firstParkingOfferPending = false)),
        )
    }

    /** Contestar cierra la pregunta: el aviso no sobrevive a su propia respuesta aunque fuera el
     *  prompt en curso. */
    @Test
    fun should_dropTheRefuelAsk_when_itHasBeenAnswered() {
        val answered = pending.copy(
            plusMomentAvailable = false, detectionPitchPending = false,
            firstParkingOfferPending = false, refuelAskPending = false,
        )
        assertEquals(HomeEntryPrompt.NONE, resolveHomeEntryPrompt(answered, HomeEntryPrompt.REFUEL))
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
            plusMomentAvailable = false, detectionPitchPending = false,
            firstParkingOfferPending = false, refuelAskPending = false,
        )))
    }
}
