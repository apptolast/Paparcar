package io.apptolast.paparcar.presentation.home

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
import io.apptolast.paparcar.presentation.util.collectAsStateLifecycleAware
import io.apptolast.paparcar.presentation.util.MapForegroundEffect
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import io.apptolast.paparcar.domain.error.PaparcarError
import io.apptolast.paparcar.domain.model.DrivingPuck
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.presentation.home.sections.header.HomeHeaderSection
import io.apptolast.paparcar.presentation.home.sections.map.HomeMapFabsLayer
import io.apptolast.paparcar.presentation.home.sections.map.HomeMapSection
import io.apptolast.paparcar.presentation.home.sections.sheet.HomeBottomSheet
import io.apptolast.paparcar.presentation.home.sections.sheet.HomeSheetAction
import io.apptolast.paparcar.presentation.home.sections.sheet.HomeSheetFrame
import io.apptolast.paparcar.presentation.home.sections.sheet.HomeSheetSnap
import io.apptolast.paparcar.presentation.home.sections.sheet.SheetTransitionEffects
import io.apptolast.paparcar.presentation.home.sections.sheet.rememberSheetMotion
import io.apptolast.paparcar.presentation.home.sections.sheet.rememberSheetPositioning
import io.apptolast.paparcar.presentation.home.sections.sheet.components.HomeReleaseDialog
import io.apptolast.paparcar.presentation.home.sections.sheet.components.homeSheetSpotItemIndex
import io.apptolast.paparcar.presentation.util.rememberOpenExternalNavigation
import io.apptolast.paparcar.presentation.util.zoneIconFor
import io.apptolast.paparcar.ui.components.CenterPinKind
import io.apptolast.paparcar.ui.components.ConfirmationBottomSheet
import io.apptolast.paparcar.ui.components.LocalMapInteracting
import io.apptolast.paparcar.ui.theme.PapMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.error_gps_unavailable
import paparcar.composeapp.generated.resources.error_load_session
import paparcar.composeapp.generated.resources.error_load_spots
import paparcar.composeapp.generated.resources.error_parking_save_failed
import paparcar.composeapp.generated.resources.error_release_parking
import paparcar.composeapp.generated.resources.error_unknown
import paparcar.composeapp.generated.resources.home_det_enabled_confirm
import paparcar.composeapp.generated.resources.home_det_stopped_action
import paparcar.composeapp.generated.resources.home_det_stopped_msg
import paparcar.composeapp.generated.resources.home_spot_reported
import paparcar.composeapp.generated.resources.home_spot_signal_sent
import paparcar.composeapp.generated.resources.home_test_spot_sent
import paparcar.composeapp.generated.resources.home_zone_saved_message
import kotlin.time.Duration.Companion.milliseconds

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
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateLifecycleAware()
    // Live trip render data collected SEPARATELY from [state] — it changes at the GPS fix rate and is
    // NOT in HomeState, so this screen (and the expensive map) don't recompose per fix. We hold the
    // State objects and derive per-field States; nothing here reads `.value` in the main body, so the
    // fix-rate updates flow untouched to the map's isolated scopes. [DRIVE-PUCK-NATIVE-001]
    val tripState = viewModel.tripRender.collectAsStateLifecycleAware()
    val snackbarHostState = remember { SnackbarHostState() }

    // Pre-resolve strings in Composable scope — cannot use stringResource inside LaunchedEffect.
    val msgErrorUnknown = stringResource(Res.string.error_unknown)
    val msgErrorLoadSpots = stringResource(Res.string.error_load_spots)
    val msgErrorLoadSession = stringResource(Res.string.error_load_session)
    val msgErrorReleaseParking = stringResource(Res.string.error_release_parking)
    val msgErrorGpsUnavailable = stringResource(Res.string.error_gps_unavailable)
    val msgErrorParkingSaveFailed = stringResource(Res.string.error_parking_save_failed)
    val msgSpotReported = stringResource(Res.string.home_spot_reported)
    val msgTestSpotSent = stringResource(Res.string.home_test_spot_sent)
    val msgSpotSignalSent = stringResource(Res.string.home_spot_signal_sent)
    val msgZoneSaved = stringResource(Res.string.home_zone_saved_message)
    val msgDetectionEnabled = stringResource(Res.string.home_det_enabled_confirm)
    val msgDetectionStopped = stringResource(Res.string.home_det_stopped_msg)
    val msgDetectionStoppedAction = stringResource(Res.string.home_det_stopped_action)

    // Scope the high-accuracy user-location request to when this screen is actually on-screen: under
    // Compose Navigation the lifecycle owner is Home's back-stack entry, so this is RESUMED only while
    // Home is the current destination AND the app is foreground. [UI-LOC-FOREGROUND-001]
    MapForegroundEffect { active -> viewModel.handleIntent(HomeIntent.SetMapForeground(active)) }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is HomeEffect.ShowError -> {
                    val msg = when (effect.error) {
                        is PaparcarError.Location.ProviderDisabled -> msgErrorGpsUnavailable
                        is PaparcarError.Database.WriteError -> msgErrorReleaseParking
                        is PaparcarError.Parking.ReleaseFailed -> msgErrorReleaseParking
                        is PaparcarError.Network.Unknown -> msgErrorLoadSpots
                        is PaparcarError.Database.Unknown -> msgErrorLoadSession
                        is PaparcarError.Parking.SaveFailed -> msgErrorParkingSaveFailed
                        else -> msgErrorUnknown
                    }
                    snackbarHostState.showSnackbar(msg)
                }

                HomeEffect.SpotReported -> snackbarHostState.showSnackbar(msgSpotReported)
                HomeEffect.TestSpotSent -> snackbarHostState.showSnackbar(msgTestSpotSent)
                HomeEffect.SpotSignalSent -> snackbarHostState.showSnackbar(msgSpotSignalSent)
                HomeEffect.ZoneSaved -> snackbarHostState.showSnackbar(msgZoneSaved)
                HomeEffect.DetectionEnabled -> snackbarHostState.showSnackbar(msgDetectionEnabled)
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
                HomeEffect.RequestLocationPermission -> {}
                // MoveCameraTo needs the HomeUiController which lives inside
                // HomeContent; a dedicated collector down there handles it.
                is HomeEffect.MoveCameraTo -> Unit
            }
        }
    }

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
    )

    state.pendingParkingGps?.let { pending ->
        ConfirmationBottomSheet(
            onConfirm = { viewModel.handleIntent(HomeIntent.ConfirmDetectedParking) },
            onDismiss = { viewModel.handleIntent(HomeIntent.DismissConfirmation) },
            addressLine = state.cameraAddressAndPlace?.displayLine,
            detectionTimestampMs = pending.timestamp,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Content — orchestrates state, math, and the four section composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HomeContent(
    state: HomeState,
    tripState: State<TripUpdate>,
    onIntent: (HomeIntent) -> Unit,
    effects: SharedFlow<HomeEffect>,
    snackbarHostState: SnackbarHostState,
    navProgressState: MutableFloatState,
    bottomPadding: Dp,
    onActivateDetection: (focus: String) -> Unit,
    onAddVehicle: () -> Unit,
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
    val mapInteraction = rememberMapInteractionTracker(uiController)

    // Per-section slices — pure projections built ONCE per state emission. Each section
    // composable receives only its slice, so unrelated state changes stop recomposing it.
    // HomeContent itself keeps the full state (it is the orchestrator). [HOME-ATOMIZE-001 F1]
    val headerSlice = remember(state) { state.toHeaderSlice() }
    val fabsSlice = remember(state) { state.toFabsSlice() }
    val mapSlice = remember(state) { state.toMapSlice() }
    val peekSlice = remember(state) { state.toPeekSlice() }
    val browseSlice = remember(state) { state.toBrowseListSlice() }

    val isParkingSelected = state.isParkingSelected
    val selectedSpotId = state.selectedItemId?.takeIf { !isParkingSelected }
    var spotListExpanded by remember(selectedSpotId) { mutableStateOf(false) }
    var showReleaseDialog by remember { mutableStateOf(false) }
    // The session the release dialog acts on — set from the peek that opened it, so the release
    // targets THAT card, never a ranked fallback. [VEH-ACTIVE-FENCE-001]
    var releaseTargetSessionId by remember { mutableStateOf<String?>(null) }
    val lazyListState = rememberLazyListState()

    // Auto-close the release dialog as soon as the in-flight release finishes
    // (success or failure). Errors are surfaced via snackbar — the dialog has
    // nothing to add once isReleasingParking flips back to false. Tracking the
    // true→false flip avoids the multi-parking pitfall where checking
    // `state.userParking == null` would never be true when other vehicles
    // still have active sessions. [BUG-RELEASE-DIALOG-001] [MULTI-PARKING-001]
    val releaseInFlight = state.isReleasingParking
    var wasReleasing by remember { mutableStateOf(false) }
    LaunchedEffect(releaseInFlight) {
        if (releaseInFlight) {
            wasReleasing = true
        } else if (wasReleasing) {
            wasReleasing = false
            showReleaseDialog = false
        }
    }

    // Initial focus, parking re-frame, driver-follow and MoveCameraTo effects —
    // see [HomeCameraEffects].
    HomeCameraEffects(
        uiController = uiController,
        state = state,
        drivingPuck = trip.puck,
        effects = effects,
    )

    LaunchedEffect(selectedSpotId) {
        val spotId = selectedSpotId ?: return@LaunchedEffect
        val idx = homeSheetSpotItemIndex(browseSlice, spotId)
        if (idx >= 0) lazyListState.animateScrollToItem(idx)
    }

    CompositionLocalProvider(LocalMapInteracting provides mapInteraction.isInteracting) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0),
            containerColor = Color.Transparent,
        ) { scaffoldPadding ->

            HomeReleaseDialogHost(
                visible = showReleaseDialog,
                isReleasing = state.isReleasingParking,
                sessionId = releaseTargetSessionId,
                onIntent = onIntent,
                onDismiss = { showReleaseDialog = false },
            )

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
                val isPureBrowsePeek = state.mode is HomeMode.Browse && !isParkingSelected && selectedSpotId == null
                val positioning = rememberSheetPositioning(
                    containerHeightPx = containerHeightPx,
                    peekHeightPx = peekHeightPx,
                    lazyListState = lazyListState,
                    isPureBrowsePeek = isPureBrowsePeek,
                    // Pin modes / parking selected / spot selected with the list hidden: the peek
                    // handle owns the whole surface, so the sheet must not expand above peek.
                    // [SHEET-DRAG-001]
                    capExpandAtPeek = state.mode !is HomeMode.Browse ||
                        isParkingSelected ||
                        (selectedSpotId != null && !spotListExpanded),
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

                // Reset-to-peek, list auto-expand and nav-progress hoisting — see
                // HomeSheetPositioning.kt. [HOME-ATOMIZE-001 F2]
                SheetTransitionEffects(
                    positioning = positioning,
                    sheetOffsetPx = sheetOffsetPx,
                    mode = state.mode,
                    selectedItemId = state.selectedItemId,
                    isParkingSelected = isParkingSelected,
                    spotListExpanded = spotListExpanded,
                    navProgressState = navProgressState,
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
                    state.selectedItemId == null &&
                    state.mode is HomeMode.Browse
                // Camera FABs (location / car / midpoint) remain reachable in any
                // modal state — the user may want to jump to their car while reviewing
                // a selected spot or confirming a parking pin.
                val fabsVisible = sheetAtPeekLevel

                // Cache the bleed in pixels for use inside layout-phase lambdas
                // (Modifier.layout/offset) where toDp() is not available.
                val mapBleedPx = with(density) { MAP_BOTTOM_BLEED.toPx() }
                val fabGapPx = with(density) { FAB_ABOVE_SHEET_GAP.toPx() }

                val dragSnap = remember(positioning) { HomeSheetSnap(positioning) }

                // O(1) spot lookup keyed by nearbySpots reference equality.
                val spotsById = remember(state.nearbySpots) {
                    state.nearbySpots.associateBy { it.id }
                }
                // Stable lambda — only recreated when the spots map changes.
                val onSpotMarkerClick: (String) -> Unit = remember(spotsById, uiController, motion) {
                    { spotId ->
                        spotsById[spotId]?.let { spot ->
                            onIntent(HomeIntent.SelectItem(spotId))
                            uiController.moveCamera(spot.location.latitude, spot.location.longitude)
                            motion.animateToExpanded()
                        }
                    }
                }

                // Stable lambda — activeSessions read via rememberUpdatedState at call-time.
                val currentActiveSessions = rememberUpdatedState(state.activeSessions)
                val onMyCarMarkerClick: (sessionId: String) -> Unit = remember(uiController, motion) {
                    { sessionId ->
                        currentActiveSessions.value.firstOrNull { it.id == sessionId }?.let { p ->
                            onIntent(HomeIntent.SelectItem(p.id))
                            uiController.moveCamera(p.location.latitude, p.location.longitude)
                            motion.animateToExpanded()
                        }
                    }
                }

                val isPinningMode = state.mode is HomeMode.Reporting ||
                    state.mode is HomeMode.AddingZone ||
                    state.mode is HomeMode.AddingParking

                // Pin variant per mode. All three share the same white-teardrop
                // molde — only the inner silhouette varies (P letter / car
                // glyph / zone icon). Null in Browse → default crosshair.
                val centerPinKind: CenterPinKind? = when (state.mode) {
                    is HomeMode.Reporting -> CenterPinKind.Report
                    is HomeMode.AddingZone -> CenterPinKind.Zone(zoneIconFor(state.addingZoneIconKey))
                    is HomeMode.AddingParking -> CenterPinKind.Parking
                    else -> null
                }

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
                    selectedSessionId = state.selectedItemId.takeIf { isParkingSelected },
                    reportMode = isPinningMode,
                    cameraTarget = uiController.cameraTarget,
                    centerPin = centerPinKind,
                    dimSpots = isPinningMode || state.selectedItemId != null,
                    onSpotClick = onSpotMarkerClick,
                    onMyCarClick = onMyCarMarkerClick,
                    onZoneClick = onZoneClick,
                    onCameraMove = onMapCameraMove,
                    onUserMapGesture = { uiController.onUserMapGesture() },
                    followingDriver = uiController.followingDriver,
                    previewZoneLat = if (isAddingZone) uiController.cameraLat else null,
                    previewZoneLon = if (isAddingZone) uiController.cameraLon else null,
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
                    slice = headerSlice,
                    uiController = uiController,
                    userGpsPoint = state.userGpsPoint,
                    onIntent = onIntent,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding(),
                )

                // ── Right FAB column (utilities) ─────────────────────────────
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
                        .offset {
                            val sheetH = (rawContainerHeightPx - sheetOffsetPx.value)
                                .coerceAtLeast(0f)
                            val halfH = (rawContainerHeightPx - positioning.halfOffsetPx)
                                .coerceAtLeast(0f)
                            // FABs ride above the sheet edge from peek up to the first expand (half),
                            // where they're still visible; past half they clamp so they don't fly
                            // off-screen while fading out toward the deeper expanded. [HOME-SNAP-001]
                            val base = if (sheetOffsetPx.value >= positioning.halfOffsetPx) sheetH else halfH
                            IntOffset(0, -(base + fabGapPx).roundToInt())
                        },
                )

                // ── Bottom sheet ─────────────────────────────────────────────
                val sheetFrame = HomeSheetFrame(
                    containerHeightPx = rawContainerHeightPx,
                    sheetOffsetPx = sheetOffsetPx,
                    dragSnap = dragSnap,
                    lazyListState = lazyListState,
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
                    spotListExpanded = spotListExpanded,
                    onIntent = onIntent,
                    uiController = uiController,
                    onToggleSpotList = { spotListExpanded = !spotListExpanded },
                    onRelease = { sessionId ->
                        releaseTargetSessionId = sessionId
                        showReleaseDialog = true
                    },
                    onNavigateExternal = openExternalNav,
                    onToggle = motion.toggle,
                    onActivateDetection = onActivateDetection,
                    onAddVehicle = onAddVehicle,
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
    // isProgrammaticMove is flipped synchronously by uiController.moveCamera*
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
    LaunchedEffect(state.userGpsPoint) {
        val gps = state.userGpsPoint ?: return@LaunchedEffect
        // Stateful initial focus: frame the parked car (with the user, if close), else centre on the
        // user so nearby free spots reveal around them. One-shot, inside the controller. [FOCUS-001]
        uiController.centerInitialFocus(
            parking = state.userParking?.let { it.location.latitude to it.location.longitude },
            selectedSpot = state.selectedSpot?.let { it.location.latitude to it.location.longitude },
            user = gps.latitude to gps.longitude,
        )
    }

    // Re-frame the car once if its session loads just after the first GPS fix (common race), unless
    // the user already panned by hand — the controller enforces those guards. [FOCUS-002]
    LaunchedEffect(state.userParking?.id) {
        val parking = state.userParking ?: return@LaunchedEffect
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
                uiController.moveCamera(effect.lat, effect.lon, zoom = 15f)
            }
        }
    }
}

