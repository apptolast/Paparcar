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
import com.rndeveloper.paparcar.ui.icons.PaparcarIcons
import com.rndeveloper.paparcar.ui.illustrations.AutoDetectionIllustration
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_det_pitch_body
import paparcar.composeapp.generated.resources.home_det_pitch_title
import paparcar.composeapp.generated.resources.home_det_producer_cta
import paparcar.composeapp.generated.resources.permissions_btn_not_now
import paparcar.composeapp.generated.resources.refuel_answer_no
import paparcar.composeapp.generated.resources.refuel_answer_yes
import paparcar.composeapp.generated.resources.refuel_ask_body
import paparcar.composeapp.generated.resources.refuel_ask_body_at
import paparcar.composeapp.generated.resources.refuel_ask_title

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
        // [DET-A-STOP-AT-A-PUMP-IS-NOT-A-PARKING-001] Freshness was already applied upstream; what
        // is left here is whether the user has waved this one away during this run.
        refuelAskPending = state.pendingRefuelStop
            ?.let { it.id !in state.refuelAsksDismissed } == true,
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
            // La invitación a la estrella del producto abre con la ESCENA que describe —tu coche
            // en su plaza, vigilado—, no con el glifo de un sensor. Misma decisión y misma
            // proyección que la bienvenida de Fundadores, que sale justo antes en esta cadena.
            // `icon` se queda como respaldo SEMÁNTICO, igual que en la bienvenida del regalo:
            // la ilustración es lo que se dibuja, el glifo es lo que la superficie significa.
            icon = Icons.Rounded.Sensors,
            heroIllustration = { AutoDetectionIllustration() },
            title = stringResource(Res.string.home_det_pitch_title),
            body = stringResource(Res.string.home_det_pitch_body),
            primaryLabel = stringResource(Res.string.home_det_producer_cta),
            onPrimary = { onIntent(HomeIntent.AcceptDetectionPitch) },
            cancelLabel = stringResource(Res.string.permissions_btn_not_now),
        )
        HomeEntryPrompt.AD_MEASUREMENT ->
            AdMeasurementConsentDialog(onAnswer = onAnswerAdMeasurementConsent)
        // [DET-A-STOP-AT-A-PUMP-IS-NOT-A-PARKING-001] Two answers and no third button: the question
        // is one the user always knows the answer to, so an «ahora no» would only be a way of
        // asking it again tomorrow. Backing out still works and writes nothing.
        HomeEntryPrompt.REFUEL -> state.pendingRefuelStop?.let { stop ->
            PapAlertDialog(
                onDismiss = { onIntent(HomeIntent.DismissRefuelStopAsk(stop.id)) },
                icon = PaparcarIcons.Fuel,
                title = stringResource(Res.string.refuel_ask_title),
                body = stop.placeInfo?.name
                    ?.let { stringResource(Res.string.refuel_ask_body_at, it) }
                    ?: stringResource(Res.string.refuel_ask_body),
                primaryLabel = stringResource(Res.string.refuel_answer_yes),
                onPrimary = { onIntent(HomeIntent.AnswerRefuelStop(stop.id, refuelled = true)) },
                secondaryLabel = stringResource(Res.string.refuel_answer_no),
                onSecondary = { onIntent(HomeIntent.AnswerRefuelStop(stop.id, refuelled = false)) },
            )
        }
        HomeEntryPrompt.FIRST_PARKING, HomeEntryPrompt.NONE -> Unit
    }
}
