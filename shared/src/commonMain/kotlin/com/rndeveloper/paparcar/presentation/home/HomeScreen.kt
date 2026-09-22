package com.rndeveloper.paparcar.presentation.home

import androidx.compose.runtime.withFrameNanos
import com.rndeveloper.paparcar.presentation.home.model.FirstStepGuide
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rndeveloper.paparcar.presentation.home.model.homeHasNoLocation
import com.rndeveloper.paparcar.presentation.permissions.rememberRequestFineLocationAction
import com.rndeveloper.paparcar.presentation.util.MapForegroundEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.rndeveloper.paparcar.domain.entitlement.PaywallReason
import com.rndeveloper.paparcar.domain.error.PaparcarError
import com.rndeveloper.paparcar.domain.model.SpotVoteOutcome
import com.rndeveloper.paparcar.presentation.home.model.isDetectionStopped
import com.rndeveloper.paparcar.domain.model.DrivingPuck
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.ParkingReleaseReason
import com.rndeveloper.paparcar.domain.spot.SpotReleasePolicy
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.displayName
import com.rndeveloper.paparcar.presentation.home.sections.header.HomeHeaderSection
import com.rndeveloper.paparcar.presentation.home.sections.map.HomeMapFabsLayer
import com.rndeveloper.paparcar.presentation.home.sections.map.HomeMapGpsLane
import com.rndeveloper.paparcar.presentation.home.sections.map.HomeMapSection
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.rememberHomeSheetRowsScroll
import com.rndeveloper.paparcar.presentation.home.sections.sheet.HomeBottomSheet
import com.rndeveloper.paparcar.presentation.home.sections.sheet.HomeSheetAction
import com.rndeveloper.paparcar.presentation.home.sections.sheet.HomeSheetFrame
import com.rndeveloper.paparcar.presentation.home.sections.sheet.HomeSheetSnap
import com.rndeveloper.paparcar.presentation.home.sections.sheet.SheetTransitionEffects
import com.rndeveloper.paparcar.presentation.home.sections.sheet.rememberSheetMotion
import com.rndeveloper.paparcar.presentation.home.sections.sheet.rememberSheetPositioning
import com.rndeveloper.paparcar.presentation.home.model.HomeNearbyLens
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.NEARBY_LENS_KEY
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.HomeReleaseDialog
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.peek.LaneHeaderLatchInputs
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.peek.laneKeepsHeaderOnCollapse
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.peek.nextLaneHeaderLatch
import com.rndeveloper.paparcar.presentation.util.rememberOpenExternalNavigation
import com.rndeveloper.paparcar.presentation.util.zoneIconFor
import com.rndeveloper.paparcar.presentation.map.ZoomIntent
import com.rndeveloper.paparcar.presentation.map.zoomForZoneRadius
import com.rndeveloper.paparcar.ui.components.CenterPinKind
import com.rndeveloper.paparcar.ui.components.LocalMapInteracting
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sensors
import com.rndeveloper.paparcar.ui.components.PapAlertDialog
import com.rndeveloper.paparcar.ui.components.PapSpotlight
import com.rndeveloper.paparcar.domain.onboarding.FirstStep
import com.rndeveloper.paparcar.ui.theme.PapMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.error_gps_unavailable
import paparcar.composeapp.generated.resources.error_load_session
import paparcar.composeapp.generated.resources.error_load_spots
import paparcar.composeapp.generated.resources.error_parking_save_failed
import paparcar.composeapp.generated.resources.error_release_parking
import paparcar.composeapp.generated.resources.error_report_too_far
import paparcar.composeapp.generated.resources.error_search_failed
import paparcar.composeapp.generated.resources.first_steps_spotlight_caption
import paparcar.composeapp.generated.resources.location_approximate_near
import paparcar.composeapp.generated.resources.error_unknown
import paparcar.composeapp.generated.resources.error_watch_resume_failed
import paparcar.composeapp.generated.resources.home_det_enabled_confirm
import paparcar.composeapp.generated.resources.home_det_pitch_body
import paparcar.composeapp.generated.resources.home_det_pitch_title
import paparcar.composeapp.generated.resources.home_det_producer_cta
import paparcar.composeapp.generated.resources.home_det_stopped_action
import paparcar.composeapp.generated.resources.home_det_stopped_msg
import paparcar.composeapp.generated.resources.home_det_trip_stopped_msg
import paparcar.composeapp.generated.resources.permissions_btn_not_now
import paparcar.composeapp.generated.resources.home_release_dialog_detection_active
import paparcar.composeapp.generated.resources.home_release_dialog_detection_inactive
import paparcar.composeapp.generated.resources.home_release_dialog_detection_unavailable
import paparcar.composeapp.generated.resources.home_spot_reported
import paparcar.composeapp.generated.resources.home_spot_vote_retracted
import paparcar.composeapp.generated.resources.home_spot_vote_refreshed
import paparcar.composeapp.generated.resources.home_test_spot_sent
import paparcar.composeapp.generated.resources.home_zone_saved_message
import kotlin.time.Duration.Companion.milliseconds
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.nearbyLensIndexOrNull

// Initial/fallback peek size used before the handle has been measured.
// After first layout, peekHeightPx tracks the handle's real measured height
// so the sheet's collapsed edge sits exactly at the bottom-nav divider.
private val SheetPeekHeightInitial = 88.dp

// Glass stays "interacting" for this long after the last onCameraMove tick.
// Must be ≥ PaparcarMapView's CAMERA_MOVING_DEBOUNCE_MS (280) so the glass fade-out
// does not start while the map is still settling out of a fling.
private const val MAP_INTERACTION_IDLE_DELAY_MS = 320L

// Guard window for programmatic camera animations. Must cover the full
// PaparcarMapView CAMERA_ANIM_MS (700) + idle debounce so no synthetic
// onCameraMove frame leaks through and flips the glass on while the map is
// animating to a tapped spot / "my location" / bounds fit.
private const val PROGRAMMATIC_MOVE_GUARD_MS = 1100L

// Zoom per camera PURPOSE (see CameraFrame) — the screen owns these numbers, the VM only says what
// the move is for.
//   · Navigate: a SUELO, no un destino. Aterrizar en un sitio con su contexto de calle si venías
//     lejos, y no tocar el zoom si ya estabas más cerca. [MAP-A-CAMERA-MOVE-MUST-NOT-COST-A-PINCH-001]
//   · ZoneEditing: ya no tiene número fijo. El círculo hay que verlo entero, pero eso depende de su
//     RADIO — `zoomForZoneRadius` —, no de un 14 que mandaba a 4 km para dimensionar un garaje.
//     [UI-ZONE-MANAGE-001]
private const val NAVIGATE_ZOOM = 15f

// [FIRST-STEPS-SHOW-WHERE-NOT-OPEN-001]
/** Lo que tarda la hoja en bajar después de que el paso anuncia que va a señalar arriba. Un respiro,
 *  no una espera: suficiente para que el ojo lea la línea y entienda la causa del movimiento.
 *  [ONB-A-GUIDE-ANSWERS-UNTIL-YOU-ACT-001] */
private const val GUIDE_ANNOUNCE_MS = 900L

/** Which tier the permissions screen opens focused on. Two callers since the location warning moved
 *  from the peek badge to the map pill, and they have to land on the SAME place.
 *  [UI-NO-LOCATION-WEARS-THE-ACCURACY-PILL-001] [DET-TOGGLE-001] */
private const val CORE_PERMISSIONS_FOCUS = "core"

// Sheet snap geometry, transition effects and the snap spec live in
// sections/sheet/HomeSheetPositioning.kt. [HOME-ATOMIZE-001 F2]

// Map's bottom edge extends past the sheet top so the rounded top corners
// sit on opaque map tiles instead of exposing the dark background behind
// the curves.
private val MAP_BOTTOM_BLEED = 20.dp

// FAB inset above the sheet top edge.
private val FAB_ABOVE_SHEET_GAP = 12.dp

