package com.rndeveloper.paparcar.presentation.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.rndeveloper.paparcar.domain.entitlement.PlusMoment
import com.rndeveloper.paparcar.presentation.app.AdMeasurementConsentDialog
import com.rndeveloper.paparcar.presentation.app.PlusMomentDialog
import com.rndeveloper.paparcar.ui.components.PapAlertDialog
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_det_pitch_body
import paparcar.composeapp.generated.resources.home_det_pitch_title
import paparcar.composeapp.generated.resources.home_det_producer_cta
import paparcar.composeapp.generated.resources.permissions_btn_not_now

/** One owner for the Home entry surfaces. Eligibility stays with each feature's existing owner. */
@Composable
fun HomeEntryPromptHost(
    accountId: String,
    state: HomeState,
    isHomeActive: Boolean,
    plusMoment: PlusMoment?,
    askAdMeasurementConsent: Boolean,
    onIntent: (HomeIntent) -> Unit,
    onDismissPlusMoment: (PlusMoment) -> Unit,
    onSeePlusPlans: (PlusMoment) -> Unit,
    onAnswerAdMeasurementConsent: (Boolean) -> Unit,
) {
    // A fresh composition starts inactive before its lifecycle observer resumes. That is not a visit away.
    var observedActiveHome by remember(accountId) { mutableStateOf(false) }
    var leftHomeAfterCompletion by rememberSaveable(accountId) { mutableStateOf(false) }
    var currentPrompt by rememberSaveable(accountId) { mutableStateOf(HomeEntryPrompt.NONE) }

    LaunchedEffect(isHomeActive, state.firstHomeParkingFlowCompleted) {
        if (isHomeActive) {
            observedActiveHome = true
        } else if (observedActiveHome && state.firstHomeParkingFlowCompleted) {
            leftHomeAfterCompletion = true
        }
    }
    val firstHomeDataReady = state.firstHomeParkingFlowReady && state.firstStepsLoaded &&
        state.vehiclesLoaded && state.parkingSessionsLoaded
    LaunchedEffect(firstHomeDataReady, state.shouldOfferInitialParkingEditor, state.mode) {
        // Existing accounts and accounts without a car skip only the automatic editor offer.
        if (firstHomeDataReady && !state.firstHomeParkingFlowCompleted &&
            !state.initialHomeParkingEditorActive && !state.shouldOfferInitialParkingEditor &&
            state.mode is HomeMode.Browse
        ) onIntent(HomeIntent.CompleteFirstHomeParkingFlow)
    }
    val inputs = HomeEntryPromptInputs(
        isHomeTabActive = isHomeActive,
        isBrowse = state.mode is HomeMode.Browse && state.selection == null &&
            !state.isSearchActive && state.releaseDialogSessionId == null,
        isLocationRequestPending = state.firstLocationRequestPending || state.locationAnswerInFlight,
        isWaitingForPermissionRoute = state.firstHomeWaitingForPermissionRoute,
        plusMomentAvailable = plusMoment != null,
        detectionPitchPending = state.showDetectionPitch,
        firstParkingOfferPending = state.shouldOfferInitialParkingEditor,
        canAskAdMeasurementConsent = state.canAskHomeAdConsent(
            askAdMeasurementConsent, leftHomeAfterCompletion,
        ),
    )
    val prompt = resolveHomeEntryPrompt(inputs, currentPrompt)
    SideEffect { currentPrompt = prompt }

    LaunchedEffect(prompt) {
        if (prompt == HomeEntryPrompt.FIRST_PARKING) onIntent(HomeIntent.StartFirstHomeParkingOffer)
    }
    when (prompt) {
        HomeEntryPrompt.PLUS -> plusMoment?.let { moment ->
            PlusMomentDialog(
                moment = moment,
                onDismiss = { onDismissPlusMoment(moment) },
                onSeePlans = { onSeePlusPlans(moment) },
            )
        }
        HomeEntryPrompt.DETECTION -> PapAlertDialog(
            onDismiss = { onIntent(HomeIntent.DismissDetectionPitch) },
            icon = Icons.Rounded.Sensors,
            title = stringResource(Res.string.home_det_pitch_title),
            body = stringResource(Res.string.home_det_pitch_body),
            primaryLabel = stringResource(Res.string.home_det_producer_cta),
            onPrimary = { onIntent(HomeIntent.AcceptDetectionPitch) },
            cancelLabel = stringResource(Res.string.permissions_btn_not_now),
        )
        HomeEntryPrompt.AD_MEASUREMENT ->
            AdMeasurementConsentDialog(onAnswer = onAnswerAdMeasurementConsent)
        HomeEntryPrompt.FIRST_PARKING, HomeEntryPrompt.NONE -> Unit
    }
}