@Composable
private fun HomeReleaseDialogHost(
    visible: Boolean,
    isReleasing: Boolean,
    sessionId: String?,
    onIntent: (HomeIntent) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible || sessionId == null) return
    // The id came from the tapped peek. The VM resolves the session by id and derives the
    // release location from it — no coordinates or ranked fallback here. [VEH-ACTIVE-FENCE-001]
    fun release(publishSpot: Boolean) {
        onIntent(HomeIntent.ReleaseParking(sessionId = sessionId, publishSpot = publishSpot))
    }
    HomeReleaseDialog(
        isLoading = isReleasing,
        onDismiss = { if (!isReleasing) onDismiss() },
        onPublishSpot = { release(publishSpot = true) },
        onDeleteOnly = { release(publishSpot = false) },
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
                onSearchQueryChanged = { onIntent(HomeIntent.SearchQueryChanged(it)) },
                onSearchResultClick = { result ->
                    uiController.moveCamera(result.lat, result.lon, zoom = 15f)
                    onIntent(HomeIntent.SelectSearchResult(result))
                },
                onSearchClear = { onIntent(HomeIntent.ClearSearch) },
                onMapTypeSelected = { onIntent(HomeIntent.SetMapType(it)) },
                onSelectZone = { id -> onIntent(HomeIntent.SelectZone(id)) },
                onAddZone = {
                    onIntent(
                        HomeIntent.EnterAddZoneMode(
                            lat = uiController.cameraLat ?: userGpsPoint?.latitude ?: 0.0,
                            lon = uiController.cameraLon ?: userGpsPoint?.longitude ?: 0.0,
                        )
                    )
                },
                onDeleteZone = { id -> onIntent(HomeIntent.DeleteZone(id)) },
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
    spotListExpanded: Boolean,
    onIntent: (HomeIntent) -> Unit,
    uiController: HomeUiController,
    onToggleSpotList: () -> Unit,
    onRelease: (sessionId: String) -> Unit,
    onNavigateExternal: (lat: Double, lon: Double, walking: Boolean) -> Unit,
    onToggle: () -> Unit,
    onActivateDetection: (focus: String) -> Unit,
    onAddVehicle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onAction: (HomeSheetAction) -> Unit = { action ->
        when (action) {
            HomeSheetAction.ToggleSheet -> onToggle()
            HomeSheetAction.ToggleSpotList -> onToggleSpotList()
            is HomeSheetAction.RequestRelease -> onRelease(action.sessionId)
            HomeSheetAction.RequestReportMode -> onIntent(
                HomeIntent.EnterReportMode(
                    lat = uiController.cameraLat ?: state.userGpsPoint?.latitude ?: 0.0,
                    lon = uiController.cameraLon ?: state.userGpsPoint?.longitude ?: 0.0,
                ),
            )
            is HomeSheetAction.MoveCamera -> uiController.moveCamera(action.lat, action.lon)
            is HomeSheetAction.NavigateExternal ->
                onNavigateExternal(action.lat, action.lon, action.walking)
            // Only the CORE tier routes here (Inactive uses the unified EnableAutoDetection
            // intent). Focus the permissions screen on the essential tier. [DET-TOGGLE-001]
            HomeSheetAction.OpenCorePermissions -> onActivateDetection("core")
            HomeSheetAction.AddVehicle -> onAddVehicle()
        }
    }
    HomeBottomSheet(
        peek = peek,
        browse = browse,
        frame = frame,
        browseShowsZoneHeader = browseShowsZoneHeader,
        spotListExpanded = spotListExpanded,
        onIntent = onIntent,
        onAction = onAction,
        modifier = modifier,
    )
}

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
            val puck = drivingPuck.value
            if (puck != null) {
                uiController.resumeDriverFollow(puck.latitude, puck.longitude)
            } else {
                currentUserGpsPoint.value?.let {
                    uiController.moveCamera(it.latitude, it.longitude, zoom = 16f)
                }
            }
        }
    }
    val onMidpoint: () -> Unit = remember(uiController) {
        {
            val parking = currentUserParking.value
            val gps = currentUserGpsPoint.value
            if (parking != null && gps != null) {
                uiController.moveCameraToBounds(
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