// ─────────────────────────────────────────────────────────────────────────────
// Root
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    navProgressState: MutableFloatState = remember { mutableFloatStateOf(1f) },
    bottomPadding: Dp = 0.dp,
    // [DET-READY-001f] Detection-banner CTAs. Navigate to the existing permission flow
    // (reuses its disclosure + escalation) and to vehicle registration respectively.
    onActivateDetection: (focus: String) -> Unit = {},
    onAddVehicle: () -> Unit = {},
    /** Leave for the Vehicles tab, opened on this car's ficha. [UI-A-PARKED-CARD-OFFERS-THE-WAY-ACROSS-001] */
    onOpenVehicle: (vehicleId: String) -> Unit = {},
    /** Arrive AT a parking: the ficha's "Ver en mapa" on a session that is still live. Home opens it
     *  exactly as tapping its marker does. Null when nobody asked.
     *  [UI-A-PARKED-CARD-OFFERS-THE-WAY-ACROSS-001] */
    focusParkingId: String? = null,
    /** Called once the request above has been honoured, so a rotation does not replay it. */
    onFocusParkingConsumed: () -> Unit = {},
    /** Deep link into the car-Bluetooth screen for a vehicle — the fortify step's Bluetooth face.
     *  [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001] */
    onLinkVehicleBluetooth: (vehicleId: String) -> Unit = {},
    /** Open a first step's explanation on its own screen.
     *  [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001] */
    onOpenStepExplainer: (FirstStep) -> Unit = {},
    /** A Plus gate tapped by a free user — open the Paparcar Plus paywall. [MONEY-001] */
    onOpenPaywall: (PaywallReason) -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Live trip render data collected SEPARATELY from [state] — it changes at the GPS fix rate and is
    // NOT in HomeState, so this screen (and the expensive map) don't recompose per fix. We hold the
    // State objects and derive per-field States; nothing here reads `.value` in the main body, so the
    // fix-rate updates flow untouched to the map's isolated scopes. [DRIVE-PUCK-NATIVE-001]
    val tripState = viewModel.tripRender.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // First-entry location ask, launched OVER the map — no permissions screen in front of it.
    // [ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001]
    val requestFineLocation = rememberRequestFineLocationAction()

    // Pre-resolve strings in Composable scope — cannot use stringResource inside LaunchedEffect.
    val msgErrorUnknown = stringResource(Res.string.error_unknown)
    val msgErrorLoadSpots = stringResource(Res.string.error_load_spots)
    val msgErrorLoadSession = stringResource(Res.string.error_load_session)
    val msgErrorReleaseParking = stringResource(Res.string.error_release_parking)
    val msgErrorGpsUnavailable = stringResource(Res.string.error_gps_unavailable)
    val msgErrorParkingSaveFailed = stringResource(Res.string.error_parking_save_failed)
    val msgErrorSearchFailed = stringResource(Res.string.error_search_failed)
    val msgErrorWatchResumeFailed = stringResource(Res.string.error_watch_resume_failed)
    val msgErrorReportTooFar = stringResource(Res.string.error_report_too_far)
    val msgSpotReported = stringResource(Res.string.home_spot_reported)
    val msgTestSpotSent = stringResource(Res.string.home_test_spot_sent)
    // Each vote says what it DID. The old single "Thanks for the update!" was true and useless —
    // it was the confirmation of a button that changed nothing.
    // [SPOT-COMMUNITY-VOTES-NEED-A-CONSEQUENCE-001]
    val msgSpotVoteRetracted = stringResource(Res.string.home_spot_vote_retracted)
    val msgSpotVoteRefreshed = stringResource(Res.string.home_spot_vote_refreshed)
    val msgZoneSaved = stringResource(Res.string.home_zone_saved_message)
    val msgDetectionEnabled = stringResource(Res.string.home_det_enabled_confirm)
    val msgDetectionStopped = stringResource(Res.string.home_det_stopped_msg)
    val msgTripDetectionStopped = stringResource(Res.string.home_det_trip_stopped_msg)
    val msgDetectionStoppedAction = stringResource(Res.string.home_det_stopped_action)

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is HomeEffect.ShowError -> {
                    val msg = when (effect.error) {
                        is PaparcarError.Location.ProviderDisabled -> msgErrorGpsUnavailable
                        is PaparcarError.Location.SearchFailed -> msgErrorSearchFailed
                        is PaparcarError.Database.WriteError -> msgErrorReleaseParking
                        is PaparcarError.Parking.ReleaseFailed -> msgErrorReleaseParking
                        is PaparcarError.Network.Unknown -> msgErrorLoadSpots
                        is PaparcarError.Database.Unknown -> msgErrorLoadSession
                        is PaparcarError.Parking.SaveFailed -> msgErrorParkingSaveFailed
                        // El peek ya lo dice y apaga su botón: esto sólo salta si el fix se movió
                        // entre que la cámara se posó y el dedo bajó.
                        // [SPOT-A-REPORT-YOU-CANNOT-SEE-IS-NOT-A-REPORT-001]
                        is PaparcarError.Spot.TooFarToReport -> msgErrorReportTooFar
                        // [DET-WATCH-REACTIVATE-001] "Reactivate" could not bring the watcher back.
                        is PaparcarError.Detection.WatchResumeFailed -> msgErrorWatchResumeFailed
                        else -> msgErrorUnknown
                    }
                    snackbarHostState.showSnackbar(msg)
                }

                HomeEffect.SpotReported -> snackbarHostState.showSnackbar(msgSpotReported)
                HomeEffect.TestSpotSent -> snackbarHostState.showSnackbar(msgTestSpotSent)
                is HomeEffect.SpotSignalSent -> when (effect.outcome) {
                    SpotVoteOutcome.RETRACT -> snackbarHostState.showSnackbar(msgSpotVoteRetracted)
                    SpotVoteOutcome.REFRESH -> snackbarHostState.showSnackbar(msgSpotVoteRefreshed)
                    // The buttons are not offered from too far away, so this branch is only
                    // reachable if a vote raced the user walking away. Saying nothing is right:
                    // nothing was written.
                    SpotVoteOutcome.IGNORED_TOO_FAR -> Unit
                }
                HomeEffect.ZoneSaved -> snackbarHostState.showSnackbar(msgZoneSaved)
                is HomeEffect.NavigateToPaywall -> onOpenPaywall(effect.reason)
                HomeEffect.DetectionEnabled -> snackbarHostState.showSnackbar(msgDetectionEnabled)
                // [DET-STOP-BUTTON-001] The user stopped THIS trip — plain confirmation, no action:
                // there is nothing to re-enable (the feature is still on) and the copy already says
                // how to come back.
                HomeEffect.TripDetectionStopped -> snackbarHostState.showSnackbar(msgTripDetectionStopped)
                // Detection dropped to a stopped state → snackbar with a one-tap re-activation that
                // reuses the same EnableAutoDetection flow as the banner. [DET-TOGGLE-002]
                HomeEffect.DetectionStopped -> {
                    val result = snackbarHostState.showSnackbar(
                        message = msgDetectionStopped,
                        actionLabel = msgDetectionStoppedAction,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.handleIntent(HomeIntent.EnableAutoDetection)
                    }
                }
                // Flag enabled but permissions still needed → open the permissions screen so the one
                // "activate detection" tap brings detection fully online. [DET-TOGGLE-001]
                is HomeEffect.OpenDetectionPermissions -> onActivateDetection(effect.focus)
                // [DET-WATCH-HONEST-001] Launch the OS battery-optimization exemption dialog (expect/actual).
                HomeEffect.RequestBatteryOptimizationExemption -> requestIgnoreBatteryOptimizations()
                HomeEffect.RequestLocationPermission -> requestFineLocation()
                // MoveCameraTo needs the HomeUiController which lives inside
                // HomeContent; a dedicated collector down there handles it.
                is HomeEffect.MoveCameraTo -> Unit
            }
        }
    }

    // Scope the high-accuracy user-location request to when this screen is actually on-screen: under
    // Compose Navigation the lifecycle owner is Home's back-stack entry, so this is RESUMED only while
    // Home is the current destination AND the app is foreground. [UI-LOC-FOREGROUND-001]
    // Declared AFTER the effect collector on purpose: SetMapForeground(true) can trigger the
    // first-run location ask, and a replay-less SharedFlow drops effects emitted before the
    // collector above subscribes. [ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001]
    MapForegroundEffect { active -> viewModel.handleIntent(HomeIntent.SetMapForeground(active)) }

    // Stable function reference — viewModel instance never changes so this
    // is safe to remember once. Prevents HomeContent from getting a new
    // lambda on every state emission, which would defeat skipping in children.
    val onIntent: (HomeIntent) -> Unit = remember(viewModel) { viewModel::handleIntent }

    HomeContent(
        state = state,
        tripState = tripState,
        onIntent = onIntent,
        effects = viewModel.effect,
        snackbarHostState = snackbarHostState,
        navProgressState = navProgressState,
        bottomPadding = bottomPadding,
        onActivateDetection = onActivateDetection,
        onAddVehicle = onAddVehicle,
        onOpenVehicle = onOpenVehicle,
        focusParkingId = focusParkingId,
        onFocusParkingConsumed = onFocusParkingConsumed,
        onLinkVehicleBluetooth = onLinkVehicleBluetooth,
        onOpenStepExplainer = onOpenStepExplainer,
        // El sitio que el usuario eligió, leído del ViewModel porque es lo único que sobrevive a
        // salir de Home. [HOME-WHAT-IS-REMEMBERED-IS-THE-PLACE-YOU-CHOSE-001]
        chosenView = viewModel::chosenView,
    )

}

// ─────────────────────────────────────────────────────────────────────────────
// Content — orchestrates state, math, and the four section composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HomeContent(
    state: HomeState,
    tripState: State<TripUpdate>,
    onIntent: (HomeIntent) -> Unit,
    /** Dónde estaba mirando el usuario cuando dejó Home, o null si nunca posó la cámara él.
     *  [HOME-WHAT-IS-REMEMBERED-IS-THE-PLACE-YOU-CHOSE-001] */
    chosenView: () -> Pair<Double, Double>?,
    effects: SharedFlow<HomeEffect>,
    snackbarHostState: SnackbarHostState,
    navProgressState: MutableFloatState,
    bottomPadding: Dp,
    onActivateDetection: (focus: String) -> Unit,
    onAddVehicle: () -> Unit,
    onOpenVehicle: (vehicleId: String) -> Unit,
    focusParkingId: String?,
    onFocusParkingConsumed: () -> Unit,
    onLinkVehicleBluetooth: (vehicleId: String) -> Unit,
    onOpenStepExplainer: (FirstStep) -> Unit,
) {
    val uiController = rememberHomeUiController()
    // Per-field trip States derived from [tripState]. Held as State and read only in isolated scopes
    // (map, snapshotFlow effects, one-shot lambdas), so a fix never recomposes this content. [DRIVE-PUCK-NATIVE-001]
    val trip = remember(tripState) { TripRenderStates(tripState) }
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    // Platform-aware launcher for external navigation (Google Maps on Android,
    // no-op on iOS for now). Captured here so the peek handle's primary action
    // can invoke it directly without round-tripping through the VM. [PEEK-ACTIONS-001]
    val openExternalNav = rememberOpenExternalNavigation()

    // Stabilized bottom padding for the sheet's inner LazyColumn. The root
    // Scaffold shrinks its content padding to 0 while the global nav slides
    // out during a drag — if the sheet consumed that raw value, its scroll
    // area would visibly shift underfoot. Latch the last non-zero padding
    // so the sheet keeps reserving space for the nav while it animates away.
    //
    // Mutation happens in a SideEffect rather than inline — writing to a
    // mutableState from the composition phase is a Compose anti-pattern and
    // can mis-fire invalidation in future runtime versions.
    var lastKnownNavHeight by remember { mutableStateOf(0.dp) }
    SideEffect {
        if (bottomPadding > 0.dp && bottomPadding != lastKnownNavHeight) {
            lastKnownNavHeight = bottomPadding
        }
    }
    val stableBottomPadding = if (bottomPadding > 0.dp) bottomPadding else lastKnownNavHeight

    // Glass-on-drag tracking + programmatic-move guard — see [MapInteractionTracker].
    // ⛔ Antes de nada y UNA sola vez: volver a Home recordaba el carril —vive en el ViewModel— y
    // olvidaba el sitio, porque el controlador nace nuevo. La lista hablaba de una ciudad y la
    // cámara se iba a tu ubicación, que es la misma discrepancia que
    // [HOME-A-LANE-THAT-CANNOT-ANSWER-HERE-SHOWS-NO-ROWS-001] tapó para el otro sentido: allí la
    // lista dejó de mentir sobre dónde miras, y aquí la cámara deja de olvidar dónde mirabas.
    // [HOME-WHAT-IS-REMEMBERED-IS-THE-PLACE-YOU-CHOSE-001]
    LaunchedEffect(Unit) {
        val (lat, lon) = chosenView() ?: return@LaunchedEffect
        uiController.restoreChosenView(lat, lon)
    }
    val mapInteraction = rememberMapInteractionTracker(uiController)

    // Per-section slices — pure projections built ONCE per state emission. Each section
    // composable receives only its slice, so unrelated state changes stop recomposing it.
    // HomeContent itself keeps the full state (it is the orchestrator). [HOME-ATOMIZE-001 F1]
    val headerSlice = remember(state) { state.toHeaderSlice() }
    val fabsSlice = remember(state) { state.toFabsSlice() }
    // ⛔ TODAS las porciones de los complementos se derivan del estado YA filtrado por «¿esta
    // respuesta va de donde estoy mirando?». Una sola vez y aquí, que es donde vive la cámara:
    // así el mapa, el peek, la lista y la selección no pueden discrepar, y una fila de otra ciudad
    // no queda tocable mientras llega la de ésta.
    // [HOME-A-LANE-THAT-CANNOT-ANSWER-HERE-SHOWS-NO-ROWS-001]
    val shown = remember(state, uiController.cameraLat, uiController.cameraLon) {
        state.laneAnsweringHere(uiController.cameraLat, uiController.cameraLon)
    }
    val mapSlice = remember(shown) { shown.toMapSlice() }
    val peekSlice = remember(shown) { shown.toPeekSlice() }
    val browseSlice = remember(shown) { shown.toBrowseListSlice() }

    val isParkingSelected = state.isParkingSelected
    // [UI-PROVISIONAL-SPOT-IS-NOT-ITS-SESSION-001] The kind is carried, not inferred from which
    // list happens to contain the id.
    val selectedSpotId = (state.selection as? HomeSelection.Spot)?.id
    val selectedFuelStationId = (state.selection as? HomeSelection.FuelStation)?.id
    val selectedPublicParkingId = (state.selection as? HomeSelection.PublicParking)?.id
    // The session the release dialog acts on — set from the peek that opened it, so the release
    // targets THAT card, never a ranked fallback. [VEH-ACTIVE-FENCE-001] Owned by the VM, which
    // closes it in the same update that ends the release; the screen no longer infers "done" from a
    // true→false flip it could miss when the release is faster than a frame.
    // [PARK-RELEASE-THE-SCREEN-NEVER-SAW-001]
    val releaseTargetSessionId = state.releaseDialogSessionId
    val lazyListState = rememberLazyListState()
    // [FIRST-STEPS-SHOW-WHERE-NOT-OPEN-001] El control al que apunta un paso del checklist.
    //
    // ⛔ **Sin reloj** [ONB-A-GUIDE-ANSWERS-UNTIL-YOU-ACT-001]. Duraba 6 s fijos y el reporte fue
    // que «no capta la atención»: contestar «¿dónde está?» durante seis segundos es no contestar.
    // No puede quedarse encendido para siempre porque **sólo se enciende cuando el usuario lo
    // PIDE** desde el checklist, y se apaga en cuanto actúa — al usar el control, al completarse el
    // paso, o al salir de Browse (se fue a otra cosa).
    var firstStepGuide by remember { mutableStateOf<FirstStepGuide?>(null) }
    LaunchedEffect(state.mode) {
        if (state.mode !is HomeMode.Browse) firstStepGuide = null
    }
    // El paso al que señalábamos ha dejado de ser la pregunta: lo completó, o lo aplazó con «ahora
    // no». UNA condición para los dos, porque la pregunta es la misma —«¿sigue siendo esto lo que
    // te pido?»— y separarlas dejaría el aplazado señalado, que es discutirle al usuario un «no»
    // que acaba de dar. [ONB-A-GUIDE-ANSWERS-UNTIL-YOU-ACT-001]
    val guidedStepStillAsked =
        firstStepGuide != FirstStepGuide.ADD_ZONE_CHIP || state.firstSteps.current == FirstStep.PRIVATE_ZONE
    LaunchedEffect(guidedStepStillAsked) {
        if (!guidedStepStillAsked) firstStepGuide = null
    }
    // Hoisted with the list's own state: both must outlive the list while a detail sheet hides it.
    // [HOME-BROWSE-SURVIVES-THE-DETAIL-SHEET-001]
    val sheetRowsScroll = rememberHomeSheetRowsScroll()

    // Initial focus, parking re-frame, driver-follow and MoveCameraTo effects —
    // see [HomeCameraEffects].
    HomeCameraEffects(
        uiController = uiController,
        state = state,
        drivingPuck = trip.puck,
        effects = effects,
    )

    CompositionLocalProvider(LocalMapInteracting provides mapInteraction.isInteracting) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0),
            containerColor = Color.Transparent,
        ) { scaffoldPadding ->

            // Resolve the release target's vehicle for the consequence copy (name + active state).
            val releaseSession = releaseTargetSessionId
                ?.let { id -> state.activeSessions.firstOrNull { it.id == id } }
            val releaseVehicle = releaseSession
                ?.vehicleId
                ?.let { vid -> state.vehicles.firstOrNull { it.id == vid } }
            // [SPOT-A-PRIVATE-ZONE-DECIDES-THE-PUBLICATION-NOT-A-BUTTON-001] The SAME predicate the
            // publish gate will run, over the SAME live zone list — so the dialog cannot promise a
            // plaza the policy is about to withhold, nor vice versa. Not read off the session's
            // `privateZoneId`, which was sealed when the car arrived.
            val releasePrivateZoneName = releaseSession?.let { session ->
                SpotReleasePolicy.privateZoneAt(session.location, state.zones.filter { it.isPrivate })?.name
            }
            HomeReleaseDialogHost(
                visible = releaseTargetSessionId != null,
                releasingReason = state.releasingReason,
                sessionId = releaseTargetSessionId,
                vehicleName = releaseVehicle?.displayName(fallback = "")?.takeIf { it.isNotBlank() },
                vehicleIsActive = releaseVehicle?.isActive != false,
                // Off/blocked detection can't honour the "we'll watch your next parking" promise —
                // the note says so honestly; the story surface in Home asks for the fix. [UX-PARKED-STATE-001]
                detectionAvailable = !state.detectionUiState.isDetectionStopped,
                privateZoneName = releasePrivateZoneName,
                onIntent = onIntent,
                onDismiss = { onIntent(HomeIntent.DismissReleaseDialog) },
            )

            // One-time pitch of the star feature, fired right after the first-entry location grant
            // on the map. Not the old guilt dialog: declining is one tap and never comes back — the
            // banner and the checklist keep owning the ask. [ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001]
            if (state.showDetectionPitch) {
                PapAlertDialog(
                    onDismiss = { onIntent(HomeIntent.DismissDetectionPitch) },
                    icon = Icons.Rounded.Sensors,
                    title = stringResource(Res.string.home_det_pitch_title),
                    body = stringResource(Res.string.home_det_pitch_body),
                    primaryLabel = stringResource(Res.string.home_det_producer_cta),
                    onPrimary = { onIntent(HomeIntent.AcceptDetectionPitch) },
                    cancelLabel = stringResource(Res.string.permissions_btn_not_now),
                )
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding),
            ) {
                // Home renders full-screen — the root NavHost intentionally does
                // NOT apply scaffoldPadding for this route (only non-Home routes
                // pad themselves). So BoxWithConstraints spans the entire window
                // including the AppBottomNavigation slot, which lets the sheet
                // extend down and fill the space the nav leaves behind as it
                // fades away during a drag (no black gap).
                //
                // rawContainerHeightPx = full screen height.
                // containerHeightPx   = screen − active bar height. Used ONLY for snap
                //                       points so those stay stable while bars animate.
                val rawContainerHeightPx = constraints.maxHeight.toFloat()
                val navHeightPx = with(density) { stableBottomPadding.toPx() }
                val containerHeightPx = (rawContainerHeightPx - navHeightPx).coerceAtLeast(0f)

                // Peek height tracks the HomePeekHandle's real measured height so the
                // sheet's collapsed top edge sits flush with whichever bar is visible.
                // Bootstrapped from SheetPeekHeightInitial; zero-height readings are
                // filtered to avoid a layout-cycle collapse.
                var peekHeightPx by remember {
                    mutableFloatStateOf(with(density) { SheetPeekHeightInitial.toPx() })
                }

                // The five snap points + chrome threshold, derived in one place —
                // see HomeSheetPositioning.kt for the geometry. [HOME-ATOMIZE-001 F2]
                val isPureBrowsePeek = state.mode is HomeMode.Browse && !isParkingSelected &&
                    selectedSpotId == null && selectedFuelStationId == null &&
                    selectedPublicParkingId == null
                val positioning = rememberSheetPositioning(
                    containerHeightPx = containerHeightPx,
                    peekHeightPx = peekHeightPx,
                    lazyListState = lazyListState,
                    isPureBrowsePeek = isPureBrowsePeek,
                    // Pin modes / any selected pin: the peek handle owns the whole surface, so the
                    // sheet must not expand above peek. [SHEET-DRAG-001]
                    // [UI-PEEK-STEPS-BETWEEN-PINS-001]
                    //
                    // The ONLY enumeration of those states in the codebase — everything downstream
                    // asks the geometry via `SheetPositioning.capsAtPeek()` instead of re-listing
                    // them. A second list is how the selected spot got left out of the reset rule.
                    // [UI-SHEET-SPOT-PEEK-RESTS-ON-ITS-CONTENT-001]
                    capExpandAtPeek = state.mode !is HomeMode.Browse ||
                        isParkingSelected ||
                        selectedSpotId != null ||
                        selectedFuelStationId != null ||
                        selectedPublicParkingId != null,
                )
                val peekOffsetPx = positioning.peekOffsetPx
                val sheetOffsetPx = remember { Animatable(peekOffsetPx) }

                // Browse header subject swap: collapsed = your parked car (its OWN static address);
                // expanded = the zone (camera-following counter header — the car's info lives in its
                // TUS VEHÍCULOS card). Threshold = halfway to the half anchor. Both sides are the
                // same PeekState.Browse, so the swap recomposes without an AnimatedContent jump.
                // [UI-SHEET-004]
                val sheetBeyondPeek by remember(peekOffsetPx, positioning.halfOffsetPx) {
                    derivedStateOf { sheetOffsetPx.value < (peekOffsetPx + positioning.halfOffsetPx) / 2f }
                }

                // [HOME-PEEK-FOLLOWS-THE-LANE-ONCE-OPENED-001] The parked car only headlines the
                // collapsed peek until the user first opens Browse. From then on, collapsing keeps
                // the header on the selected lane (spots / car parks / fuel) instead of flipping back
                // to the car, whose info lives in its card. Saveable, so it survives a trip to another
                // tab and process restore; only opened from PURE Browse counts — a marker tap's expand
                // is a detail opening, not Browse. Any opening counts, the automatic ones included.
                //
                // [HOME-THE-SUBJECT-IS-CHOSEN-WHEN-THE-SHEET-CLOSES-001] …and it is re-decided when
                // the sheet CLOSES, which is the only other moment the user asks. Collapsed, the
                // subject never changes on its own — only its figure does. So panning across a block
                // with no car parks reads "0" on the lane you were following, instead of summoning
                // the car under your thumb; and a spot appearing next to your parked car does not
                // steal the header either. Open and close again and Home answers afresh.
                var browseLaneHeaderLatched by rememberSaveable { mutableStateOf(false) }
                // ⛔ rememberUpdatedState, not a capture: the effect below runs ONCE, so reading these
                // straight from the composition would pin it to the frame it started on.
                val latchInputs = rememberUpdatedState(
                    LaneHeaderLatchInputs(
                        browseOpen = sheetBeyondPeek && isPureBrowsePeek,
                        laneKeepsHeader = laneKeepsHeaderOnCollapse(
                            laneCount = peekSlice.laneCount,
                            laneLoading = peekSlice.laneLoading,
                            // ⛔ The SAME two cases BrowsePeek's car branch can render — a session to
                            // name, or a vehicle to ask about, taken from the strip's own order. If
                            // this said "parked" only, closing over an empty lane would hand the
                            // header to a branch that then falls through to the lane: a handover to
                            // nobody. [HOME-AN-UNPARKED-CAR-STILL-HAS-SOMETHING-TO-SAY-001]
                            hasCarToSpeakFor = peekSlice.userParking != null ||
                                peekSlice.steppableVehicleIds.isNotEmpty(),
                        ),
                    ),
                )
                LaunchedEffect(Unit) {
                    // Only the OPEN/CLOSE edge moves the latch — snapshotFlow emits on change, so the
                    // lane's own comings and goings never reach this collect.
                    snapshotFlow { latchInputs.value.browseOpen }.collect { browseOpen ->
                        browseLaneHeaderLatched = nextLaneHeaderLatch(
                            latched = browseLaneHeaderLatched,
                            browseOpen = browseOpen,
                            // Read AT the edge: the lane as it stands the instant the sheet closes.
                            laneKeepsHeader = latchInputs.value.laneKeepsHeader,
                        )
                    }
                }

                // Reset-to-peek, list auto-expand and nav-progress hoisting — see
                // HomeSheetPositioning.kt. [HOME-ATOMIZE-001 F2]
                SheetTransitionEffects(
                    positioning = positioning,
                    sheetOffsetPx = sheetOffsetPx,
                    mode = state.mode,
                    selection = state.selection,
                    navProgressState = navProgressState,
                    // While a step is pointing somewhere, its completion must not rewind the list
                    // back to the checklist. [FIRST-STEPS-SHOW-WHERE-NOT-OPEN-001]
                    firstStepGuideActive = firstStepGuide != null,
                    // An automatic opening rewinds the list first: what the app opens the sheet for
                    // is at the top. [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001]
                    lazyListState = lazyListState,
                    // [DET-ASK-STATE-001] The open question opens the sheet on its own, once.
                    promptShownAtMs = state.promptWindow?.shownAtMs,
                    // [ONBOARDING-FIRST-STEPS-MUST-BE-READABLE-AND-FOUND-001] So does the guided
                    // checklist — otherwise the tutorial only exists for a user who already knows
                    // to drag the sheet up. Same gate the card itself renders from, and it covers
                    // coming BACK from a step's explanation: Home recomposes with the sheet at peek,
                    // and the checklist has to be there again — including when reading that
                    // explanation is what finished it.
                    // [ONBOARDING-THE-COMMUNITY-STEP-CANNOT-DEMAND-A-SPOT-001]
                    firstStepsCue = browseSlice.firstStepsCue,
                    // A refused first-entry location ask opens the sheet on the warning.
                    // [HOME-LOCATION-WARNING-IS-A-BADGE-NOT-A-MODAL-001]
                    locationWarningCue = state.locationWarningCue,
                )

                // Tap-toggle, programmatic expand and nested-scroll collapse — see [SheetMotion].
                val motion = rememberSheetMotion(sheetOffsetPx, positioning, lazyListState)

                // Sheet-position gate: only re-runs on the two frames when the
                // sheet crosses the threshold, not on every drag frame.
                val sheetAtPeekLevel by remember(positioning.overlayHideThresholdPx) {
                    derivedStateOf { sheetOffsetPx.value >= positioning.overlayHideThresholdPx }
                }
                // Full overlay gate: hidden whenever the sheet is dragged up OR
                // any item is selected (spot / parking) OR a non-Browse mode is
                // active (Reporting / AddingZone / AddingParking). State reads here
                // are fine — this recomposes only when those fields change, which
                // is far less frequent than drag frames.
                val overlayVisible = sheetAtPeekLevel &&
                    state.selection == null &&
                    state.mode is HomeMode.Browse
                // Camera FABs (location / car / midpoint) remain reachable in any
                // modal state — the user may want to jump to their car while reviewing
                // a selected spot or confirming a parking pin.
                val fabsVisible = sheetAtPeekLevel

                // Cache the bleed in pixels for use inside layout-phase lambdas
                // (Modifier.layout/offset) where toDp() is not available.
                val mapBleedPx = with(density) { MAP_BOTTOM_BLEED.toPx() }
                val fabGapPx = with(density) { FAB_ABOVE_SHEET_GAP.toPx() }

                // The FLOOR of the FAB band — the y its two lanes stand on: the camera column at
                // the end edge and the GPS pill at the start edge. Both ride above the sheet edge
                // from peek up to the first expand (half), where they're still visible; past half
                // they clamp so they don't fly off-screen while fading out toward the deeper
                // expanded. [HOME-SNAP-001]
                //
                // ONE lambda, read by both lanes in the layout phase (so dragging never recomposes
                // either subtree). It is shared rather than copied because the moment there are two
                // copies of this arithmetic, the left lane and the right lane can disagree about
                // where the floor is — and the sheet moves under them 60 times a second.
                // [UI-A-WARNING-COSTS-NOTHING-WHEN-SILENT-001]
                val fabBandOffset: Density.() -> IntOffset = remember(
                    positioning, fabGapPx, rawContainerHeightPx, sheetOffsetPx,
                ) {
                    {
                        val sheetH = (rawContainerHeightPx - sheetOffsetPx.value)
                            .coerceAtLeast(0f)
                        val halfH = (rawContainerHeightPx - positioning.halfOffsetPx)
                            .coerceAtLeast(0f)
                        val base = if (sheetOffsetPx.value >= positioning.halfOffsetPx) sheetH else halfH
                        IntOffset(0, -(base + fabGapPx).roundToInt())
                    }
                }

                val dragSnap = remember(positioning) { HomeSheetSnap(positioning) }

                // O(1) spot lookup keyed by nearbySpots reference equality.
                val spotsById = remember(state.nearbySpots) {
                    state.nearbySpots.associateBy { it.id }
                }
                // Stable lambda — only recreated when the spots map changes.
                val onSpotMarkerClick: (String) -> Unit = remember(spotsById, uiController, motion) {
                    { spotId ->
                        spotsById[spotId]?.let { spot ->
                            onIntent(HomeIntent.SelectItem(HomeSelection.Spot(spotId)))
                            uiController.goToPlace(spot.location.latitude, spot.location.longitude)
                            motion.animateToExpanded()
                        }
                    }
                }

                // Stable lambda — activeSessions read via rememberUpdatedState at call-time.
                val currentActiveSessions = rememberUpdatedState(state.activeSessions)
                // Read at call-time so the tap lambda stays identity-stable while the window opens
                // and closes underneath it.
                val currentPromptCandidate = rememberUpdatedState(state.promptWindow?.candidate)
                val onMyCarMarkerClick: (sessionId: String) -> Unit = remember(uiController, motion) {
                    { sessionId ->
                        currentActiveSessions.value.firstOrNull { it.id == sessionId }?.let { p ->
                            onIntent(HomeIntent.SelectItem(HomeSelection.Parking(p.id)))
                            uiController.goToPlace(p.location.latitude, p.location.longitude)
                            motion.animateToExpanded()
                        }
                    }
                }

                // Alguien ha llegado a Home PARA ver este aparcamiento (el "Ver en mapa" de la
                // ficha). Se atiende con la MISMA lambda que un toque en su marcador — seleccionar,
                // volar la cámara, abrir el modal — así que no hay una segunda forma de llegar al
                // mismo sitio que pueda divergir. Espera a que la sesión esté en el estado: se llega
                // aquí antes de que Room emita, y descartarla dejaría el mapa en cualquier parte.
                // [UI-A-PARKED-CARD-OFFERS-THE-WAY-ACROSS-001]
                LaunchedEffect(focusParkingId, state.activeSessions) {
                    if (focusParkingId != null &&
                        state.activeSessions.any { it.id == focusParkingId }
                    ) {
                        onMyCarMarkerClick(focusParkingId)
                        onFocusParkingConsumed()
                    }
                }

                // Same gesture as a spot: select, frame, open the peek. The station is resolved at
                // call-time so the lambda stays identity-stable — and from the very list the map
                // painted, so no drawn pin can be a dead tap. [FUEL-STATIONS-MAP-AND-PEEK-001]
                // [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001]
                val currentFuelStations = rememberUpdatedState(mapSlice.fuelStations)
                val onFuelStationMarkerClick: (String) -> Unit = remember(uiController, motion) {
                    { stationId ->
                        currentFuelStations.value.firstOrNull { it.station.id == stationId }?.let { s ->
                            onIntent(HomeIntent.SelectItem(HomeSelection.FuelStation(stationId)))
                            uiController.goToPlace(s.station.latitude, s.station.longitude)
                            motion.animateToExpanded()
                        }
                    }
                }

                // Same gesture again, for the car parks. [PARKINGS-ON-THE-MAP-001]
                val currentPublicParkings = rememberUpdatedState(mapSlice.publicParkings)
                val onPublicParkingMarkerClick: (String) -> Unit = remember(uiController, motion) {
                    { parkingId ->
                        currentPublicParkings.value.firstOrNull { it.parking.id == parkingId }?.let { p ->
                            onIntent(HomeIntent.SelectItem(HomeSelection.PublicParking(parkingId)))
                            uiController.goToPlace(p.parking.latitude, p.parking.longitude)
                            motion.animateToExpanded()
                        }
                    }
                }

                // [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] Tapping the unconfirmed marker
                // opens the sheet on the question, the same gesture a spot or a parked car already
                // gets. It does NOT select anything: there is no session to select — the question is
                // whether there should be one — and it does not answer anything either. It frames
                // the place and puts the two answers in reach, which is the whole point of showing
                // the place at all.
                val onAskMarkerClick: () -> Unit = remember(uiController, motion) {
                    {
                        currentPromptCandidate.value?.let { at ->
                            uiController.goToPlace(at.latitude, at.longitude)
                        }
                        motion.animateToExpanded()
                    }
                }

                val isPinningMode = state.mode is HomeMode.Reporting ||
                    state.mode is HomeMode.AddingZone ||
                    state.mode is HomeMode.AddingParking

                // [PARK-A-DRAGGED-PIN-MUST-OUTRANK-AN-AUTOMATIC-CAMERA-001] The camera centre IS the
                // pending pin while these modes are up, so every AUTOMATIC re-frame has to stand
                // down — otherwise the first GPS fix plants the parking on the user, however far
                // they dragged. Told to the controller from the SAME `isPinningMode` the pin, the
                // dimmed spots and the sheet cap already read: a second definition of "a pin is
                // being placed" is a second chance for one of them to disagree.
                LaunchedEffect(isPinningMode) { uiController.setPinPlacementActive(isPinningMode) }

                // Pin variant per mode. All three share the same white-teardrop
                // molde — only the inner silhouette varies (P letter / car
                // glyph / zone icon). Null in Browse → default crosshair.
                val centerPinKind: CenterPinKind? = when (state.mode) {
                    is HomeMode.Reporting -> CenterPinKind.Report
                    is HomeMode.AddingZone -> CenterPinKind.Zone(zoneIconFor(state.addingZoneIconKey))
                    is HomeMode.AddingParking -> CenterPinKind.Parking
                    else -> null
                }

                // Declared BEFORE the map because the map's gesture observer is what closes it.
                // [UI-A-COACH-MARK-MUST-NOT-EAT-THE-GESTURE-IT-TEACHES-001]
                var spotlightSeen by remember(state.mode) { mutableStateOf(false) }

                // Stable event lambdas for HomeMapSection — extracted so the composable
                // can skip recomposition when only unrelated state fields change.
                val onZoneClick: (String) -> Unit = remember {
                    { zoneId -> onIntent(HomeIntent.SelectZone(zoneId)) }
                }
                val onMapCameraMove: (Double, Double) -> Unit = remember(uiController, mapInteraction) {
                    { lat, lon ->
                        uiController.onCameraMoved(lat, lon)
                        onIntent(HomeIntent.CameraPositionChanged(lat, lon))
                        mapInteraction.onCameraFrame()
                    }
                }

                // ── Map ──────────────────────────────────────────────────────
                // Height is set via Modifier.layout so sheetOffsetPx is read in
                // the layout phase only — dragging never triggers recomposition here.
                val isAddingZone = state.mode is HomeMode.AddingZone
                HomeMapSection(
                    slice = mapSlice,
                    drivingPuck = trip.puck,
                    tripTrail = trip.trail,
                    matchedTrail = trip.matchedTrail,
                    departurePoint = trip.departurePoint,
                    selectedSpotId = selectedSpotId,
                    // Per-vehicle: only the matching marker renders selected. [MULTI-PARKING-001]
                    selectedSessionId = (state.selection as? HomeSelection.Parking)?.id?.takeIf { isParkingSelected },
                    reportMode = isPinningMode,
                    cameraTarget = uiController.cameraTarget,
                    centerPin = centerPinKind,
                    dimSpots = isPinningMode || state.selection != null,
                    onSpotClick = onSpotMarkerClick,
                    onMyCarClick = onMyCarMarkerClick,
                    onAskMarkerClick = onAskMarkerClick,
                    onZoneClick = onZoneClick,
                    onFuelStationClick = onFuelStationMarkerClick,
                    onPublicParkingClick = onPublicParkingMarkerClick,
                    onCameraMove = onMapCameraMove,
                    // El foco del primer aparcamiento se cierra AQUI, no en su propia capa: el
                    // observador del mapa es el que ve el toque de verdad, y no lo consume, asi que
                    // el mismo arrastre sigue moviendo el mapa.
                    // [UI-A-COACH-MARK-MUST-NOT-EAT-THE-GESTURE-IT-TEACHES-001]
                    onUserMapGesture = {
                        uiController.onUserMapGesture()
                        // Desde este gesto, la cámara es SUYA — y eso tiene que sobrevivir a salir
                        // de Home, cosa que el controlador no hace.
                        // [HOME-WHAT-IS-REMEMBERED-IS-THE-PLACE-YOU-CHOSE-001]
                        onIntent(HomeIntent.UserMovedCameraByHand)
                        spotlightSeen = true
                    },
                    followingDriver = uiController.followingDriver,
                    // El centro DERIVADO, no el reportado: con la cámara quieta el mapa no dispara
                    // `onCameraMoved`, así que el anillo no existía hasta el primer arrastre —
                    // justo cuando el usuario necesita ver el radio que va a colocar.
                    // [MAP-A-RING-YOU-CANNOT-SEE-IS-TWO-BUGS-001] (issue #78)
                    previewZoneLat = if (isAddingZone) uiController.cameraCenterLat else null,
                    previewZoneLon = if (isAddingZone) uiController.cameraCenterLon else null,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .layout { measurable, constraints ->
                            val heightPx = (sheetOffsetPx.value + mapBleedPx)
                                .roundToInt().coerceAtLeast(0)
                            val placeable = measurable.measure(
                                constraints.copy(
                                    minHeight = 0,
                                    maxHeight = heightPx.coerceAtMost(constraints.maxHeight),
                                )
                            )
                            layout(placeable.width, heightPx) { placeable.place(0, 0) }
                        },
                )

                // ── First-steps spotlight over the centre pin ─────────────────
                // [ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001] The one gesture in the whole
                // product that is not self-evident: the pin does not follow your finger, the MAP
                // does. Shown only while the guided checklist is still asking for the first
                // parking — after that the user has done it once and the coach mark would be noise.
                //
                // It repeats the SAME `Modifier.layout` as the map above on purpose: identical
                // bounds mean the hole's centre and the pin's centre are the same point by
                // construction, with nothing measured and nothing to keep in sync. [see PapSpotlight]
                PapSpotlight(
                    visible = state.mode is HomeMode.AddingParking &&
                        state.firstSteps.isVisible &&
                        state.firstSteps.current == FirstStep.MARK_PARKING &&
                        !spotlightSeen,
                    caption = stringResource(Res.string.first_steps_spotlight_caption),
                    onDismiss = { spotlightSeen = true },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .layout { measurable, constraints ->
                            val heightPx = (sheetOffsetPx.value + mapBleedPx)
                                .roundToInt().coerceAtLeast(0)
                            val placeable = measurable.measure(
                                constraints.copy(
                                    minHeight = 0,
                                    maxHeight = heightPx.coerceAtMost(constraints.maxHeight),
                                )
                            )
                            layout(placeable.width, heightPx) { placeable.place(0, 0) }
                        },
                )

                // ── Floating search header ────────────────────────────────────
                HomeFloatingHeader(
                    visible = overlayVisible,
                    highlightAddZone = firstStepGuide == FirstStepGuide.ADD_ZONE_CHIP,
                    slice = headerSlice,
                    uiController = uiController,
                    userGpsPoint = state.userGpsPoint,
                    onIntent = onIntent,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding(),
                )

                // ── FAB band · start-edge lane: the location warning ─────────
                // Same floor and same gate as the column opposite it. Silent — and taking no
                // space at all — while there is a fix good enough to park on, which is nearly
                // always. [UI-A-WARNING-COSTS-NOTHING-WHEN-SILENT-001]
                // Home's ONE voice about the location: no fix at all, or a fix too coarse to
                // trust. [UI-NO-LOCATION-WEARS-THE-ACCURACY-PILL-001]
                HomeMapGpsLane(
                    locationMissing = fabsSlice.locationMissing,
                    accuracy = fabsSlice.gpsAccuracy,
                    // The pill inherits the badge's action, not just its words: tapping it lands on
                    // the same permission flow. A warning nobody can act on turns a fixable problem
                    // into a complaint. [HOME-LOCATION-WARNING-IS-A-BADGE-NOT-A-MODAL-001]
                    onFixLocation = { onActivateDetection(CORE_PERMISSIONS_FOCUS) },
                    visible = fabsVisible,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(fabBandOffset),
                )

                // ── FAB band · end-edge lane: the camera column ──────────────
                // Bottom positioning is done in the layout phase via Modifier.offset
                // so dragging never triggers recomposition of this subtree.
                HomeMapFabsSection(
                    slice = fabsSlice,
                    visible = fabsVisible,
                    isDriving = trip.isDriving.value,
                    drivingPuck = trip.puck,
                    uiController = uiController,
                    userParking = state.userParking,
                    userGpsPoint = state.userGpsPoint,
                    activeSessions = state.activeSessions,
                    selectedSession = state.selectedSession,
                    onMyCarMarkerClick = onMyCarMarkerClick,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(fabBandOffset),
                )

                // ── Bottom sheet ─────────────────────────────────────────────
                val sheetFrame = HomeSheetFrame(
                    containerHeightPx = rawContainerHeightPx,
                    sheetOffsetPx = sheetOffsetPx,
                    dragSnap = dragSnap,
                    lazyListState = lazyListState,
                    rowsScroll = sheetRowsScroll,
                    nestedScroll = motion.nestedScrollConnection,
                    bottomContentPadding = stableBottomPadding,
                    coroutineScope = coroutineScope,
                    onPeekHeightChanged = { h ->
                        // EXACT per-state height (no hysteresis): peekOffset = container - peekHeight,
                        // so the peek header's bottom edge — and its divider — lands flush on the
                        // bottom-nav divider, with no dp of gap, in every state. [BUG-PEEK-DIVIDER-ALIGN]
                        if (h != peekHeightPx) peekHeightPx = h
                    },
                )
                HomeSheetSection(
                    peek = peekSlice,
                    browse = browseSlice,
                    state = state,
                    frame = sheetFrame,
                    browseShowsZoneHeader = sheetBeyondPeek,
                    browseLaneHeaderLatched = browseLaneHeaderLatched,
                    onIntent = onIntent,
                    uiController = uiController,
                    // A stepper press IS a marker tap — same lambda, so select + camera + sheet
                    // can't drift between the two. [UI-PEEK-STEPS-BETWEEN-PINS-001]
                    onSelectSpot = onSpotMarkerClick,
                    onSelectParking = onMyCarMarkerClick,
                    onSelectFuelStation = onFuelStationMarkerClick,
                    onSelectPublicParking = onPublicParkingMarkerClick,
                    onRelease = { sessionId ->
                        onIntent(HomeIntent.OpenReleaseDialog(sessionId))
                    },
                    onNavigateExternal = openExternalNav,
                    onToggle = motion.toggle,
                    onExpandSheet = motion.animateToExpanded,
                    onCollapseSheet = motion.animateToPeek,
                    onGuide = { target -> firstStepGuide = target },
                    firstStepGuide = firstStepGuide,
                    onActivateDetection = onActivateDetection,
                    onAddVehicle = onAddVehicle,
                    onOpenVehicle = onOpenVehicle,
                    onLinkVehicleBluetooth = onLinkVehicleBluetooth,
                    onOpenStepExplainer = onOpenStepExplainer,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Named sections & effects — the pieces HomeContent orchestrates
// ─────────────────────────────────────────────────────────────────────────────

/** Per-field trip States derived from the tripRender StateFlow — read only in
 *  isolated scopes so a GPS fix never recomposes HomeContent. [DRIVE-PUCK-NATIVE-001] */
@Stable
private class TripRenderStates(tripState: State<TripUpdate>) {
    val puck: State<DrivingPuck?> = derivedStateOf { tripState.value.puck }
    val trail: State<List<GpsPoint>> = derivedStateOf { tripState.value.trail }
    val matchedTrail: State<List<GpsPoint>> = derivedStateOf { tripState.value.matchedTrail }
    val departurePoint: State<GpsPoint?> = derivedStateOf { tripState.value.departurePoint }
    val isDriving: State<Boolean> = derivedStateOf { tripState.value.puck != null }
}

/**
 * Glass-on-drag tracking: [isInteracting] is the only snapshot state that feeds
 * the glass effect, and it only flips twice per gesture (true on first real drag
 * frame, false once [MAP_INTERACTION_IDLE_DELAY_MS] elapses without new frames).
 * Nothing in composition reads the idle Job, so writing it on rapid onCameraMove
 * ticks never invalidates a composition scope.
 */
@Stable
private class MapInteractionTracker(
    private val scope: CoroutineScope,
    private val uiController: HomeUiController,
) {
    var isInteracting by mutableStateOf(false)
        private set
    private var idleJob: Job? = null

    val onCameraFrame: () -> Unit = {
        if (!uiController.isProgrammaticMove) {
            if (!isInteracting) isInteracting = true
            idleJob?.cancel()
            idleJob = scope.launch {
                delay(MAP_INTERACTION_IDLE_DELAY_MS.milliseconds)
                isInteracting = false
            }
        }
    }
}

@Composable
private fun rememberMapInteractionTracker(uiController: HomeUiController): MapInteractionTracker {
    val scope = rememberCoroutineScope()
    val tracker = remember(uiController, scope) { MapInteractionTracker(scope, uiController) }

    // Clear the programmatic-move flag once the camera animation has settled.
    // isProgrammaticMove is flipped synchronously by every uiController camera door
    // before cameraTarget mutates, so this effect runs after the flag is
    // already true — it only needs to clear it when the animation is done.
    LaunchedEffect(uiController.cameraTarget?.token) {
        if (!uiController.isProgrammaticMove) return@LaunchedEffect
        delay(PROGRAMMATIC_MOVE_GUARD_MS.milliseconds)
        uiController.clearProgrammaticMove()
    }
    return tracker
}

/** Camera choreography that belongs to the [uiController]: initial focus, parking
 *  re-frame, driver-follow and the MoveCameraTo effect collector. No UI. */
@Composable
private fun HomeCameraEffects(
    uiController: HomeUiController,
    state: HomeState,
    drivingPuck: State<DrivingPuck?>,
    effects: SharedFlow<HomeEffect>,
) {
    // [HOME-A-LANE-CHANGE-TAKES-THE-CAMERA-WITH-IT-001] Cambiar de carril lleva la cámara al primer
    // elemento de ese carril. Sin esto, tocar «Gasolineras» deja el mapa donde estaba —mirando unas
    // plazas que ya no se pintan— y el usuario tiene que buscar a mano lo que acaba de pedir.
    //
    // ⛔ **Y espera a que el carril tenga algo**: parkings y gasolineras se piden al entrar, así que
    // en el instante del cambio la lista está vacía. El encuadre queda ARMADO y se dispara con la
    // primera lista no vacía, que es lo que pidió la issue («si no está cargado, pues cuando carguen
    // los elementos»).
    //
    // ⛔ La primera composición NO encuadra: el carril de arranque es PLAZAS y ahí manda el enfoque
    // inicial de [FOCUS-001] (tu coche, o tú). Esto sólo reacciona a un cambio deliberado de carril.
    var laneAwaitingFrame by remember { mutableStateOf<HomeNearbyLens?>(null) }
    var laneFramingArmed by remember { mutableStateOf(false) }
    LaunchedEffect(state.nearbyLens) {
        if (!laneFramingArmed) {
            laneFramingArmed = true
            return@LaunchedEffect
        }
        laneAwaitingFrame = state.nearbyLens
    }
    val laneFirstPoint = state.laneFirstPoint(uiController.cameraLat, uiController.cameraLon)
    LaunchedEffect(state.nearbyLens, laneFirstPoint) {
        if (laneAwaitingFrame != state.nearbyLens) return@LaunchedEffect
        val (lat, lon) = laneFirstPoint ?: return@LaunchedEffect
        // `goToPlace` es la puerta deliberada: suelta el follow del conductor y trata el zoom como
        // SUELO, así que ir al primer elemento nunca le cuesta al usuario volver a acercar.
        // [MAP-A-CAMERA-MOVE-MUST-NOT-COST-A-PINCH-001]
        uiController.goToPlace(lat, lon)
        laneAwaitingFrame = null
    }

    // [UI-THE-ASK-IS-A-CAMERA-SUBJECT-001] Frame the place an open "did you park?" question is about.
    // The sheet already opens itself on that question ([SheetTransitionEffects]); without this the
    // camera stayed wherever it was, so on the very case the question exists for — park, walk off,
    // open the app — the sheet asked about one place while the map showed another.
    //
    // Keyed by the question's own timestamp, the same identity the sheet's auto-open uses, so the two
    // surfaces open on the same place exactly once. Deliberately NOT keyed on the GPS fix: the framing
    // is tight on the asked place and needs no user position, which is what lets it fire on a cold
    // open before the first fix and claim the initial focus from inside the controller.
    //
    // Declared BEFORE the initial focus so that on an open where the fix is already in hand the ask
    // frames first and the initial focus finds its one-shot spent — one camera move, not a centre on
    // the user immediately corrected.
    //
    // A live trip owns the camera: with the puck moving, an unanswered question is about an earlier
    // stop and must not steal the drive's frame. [DET-READY-TRIP-OVER-PARKED-001]
    LaunchedEffect(state.promptWindow?.shownAtMs) {
        val window = state.promptWindow ?: return@LaunchedEffect
        val at = window.candidate ?: return@LaunchedEffect
        if (drivingPuck.value != null) return@LaunchedEffect
        uiController.frameTheAsk(window.shownAtMs, at.latitude to at.longitude)
    }

    LaunchedEffect(state.userGpsPoint) {
        val gps = state.userGpsPoint ?: return@LaunchedEffect
        // Stateful initial focus: frame the parked car (with the user, if close), else centre on the
        // user so nearby free spots reveal around them. One-shot, inside the controller. [FOCUS-001]
        // A live trip owns the camera (driver-follow below): framing a parked car first would open
        // the map on the OTHER car and then snap away to the moving one. Centring on the user is
        // already centring on the car being driven. [DET-READY-TRIP-OVER-PARKED-001]
        uiController.centerInitialFocus(
            parking = state.userParking
                ?.takeIf { drivingPuck.value == null }
                ?.let { it.location.latitude to it.location.longitude },
            selectedSpot = state.selectedSpot?.let { it.location.latitude to it.location.longitude },
            user = gps.latitude to gps.longitude,
        )
    }

    // [HOME-LOCATION-WARNING-IS-A-BADGE-NOT-A-MODAL-001] No location at all (permission denied or GPS
    // off) and no fix in hand, but a car is parked: the initial focus above will never fire, so fly to
    // the car instead. A live trip owns the camera. One-shot inside the controller.
    // ⛔ The SAME verdict the map's pill states — this line used to spell those two terms out a
    // second time. [HOME-WITHOUT-LOCATION-STILL-ANSWERS-001]
    val locationUnavailable = homeHasNoLocation(state.hasCorePermissions, state.detectionUiState)
    LaunchedEffect(state.userParking?.id, locationUnavailable) {
        val parking = state.userParking ?: return@LaunchedEffect
        if (!locationUnavailable || state.userGpsPoint != null || drivingPuck.value != null) return@LaunchedEffect
        uiController.centerOnParkingWithoutLocation(parking.location.latitude to parking.location.longitude)
    }

    // Y sin coche aparcado, una zona guardada — si no, Home abre en (0, 0) y todo lo que se pregunta
    // POR LA CÁMARA (plazas, parkings, gasolineras) se queda sin sitio por el que preguntar. El
    // controller lleva el one-shot, así que el coche de arriba siempre gana si existe.
    // [HOME-WITHOUT-LOCATION-STILL-ANSWERS-001]
    LaunchedEffect(state.zones.firstOrNull()?.id, state.userParking?.id, locationUnavailable) {
        if (!locationUnavailable || state.userParking != null) return@LaunchedEffect
        if (state.userGpsPoint != null || drivingPuck.value != null) return@LaunchedEffect
        val zone = state.zones.firstOrNull() ?: return@LaunchedEffect
        uiController.centerOnZoneWithoutLocation(zone.lat to zone.lon)
    }

    // Re-frame the car once if its session loads just after the first GPS fix (common race), unless
    // the user already panned by hand or a trip is live — the controller enforces those guards.
    // [FOCUS-002] [DET-READY-TRIP-OVER-PARKED-001]
    LaunchedEffect(state.userParking?.id) {
        val parking = state.userParking ?: return@LaunchedEffect
        if (drivingPuck.value != null) return@LaunchedEffect
        uiController.refocusOnParkingArrival(
            parking = parking.location.latitude to parking.location.longitude,
            user = state.userGpsPoint?.let { it.latitude to it.longitude },
        )
    }

    // Driver-follow: engage when a detected trip starts (the driving puck appears) and disengage when
    // it ends; while engaged, the camera tracks each puck update without changing the user's zoom.
    // A manual pan pauses it (handled in the controller), and the map shows a resume FAB. [FOLLOW-001]
    // Observe the puck via snapshotFlow (in a coroutine) rather than reading it in the composition, so
    // driver-follow tracking doesn't recompose this screen per fix. [DRIVE-PUCK-NATIVE-001] [FOLLOW-001]
    LaunchedEffect(uiController) {
        snapshotFlow { drivingPuck.value != null }.collect { uiController.setDriverFollowActive(it) }
    }
    LaunchedEffect(uiController) {
        snapshotFlow { drivingPuck.value }.collect { puck ->
            puck?.let { uiController.followDriver(it.latitude, it.longitude) }
        }
    }

    // Dedicated collector for camera-move effects (e.g. zone chip tap).
    // Lives here because uiController is scoped to HomeContent.
    LaunchedEffect(Unit) {
        effects.collect { effect ->
            if (effect is HomeEffect.MoveCameraTo) {
                // [MAP-A-CAMERA-MOVE-MUST-NOT-COST-A-PINCH-001] Dos intenciones distintas, y antes
                // las dos se aplicaban en absoluto:
                //  · Navegar es un SUELO. Tocar una zona desde nivel calle te alejaba a 15 y había
                //    que volver a acercar a mano — la queja de la issue #31.
                //  · Editar una zona SÍ es un encuadre (hay que ver el círculo entero), pero por su
                //    RADIO: un garaje de 40 m no necesita el zoom 14 fijo de antes, que enseña 4 km.
                val (zoom, intent) = when (effect.frame) {
                    CameraFrame.Navigate -> NAVIGATE_ZOOM to ZoomIntent.AtLeast
                    CameraFrame.ZoneEditing ->
                        zoomForZoneRadius(state.addingZoneRadius) to ZoomIntent.Fit
                }
                uiController.goToPlace(effect.lat, effect.lon, zoom = zoom, zoomIntent = intent)
            }
        }
    }
}

@Composable
private fun HomeReleaseDialogHost(
    visible: Boolean,
    releasingReason: ParkingReleaseReason?,
    sessionId: String?,
    vehicleName: String?,
    vehicleIsActive: Boolean,
    detectionAvailable: Boolean,
    privateZoneName: String?,
    onIntent: (HomeIntent) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible || sessionId == null) return
    // The id came from the tapped peek. The VM resolves the session by id and derives the
    // release location from it — no coordinates or ranked fallback here. [VEH-ACTIVE-FENCE-001]
    // One answer now: leaving in this car, which is also the declaration that you drive it.
    // [PARK-DELETE-NO-DECLARE-001] [SPOT-A-PRIVATE-ZONE-DECIDES-THE-PUBLICATION-NOT-A-BUTTON-001]
    fun release(reason: ParkingReleaseReason) {
        onIntent(HomeIntent.ReleaseParking(sessionId = sessionId, reason = reason))
    }
    // Leaving re-arms detection. If this car wasn't the active one, releasing IS the declaration
    // that you drive it (the VM makes it active), so the copy says so. When detection is off or
    // blocked the promise would be a lie — the note says so instead, and Home's story surface
    // offers the fix after the release. [VEH-ACTIVE-FENCE-001] [UX-PARKED-STATE-001]
    val detectionNote = when {
        !detectionAvailable ->
            stringResource(Res.string.home_release_dialog_detection_unavailable)
        !vehicleIsActive && vehicleName != null ->
            stringResource(Res.string.home_release_dialog_detection_inactive, vehicleName)
        else ->
            stringResource(Res.string.home_release_dialog_detection_active)
    }
    HomeReleaseDialog(
        releasingReason = releasingReason,
        onDismiss = { if (releasingReason == null) onDismiss() },
        onConfirm = { release(ParkingReleaseReason.DEPARTURE) },
        detectionNote = detectionNote,
        privateZoneName = privateZoneName,
    )
}

/**
 * Floating search header over the map. The detection ACTION surface no longer
 * lives here — it is a section inside the bottom sheet, under the address
 * header. The wrapping column owns the status-bar inset (HomeHeaderSection no
 * longer applies it). [DET-READY-001h]
 */
@Composable
private fun HomeFloatingHeader(
    visible: Boolean,
    highlightAddZone: Boolean,
    slice: HomeHeaderSlice,
    uiController: HomeUiController,
    userGpsPoint: GpsPoint?,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(PapMotion.medium()),
            exit = fadeOut(PapMotion.medium()),
        ) {
            HomeHeaderSection(
                slice = slice,
                highlightAddZone = highlightAddZone,
                onSearchQueryChanged = { onIntent(HomeIntent.SearchQueryChanged(it)) },
                onSearchResultClick = { result ->
                    uiController.goToPlace(result.lat, result.lon, zoom = NAVIGATE_ZOOM)
                    onIntent(HomeIntent.SelectSearchResult(result))
                },
                onSearchClear = { onIntent(HomeIntent.ClearSearch) },
                onMapTypeSelected = { onIntent(HomeIntent.SetMapType(it)) },
                onSelectZone = { id -> onIntent(HomeIntent.SelectZone(id)) },
                onAddZone = {
                    onIntent(
                        HomeIntent.EnterAddZoneMode(
                            lat = uiController.cameraLat ?: userGpsPoint?.latitude,
                            lon = uiController.cameraLon ?: userGpsPoint?.longitude,
                        )
                    )
                },
                onEditZone = { id -> onIntent(HomeIntent.EnterEditZoneMode(id)) },
            )
        }
    }
}

/**
 * The bottom sheet plus the SINGLE translation point of its [HomeSheetAction]
 * channel: sheet motion, local UI state, camera moves and navigation. Intents
 * flow through untouched — the sheet emits them directly. [HOME-ATOMIZE-001 F3]
 */
@Composable
private fun HomeSheetSection(
    peek: HomePeekSlice,
    browse: HomeBrowseListSlice,
    state: HomeState,
    frame: HomeSheetFrame,
    browseShowsZoneHeader: Boolean,
    browseLaneHeaderLatched: Boolean,
    onIntent: (HomeIntent) -> Unit,
    uiController: HomeUiController,
    onSelectSpot: (spotId: String) -> Unit,
    onSelectParking: (sessionId: String) -> Unit,
    /** Same gesture as a marker tap: select, frame, open the peek. [FUEL-STATIONS-MAP-AND-PEEK-001] */
    onSelectFuelStation: (stationId: String) -> Unit,
    onSelectPublicParking: (parkingId: String) -> Unit,
    onRelease: (sessionId: String) -> Unit,
    onNavigateExternal: (lat: Double, lon: Double, walking: Boolean) -> Unit,
    onToggle: () -> Unit,
    /** Open the sheet to its "expanded" anchor — the same motion a tap on the peek performs.
     *  [ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001] */
    onExpandSheet: () -> Unit,
    /** Settle the sheet at peek, revealing the floating header. [FIRST-STEPS-SHOW-WHERE-NOT-OPEN-001] */
    onCollapseSheet: () -> Unit,
    /** Start highlighting a control a checklist step points at. [FIRST-STEPS-SHOW-WHERE-NOT-OPEN-001] */
    onGuide: (FirstStepGuide) -> Unit,
    firstStepGuide: FirstStepGuide?,
    onActivateDetection: (focus: String) -> Unit,
    onAddVehicle: () -> Unit,
    onOpenVehicle: (vehicleId: String) -> Unit,
    onLinkVehicleBluetooth: (vehicleId: String) -> Unit,
    onOpenStepExplainer: (FirstStep) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onAction: (HomeSheetAction) -> Unit = { action ->
        when (action) {
            HomeSheetAction.ToggleSheet -> onToggle()
            // [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001] Its own screen: no surface of
            // Home stays on screen competing with what is being explained.
            is HomeSheetAction.OpenFirstStepExplainer -> onOpenStepExplainer(action.step)
            is HomeSheetAction.SelectSpot -> onSelectSpot(action.spotId)
            is HomeSheetAction.SelectFuelStation -> onSelectFuelStation(action.stationId)
            // Straight to the intent: following is a WRITE to the user's own store, with no camera
            // move and no selection change — nothing for this translation point to arrange.
            is HomeSheetAction.ToggleFollowFuelStation ->
                onIntent(HomeIntent.ToggleFollowFuelStation(action.stationKey))
            is HomeSheetAction.SelectPublicParking -> onSelectPublicParking(action.parkingId)
            // The car lane steps over VEHICLES; each stop resolves to its modal here, in the one
            // translation point. Parked → the very lambda a marker tap uses; unparked → the very
            // intent the vehicle's "Aparcar" chip sends, plus a camera fly to the user's GPS so
            // the new pin doesn't sprout on the previous car's spot (the GPS is also the mode's
            // fallback coordinate). [UI-PEEK-STEPS-WALK-VEHICLES-NOT-SESSIONS-001]
            is HomeSheetAction.StepToVehicle -> {
                val session = state.activeSessions.firstOrNull { it.vehicleId == action.vehicleId }
                if (session != null) {
                    onSelectParking(session.id)
                } else {
                    onIntent(
                        HomeIntent.EnterAddParkingMode(
                            initialGps = state.userGpsPoint,
                            targetVehicleId = action.vehicleId,
                        ),
                    )
                    state.userGpsPoint?.let { uiController.goToPlace(it.latitude, it.longitude) }
                }
            }
            is HomeSheetAction.RequestRelease -> onRelease(action.sessionId)
            // [SPOT-A-REPORT-WITHOUT-A-PLACE-MUST-NOT-HAPPEN-001] No place, no report mode. This
            // used to fall back to `0.0, 0.0` — the Gulf of Guinea — and a spot published there is
            // garbage on everyone else's map. The CTAs above are disabled without a place, so this
            // guard is the floor, not the message: there is nothing to tell a user who could not
            // have pressed anything.
            HomeSheetAction.RequestReportMode -> pinStartPoint(uiController, state)?.let { (lat, lon) ->
                onIntent(HomeIntent.EnterReportMode(lat = lat, lon = lon))
            }
            is HomeSheetAction.MoveCamera -> uiController.goToPlace(action.lat, action.lon)
            is HomeSheetAction.NavigateExternal ->
                onNavigateExternal(action.lat, action.lon, action.walking)
            // Only the CORE tier routes here (Inactive uses the unified EnableAutoDetection
            // intent). Focus the permissions screen on the essential tier. [DET-TOGGLE-001]
            HomeSheetAction.OpenCorePermissions -> onActivateDetection(CORE_PERMISSIONS_FOCUS)
            HomeSheetAction.AddVehicle -> onAddVehicle()
            // [UI-A-PARKED-CARD-OFFERS-THE-WAY-ACROSS-001] Home hands over to the Vehicles tab,
            // carrying WHICH car so the ficha opens on the one the modal was about.
            is HomeSheetAction.OpenVehicle -> onOpenVehicle(action.vehicleId)
            // [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001] Same destination Settings uses.
            is HomeSheetAction.LinkVehicleBluetooth -> onLinkVehicleBluetooth(action.vehicleId)
            // The checklist asking the sheet to show a subject. Three moves, one place, whichever
            // lane asked for it. [ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001]
            // [ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001]
            //
            // "See spots" only wears that face when there ARE spots on offer, so it never lands on
            // an empty section — with one honest exception: a size filter the user set can still
            // leave it showing "none your size", which is the right thing to show them (the filter
            // bar is right there). A COMPLEMENT lane has no such promise to keep and needs none:
            // whatever it finds, entering it is what the step teaches, and the lane says its own
            // honest sentence about what it found.
            // [FIRST-STEPS-SHOW-WHERE-NOT-OPEN-001] Bring the control on screen and highlight it; the
            // user's own tap is what uses it.
            is HomeSheetAction.GuideTo -> {
                onGuide(action.target)
                when (action.target) {
                    FirstStepGuide.SPOTS_LANE, FirstStepGuide.REPORT_SPOT_CARD -> {
                        // Point the lane AT spots — never a toggle. The report card only exists there.
                        // [HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001]
                        onIntent(HomeIntent.SetNearbyLens(HomeNearbyLens.SPOTS))
                        onExpandSheet()
                        frame.coroutineScope.launch {
                            // Let the lane recompose before measuring where its items are.
                            withFrameNanos { }
                            withFrameNanos { }
                            val list = frame.lazyListState
                            val index = if (action.target == FirstStepGuide.REPORT_SPOT_CARD) {
                                // The report card is the last item of the spots lane.
                                (list.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                            } else {
                                list.nearbyLensIndex()
                            }
                            list.animateScrollToItem(index)
                        }
                    }
                    // Scroll to the tabs and highlight them — the user picks the lane.
                    FirstStepGuide.NEARBY_COMPLEMENT_TABS -> {
                        onExpandSheet()
                        frame.coroutineScope.launch {
                            frame.lazyListState.animateScrollToItem(frame.lazyListState.nearbyLensIndex())
                        }
                    }
                    // El chip de zona vive en el header flotante, que sólo se dibuja con la hoja
                    // abajo — así que bajarla es la única forma de que exista.
                    //
                    // ⛔ Pero ANTES se anuncia: el paso se marca y dice que lo señala arriba, y sólo
                    // entonces baja la hoja. Sin ese respiro la hoja daba un salto sin causa visible
                    // y el usuario llegaba al chip sin saber por qué se había movido nada — leer el
                    // motivo DESPUÉS del movimiento es leerlo tarde.
                    // [ONB-A-GUIDE-ANSWERS-UNTIL-YOU-ACT-001]
                    FirstStepGuide.ADD_ZONE_CHIP -> frame.coroutineScope.launch {
                        delay(GUIDE_ANNOUNCE_MS)
                        onCollapseSheet()
                    }
                }
            }
        }
    }
    HomeBottomSheet(
        peek = peek,
        browse = browse,
        frame = frame,
        firstStepGuide = firstStepGuide,
        browseShowsZoneHeader = browseShowsZoneHeader,
        browseLaneHeaderLatched = browseLaneHeaderLatched,
        onIntent = onIntent,
        onAction = onAction,
        modifier = modifier,
    )

}

/**
 * Where a pin mode STARTS: the camera if the map has drawn a frame, else the last GPS fix, else
 * nowhere. [SPOT-A-REPORT-WITHOUT-A-PLACE-MUST-NOT-HAPPEN-001]
 *
 * Null is a real answer and the callers honour it. Before this, report mode defaulted to
 * `0.0, 0.0` when both were missing — a coordinate nobody chose, in the middle of the ocean, on a
 * surface whose whole job is publishing places to strangers. The window is small (the first frames
 * after a cold start) but what it produces is permanent: a spot on the map that is not anywhere.
 */
internal fun pinStartPoint(uiController: HomeUiController, state: HomeState): Pair<Double, Double>? {
    val lat = uiController.cameraLat ?: state.userGpsPoint?.latitude ?: return null
    val lon = uiController.cameraLon ?: state.userGpsPoint?.longitude ?: return null
    return lat to lon
}

/**
 * Where the nearby lane starts in the sheet list — its sticky chip row.
 * [ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001]
 *
 * Resolved by that row's own item KEY, so nothing here counts the items above it — that count depends
 * on the checklist, the detection story and the vehicle strip all being present or not, and a second
 * tally of those would be wrong the next time one of them changes.
 *
 * The fallback when the row is not composed yet is the LAST item, which is not a different
 * destination: everything below the lens row belongs to the lane, so both roads end inside it.
 */
private fun LazyListState.nearbyLensIndex(): Int =
    nearbyLensIndexOrNull() ?: (layoutInfo.totalItemsCount - 1).coerceAtLeast(0)

/** The right-side camera FAB column plus its actions (locate/car/midpoint). */
@Composable
private fun HomeMapFabsSection(
    slice: HomeFabsSlice,
    visible: Boolean,
    isDriving: Boolean,
    drivingPuck: State<DrivingPuck?>,
    uiController: HomeUiController,
    userParking: UserParking?,
    userGpsPoint: GpsPoint?,
    activeSessions: List<UserParking>,
    selectedSession: UserParking?,
    onMyCarMarkerClick: (sessionId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Stable lambdas — GPS and parking read via rememberUpdatedState at call-time.
    val currentUserParking = rememberUpdatedState(userParking)
    val currentUserGpsPoint = rememberUpdatedState(userGpsPoint)
    val onMyLocation: () -> Unit = remember(uiController) {
        {
            // Mid-trip, "locate me" means the moving car: re-engage driver-follow and snap onto
            // the puck (paused earlier by a map gesture). Otherwise recentre on GPS. [FOLLOW-001]
            //
            // ⛔ Las DOS ramas encuadran. Sólo lo hacía la de abajo, así que el mismo botón acercaba
            // parado y no acercaba en ruta — el zoom es del ACTO de pedir tu localización, no de la
            // rama que lo atiende. El suelo de la de arriba lo pone `resumeDriverFollow`.
            // [HOME-A-LOCATE-ME-TAP-IS-A-FRAMING-001]
            val puck = drivingPuck.value
            if (puck != null) {
                uiController.resumeDriverFollow(puck.latitude, puck.longitude)
            } else {
                currentUserGpsPoint.value?.let {
                    // El número ya tenía nombre: era el mismo 16 del enfoque inicial, escrito dos
                    // veces. [HOME-A-LOCATE-ME-TAP-IS-A-FRAMING-001]
                    uiController.goToPlace(
                        it.latitude,
                        it.longitude,
                        zoom = HomeUiController.FOCUS_SEARCH_ZOOM,
                    )
                }
            }
        }
    }
    val onMidpoint: () -> Unit = remember(uiController) {
        {
            val parking = currentUserParking.value
            val gps = currentUserGpsPoint.value
            if (parking != null && gps != null) {
                uiController.framePlaces(
                    lat1 = parking.location.latitude,
                    lon1 = parking.location.longitude,
                    lat2 = gps.latitude,
                    lon2 = gps.longitude,
                )
            }
        }
    }
    HomeMapFabsLayer(
        slice = slice,
        visible = visible,
        isDriving = isDriving,
        onMyLocation = onMyLocation,
        onParkedCar = {
            // Cycle through the parked vehicles: no selection → first session,
            // a selected session → the next one (wraps around). [MULTI-PARKING-001]
            if (activeSessions.isNotEmpty()) {
                val target = if (selectedSession != null) {
                    val idx = activeSessions.indexOfFirst { it.id == selectedSession.id }
                    activeSessions[(idx + 1) % activeSessions.size]
                } else {
                    activeSessions.first()
                }
                onMyCarMarkerClick(target.id)
            }
        },
        onMidpoint = onMidpoint,
        modifier = modifier,
    )
}

