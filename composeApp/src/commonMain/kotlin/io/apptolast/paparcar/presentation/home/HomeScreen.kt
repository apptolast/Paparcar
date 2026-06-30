package io.apptolast.paparcar.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import io.apptolast.paparcar.presentation.util.collectAsStateLifecycleAware
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
import io.apptolast.paparcar.presentation.home.sections.header.HomeHeaderSection
import io.apptolast.paparcar.presentation.home.sections.map.HomeMapFabsLayer
import io.apptolast.paparcar.presentation.home.sections.map.HomeMapSection
import io.apptolast.paparcar.presentation.home.sections.map.components.HomeMonitoringPill
import io.apptolast.paparcar.presentation.home.sections.map.components.HomeReportFab
import io.apptolast.paparcar.presentation.home.model.DetectionUiState
import io.apptolast.paparcar.presentation.home.sections.sheet.HomeBottomSheet
import io.apptolast.paparcar.presentation.home.sections.sheet.HomeSheetSnap
import io.apptolast.paparcar.presentation.home.sections.sheet.components.HomeReleaseDialog
import io.apptolast.paparcar.presentation.home.sections.sheet.components.homeSheetSpotItemIndex
import io.apptolast.paparcar.presentation.util.rememberOpenExternalNavigation
import io.apptolast.paparcar.presentation.util.zoneIconFor
import io.apptolast.paparcar.ui.components.CenterPinKind
import io.apptolast.paparcar.ui.components.ConfirmationBottomSheet
import io.apptolast.paparcar.ui.components.LocalMapInteracting
import io.apptolast.paparcar.ui.theme.PapMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.connectivity_action_blocked_offline
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

private val SnapSpec = tween<Float>(durationMillis = 300, easing = FastOutSlowInEasing)

// Sheet top position when fully expanded ("sheet top at screen top").
private const val FULL_SNAP_OFFSET_PX = 0f

// Fraction of peek offset at/above which the global bottom nav starts hiding.
// Remaps raw sheet progress so the nav disappears well before the sheet is
// fully expanded — responsive feel instead of a linear fade across the drag.
private const val NAV_HIDE_START = 0.65f

// Map's bottom edge extends past the sheet top so the rounded top corners
// sit on opaque map tiles instead of exposing the dark background behind
// the curves.
private val MAP_BOTTOM_BLEED = 20.dp

// FAB inset above the sheet top edge.
private val FAB_ABOVE_SHEET_GAP = 12.dp

// When peekOffsetPx changes by less than this amount and the sheet is at rest,
// snap directly instead of animating — avoids the visible 300ms glide that
// occurs when the peek handle is first measured and peekHeightPx is corrected
// from the SheetPeekHeightInitial estimate to the real measured height.
private val PEEK_LAYOUT_SNAP_TOLERANCE = 64.dp


// Minimum visible sheet height when dragged to its smallest "minimized" state.
// Matches the natural Browse peek (drag pill + CameraLocationRow with title +
// secondary line) so the sheet never collapses below the band the user sees
// in Browse. The Browse peek auto-coerces to this when its measured height is
// smaller; non-Browse peeks treat it as the drag-down floor. [SHEET-MIN-001]
private val SHEET_MIN_VISIBLE_HEIGHT = 96.dp


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
    val msgOfflineBlocked = stringResource(Res.string.connectivity_action_blocked_offline)
    val msgZoneSaved = stringResource(Res.string.home_zone_saved_message)
    val msgDetectionEnabled = stringResource(Res.string.home_det_enabled_confirm)
    val msgDetectionStopped = stringResource(Res.string.home_det_stopped_msg)
    val msgDetectionStoppedAction = stringResource(Res.string.home_det_stopped_action)

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is HomeEffect.ShowError -> {
                    val msg = when (effect.error) {
                        is PaparcarError.Location.ProviderDisabled -> msgErrorGpsUnavailable
                        is PaparcarError.Database.WriteError -> msgErrorReleaseParking
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
                HomeEffect.OfflineActionBlocked -> snackbarHostState.showSnackbar(msgOfflineBlocked)
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
    onIntent: (HomeIntent) -> Unit,
    effects: kotlinx.coroutines.flow.SharedFlow<HomeEffect>,
    snackbarHostState: SnackbarHostState,
    navProgressState: MutableFloatState,
    bottomPadding: Dp,
    onActivateDetection: (focus: String) -> Unit,
    onAddVehicle: () -> Unit,
) {
    val uiController = rememberHomeUiController()
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

    // ── Glass interaction tracking ───────────────────────────────────────────
    // isMapInteracting is the only snapshot state that feeds the glass effect,
    // and it only flips twice per gesture (true on first real drag frame,
    // false once MAP_INTERACTION_IDLE_DELAY_MS elapses without new frames).
    // The idle Job lives in a non-snapshot holder so rapid onCameraMove ticks
    // never invalidate a composition scope.
    var isMapInteracting by remember { mutableStateOf(false) }
    val idleJobHolder = remember { arrayOfNulls<Job>(1) }

    // Clear the programmatic-move flag once the camera animation has settled.
    // isProgrammaticMove is flipped synchronously by uiController.moveCamera*
    // before cameraTarget mutates, so this effect runs after the flag is
    // already true — it only needs to clear it when the animation is done.
    LaunchedEffect(uiController.cameraTarget?.token) {
        if (!uiController.isProgrammaticMove) return@LaunchedEffect
        delay(PROGRAMMATIC_MOVE_GUARD_MS.milliseconds)
        uiController.clearProgrammaticMove()
    }

    val onCameraFrame: () -> Unit = remember(coroutineScope, uiController) {
        {
            if (!uiController.isProgrammaticMove) {
                if (!isMapInteracting) isMapInteracting = true
                idleJobHolder[0]?.cancel()
                idleJobHolder[0] = coroutineScope.launch {
                    delay(MAP_INTERACTION_IDLE_DELAY_MS.milliseconds)
                    isMapInteracting = false
                }
            }
        }
    }

    val isParkingSelected = state.isParkingSelected
    val selectedSpotId = state.selectedItemId?.takeIf { !isParkingSelected }
    var spotListExpanded by remember(selectedSpotId) { mutableStateOf(false) }
    var showReleaseDialog by remember { mutableStateOf(false) }
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
    LaunchedEffect(state.drivingPuck != null) {
        uiController.setDriverFollowActive(state.drivingPuck != null)
    }
    LaunchedEffect(state.drivingPuck) {
        state.drivingPuck?.let { uiController.followDriver(it.latitude, it.longitude) }
    }

    LaunchedEffect(selectedSpotId) {
        val spotId = selectedSpotId ?: return@LaunchedEffect
        val idx = homeSheetSpotItemIndex(state, spotId)
        if (idx >= 0) lazyListState.animateScrollToItem(idx)
    }

    // Dedicated collector for camera-move effects (e.g. zone chip tap).
    // Lives here because uiController is local to this composable.
    LaunchedEffect(Unit) {
        effects.collect { effect ->
            if (effect is HomeEffect.MoveCameraTo) {
                uiController.moveCamera(effect.lat, effect.lon, zoom = 15f)
            }
        }
    }

    CompositionLocalProvider(LocalMapInteracting provides isMapInteracting) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0),
            containerColor = Color.Transparent,
        ) { scaffoldPadding ->

            if (showReleaseDialog) {
                HomeReleaseDialog(
                    isLoading = state.isReleasingParking,
                    onDismiss = { if (!state.isReleasingParking) showReleaseDialog = false },
                    onPublishSpot = {
                        state.userParking?.let { p ->
                            onIntent(
                                HomeIntent.ReleaseParking(
                                    lat = p.location.latitude,
                                    lon = p.location.longitude,
                                    publishSpot = true,
                                ),
                            )
                        }
                    },
                    onDeleteOnly = {
                        state.userParking?.let { p ->
                            onIntent(
                                HomeIntent.ReleaseParking(
                                    lat = p.location.latitude,
                                    lon = p.location.longitude,
                                    publishSpot = false,
                                ),
                            )
                        }
                    },
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
                val peekOffsetPx = (containerHeightPx - peekHeightPx).coerceAtLeast(0f)

                // Minimized snap point — the drag-down floor for non-Browse states.
                // Same fixed height for all states so the modal never collapses
                // below the Browse peek's visible band. For Browse (peekHeightPx ≈
                // SHEET_MIN_VISIBLE_HEIGHT), this coerces to peekOffsetPx so there
                // is no extra drag below peek. [SHEET-MIN-001]
                val sheetMinHeightPx = with(density) { SHEET_MIN_VISIBLE_HEIGHT.toPx() }
                val minimizedOffsetPx = (containerHeightPx - sheetMinHeightPx)
                    .coerceAtLeast(peekOffsetPx)

                // Content-aware full snap: when ALL list items are visible in the current
                // viewport (nothing to scroll) the sheet stops at content height instead of
                // expanding all the way to the screen top and leaving empty space.
                // derivedStateOf isolates the layoutInfo reads so they don't retrigger the
                // whole composition on every scroll frame — only when the boolean flips.
                val allItemsFit by remember {
                    derivedStateOf {
                        lazyListState.layoutInfo.let { info ->
                            info.totalItemsCount > 0 &&
                                info.visibleItemsInfo.size >= info.totalItemsCount
                        }
                    }
                }
                val listNaturalHeightPx by remember {
                    derivedStateOf {
                        if (!allItemsFit) 0f
                        else lazyListState.layoutInfo.let { info ->
                            info.visibleItemsInfo.sumOf { it.size }.toFloat() +
                                info.beforeContentPadding + info.afterContentPadding
                        }
                    }
                }
                val fullSnapOffsetPx = when {
                    // Pin-positioning modes (Reporting / AddingZone / AddingParking) AND
                    // vehicle-selected cap the sheet's UPPER extent at peek height because
                    // the peek handle owns the whole surface — no list to expose above.
                    // The user can still drag DOWN to minimizedOffsetPx (header-only). [SHEET-DRAG-001]
                    state.mode !is HomeMode.Browse -> peekOffsetPx
                    isParkingSelected -> peekOffsetPx
                    // Spot selected with list hidden: no content below the peek
                    // card, so the sheet must not expand above peek height.
                    selectedSpotId != null && !spotListExpanded -> peekOffsetPx
                    // Browse with all items already visible: stop at content
                    // height so the sheet doesn't expose empty space above
                    // the last row when the list is short.
                    allItemsFit && peekHeightPx > 0f ->
                        (containerHeightPx - peekHeightPx - listNaturalHeightPx).coerceAtLeast(0f)
                    else -> FULL_SNAP_OFFSET_PX
                }

                // True midpoint between the two expansion extremes — more accurate than
                // containerHeight/2 when content-aware full snap is in play.
                val halfOffsetPx = (fullSnapOffsetPx + peekOffsetPx) / 2f
                // Overlay hides just before the midpoint snap (15% of the peek→half range).
                val overlayHideThresholdPx = halfOffsetPx + (peekOffsetPx - halfOffsetPx) * 0.15f

                val sheetOffsetPx = remember { Animatable(peekOffsetPx) }
                val peekSnapTolerancePx = with(density) { PEEK_LAYOUT_SNAP_TOLERANCE.toPx() }
                LaunchedEffect(peekOffsetPx, state.selectedItemId, state.mode) {
                    // Entering a non-Browse state resets the sheet to peek (full
                    // peek content visible). The user can then drag DOWN to
                    // minimizedOffsetPx for a header-only view. [SHEET-DRAG-001]
                    val isPinning = state.mode is HomeMode.Reporting ||
                        state.mode is HomeMode.AddingZone ||
                        state.mode is HomeMode.AddingParking
                    val resetToPeek = isPinning || isParkingSelected
                    if (state.selectedItemId == null || resetToPeek) {
                        val correction = kotlin.math.abs(sheetOffsetPx.value - peekOffsetPx)
                        // Snap (not animate) when: small layout correction OR sheet is already at
                        // or below the new peek. The latter covers selection events where the peek
                        // handle grows (Browse→SelectedParking/Spot), shifting peekOffsetPx upward.
                        // Without this guard the LaunchedEffect would fire animateTo and the sheet
                        // would visibly slide up in slow motion to follow the handle.
                        val sheetBelowNewPeek = sheetOffsetPx.value >= peekOffsetPx
                        if (!sheetOffsetPx.isRunning && (correction < peekSnapTolerancePx || sheetBelowNewPeek)) {
                            sheetOffsetPx.snapTo(peekOffsetPx)
                        } else {
                            sheetOffsetPx.animateTo(peekOffsetPx, SnapSpec)
                        }
                    } else if (!sheetOffsetPx.isRunning && sheetOffsetPx.value >= peekOffsetPx) {
                        // Guard isRunning so animateSheetToHalf() (launched on spot/car tap)
                        // is not cancelled by the peekOffsetPx change that occurs when the
                        // peek handle grows to show the selected-item content.
                        sheetOffsetPx.snapTo(peekOffsetPx)
                    }
                }

                // Keep sheet position in sync with spot list expand/collapse.
                LaunchedEffect(spotListExpanded) {
                    if (spotListExpanded) {
                        // Auto-open the sheet so the list is visible immediately.
                        sheetOffsetPx.animateTo(fullSnapOffsetPx, SnapSpec)
                    } else if (sheetOffsetPx.value < peekOffsetPx) {
                        // List collapsed while sheet was expanded — snap back to peek.
                        sheetOffsetPx.animateTo(peekOffsetPx, SnapSpec)
                    }
                }

                // Hoist sheet progress up to the root so the global bottom nav can
                // fade + slide with the drag via graphicsLayer. snapshotFlow keeps
                // this off the composition path — only the layer phase reacts.
                LaunchedEffect(peekOffsetPx) {
                    snapshotFlow {
                        val raw = if (peekOffsetPx > 0f) sheetOffsetPx.value / peekOffsetPx else 1f
                        ((raw - NAV_HIDE_START) / (1f - NAV_HIDE_START)).coerceIn(0f, 1f)
                    }.collect { progress -> navProgressState.floatValue = progress }
                }

                // Instagram-style nested scroll: when the list is scrolled to the very top and
                // the user drags down, collapse the sheet instead of letting the gesture be wasted.
                // Upward gestures are never intercepted — they always scroll the list.
                val currentPeekOffset = rememberUpdatedState(peekOffsetPx)
                val currentFullSnap = rememberUpdatedState(fullSnapOffsetPx)
                val sheetNestedScroll = remember {
                    object : NestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                            val listAtTop = lazyListState.firstVisibleItemIndex == 0 &&
                                lazyListState.firstVisibleItemScrollOffset == 0
                            val peek = currentPeekOffset.value
                            val sheetCanCollapse = sheetOffsetPx.value < peek
                            if (available.y > 0f && listAtTop && sheetCanCollapse) {
                                val newOffset = (sheetOffsetPx.value + available.y)
                                    .coerceIn(currentFullSnap.value, peek)
                                val consumed = newOffset - sheetOffsetPx.value
                                coroutineScope.launch { sheetOffsetPx.snapTo(newOffset) }
                                return Offset(0f, consumed)
                            }
                            return Offset.Zero
                        }
                    }
                }

                // Boolean — equality short-circuits recomposition for downstream readers
                // (e.g. PaparcarMapView.reportMode) on every drag frame.
                // "Expanded" = sheet past the halfway point between peek and full snap.
                // Key on halfOffsetPx so the lambda captures the fresh value whenever
                // the snap geometry changes (e.g. after first peek-height measurement).
                val sheetExpanded by remember(halfOffsetPx) {
                    derivedStateOf { sheetOffsetPx.value < halfOffsetPx }
                }

                // Sheet-position gate: only re-runs on the two frames when the
                // sheet crosses the threshold, not on every drag frame.
                val sheetAtPeekLevel by remember(overlayHideThresholdPx) {
                    derivedStateOf { sheetOffsetPx.value >= overlayHideThresholdPx }
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

                val dragSnap = remember(peekOffsetPx, halfOffsetPx, fullSnapOffsetPx, minimizedOffsetPx) {
                    HomeSheetSnap(
                        peekOffsetPx = peekOffsetPx,
                        halfOffsetPx = halfOffsetPx,
                        fullSnapOffsetPx = fullSnapOffsetPx,
                        minimizedOffsetPx = minimizedOffsetPx,
                        snapSpec = SnapSpec,
                    )
                }

                // rememberUpdatedState wrappers for floats that change when geometry
                // changes — used by lambdas that must be stable but always read the
                // latest snap values at call-time rather than capture-time.
                val currentHalfOffset = rememberUpdatedState(halfOffsetPx)
                val currentMinimizedOffset = rememberUpdatedState(minimizedOffsetPx)
                val currentUserParking = rememberUpdatedState(state.userParking)
                val currentActiveSessions = rememberUpdatedState(state.activeSessions)
                val currentUserGpsPoint = rememberUpdatedState(state.userGpsPoint)
                val currentDrivingPuck = rememberUpdatedState(state.drivingPuck)

                // Stable lambda — remember(coroutineScope, sheetOffsetPx) so the
                // object identity is preserved across recompositions; snap-target
                // floats are read from rememberUpdatedState at call-time.
                val animateSheetToHalf: () -> Unit = remember(coroutineScope, sheetOffsetPx) {
                    {
                        coroutineScope.launch {
                            sheetOffsetPx.animateTo(
                                currentHalfOffset.value.coerceIn(
                                    currentFullSnap.value,
                                    currentPeekOffset.value,
                                ),
                                SnapSpec,
                            )
                        }
                    }
                }

                // Toggles the sheet between peek and the adjacent snap. The
                // "adjacent" snap depends on the current state:
                //  - Browse with a list (half < peek):  peek ↔ half
                //  - Non-Browse (half == peek, no expansion above):  peek ↔ minimized
                // So tap-from-peek opens upward in Browse and downward in pin /
                // selection states — matching the user's directional expectation
                // for each modal. [SHEET-TAP-001]
                val toggleSheet: () -> Unit = remember(coroutineScope, sheetOffsetPx) {
                    {
                        coroutineScope.launch {
                            val current = sheetOffsetPx.value
                            val peek = currentPeekOffset.value
                            val minimized = currentMinimizedOffset.value
                            val half = currentHalfOffset.value
                                .coerceIn(currentFullSnap.value, peek)
                            val canExpandAbovePeek = half < peek - 1f
                            val canCollapseBelowPeek = minimized > peek + 1f
                            val target = when {
                                // Above peek → collapse to peek.
                                current < peek - 1f -> peek
                                // Below peek → expand to peek.
                                current > peek + 1f -> peek
                                // At peek with a half snap available → go to half.
                                canExpandAbovePeek -> half
                                // At peek with no expansion but a minimized snap → collapse.
                                canCollapseBelowPeek -> minimized
                                else -> peek
                            }
                            sheetOffsetPx.animateTo(target, SnapSpec)
                        }
                    }
                }

                // O(1) spot lookup keyed by nearbySpots reference equality.
                val spotsById = remember(state.nearbySpots) {
                    state.nearbySpots.associateBy { it.id }
                }
                // Stable lambda — only recreated when the spots map changes.
                val onSpotMarkerClick: (String) -> Unit = remember(spotsById, uiController) {
                    { spotId ->
                        spotsById[spotId]?.let { spot ->
                            onIntent(HomeIntent.SelectItem(spotId))
                            uiController.moveCamera(spot.location.latitude, spot.location.longitude)
                            animateSheetToHalf()
                        }
                    }
                }

                // Stable lambda — activeSessions read via rememberUpdatedState at call-time.
                val onMyCarMarkerClick: (sessionId: String) -> Unit = remember(uiController) {
                    { sessionId ->
                        currentActiveSessions.value.firstOrNull { it.id == sessionId }?.let { p ->
                            onIntent(HomeIntent.SelectItem(p.id))
                            uiController.moveCamera(p.location.latitude, p.location.longitude)
                            animateSheetToHalf()
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
                val onMapCameraMove: (Double, Double) -> Unit = remember(uiController) {
                    { lat, lon ->
                        uiController.onCameraMoved(lat, lon)
                        onIntent(HomeIntent.CameraPositionChanged(lat, lon))
                        onCameraFrame()
                    }
                }

                // ── Map ──────────────────────────────────────────────────────
                // Height is set via Modifier.layout so sheetOffsetPx is read in
                // the layout phase only — dragging never triggers recomposition here.
                val isAddingZone = state.mode is HomeMode.AddingZone
                HomeMapSection(
                    state = state,
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
                    previewZoneRadius = state.addingZoneRadius,
                    previewZoneIsPrivate = state.addingZoneIsPrivate,
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

                // ── Floating search header + ephemeral Monitoring pill ───────
                // The detection ACTION surface no longer lives here — it is now a section
                // inside the bottom sheet, under the address header. Only the transient
                // "following your trip" pill floats over the map (centred under the search
                // bar) while a trip is being tracked. The wrapping column owns the status-bar
                // inset (HomeHeaderSection no longer applies it). [DET-READY-001h]
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding(),
                ) {
                    AnimatedVisibility(
                        visible = overlayVisible,
                        enter = fadeIn(PapMotion.medium()),
                        exit = fadeOut(PapMotion.medium()),
                    ) {
                        HomeHeaderSection(
                            state = state,
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
                                        lat = uiController.cameraLat ?: state.userGpsPoint?.latitude ?: 0.0,
                                        lon = uiController.cameraLon ?: state.userGpsPoint?.longitude ?: 0.0,
                                    )
                                )
                            },
                            onDeleteZone = { id -> onIntent(HomeIntent.DeleteZone(id)) },
                            onEditZone = { id -> onIntent(HomeIntent.EnterEditZoneMode(id)) },
                            modifier = Modifier,
                        )
                    }
                }

                // Stable event lambdas for FABs layer and report button.
                // GPS and parking are read via rememberUpdatedState at call-time.
                val onMyLocation: () -> Unit = remember(uiController) {
                    {
                        // Mid-trip, "locate me" means the moving car: re-engage driver-follow and snap onto
                        // the puck (paused earlier by a map gesture). Otherwise recentre on GPS. [FOLLOW-001]
                        val puck = currentDrivingPuck.value
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
                val onReportFabClick: () -> Unit = remember(uiController) {
                    {
                        onIntent(
                            HomeIntent.EnterReportMode(
                                lat = uiController.cameraLat ?: currentUserGpsPoint.value?.latitude ?: 0.0,
                                lon = uiController.cameraLon ?: currentUserGpsPoint.value?.longitude ?: 0.0,
                            )
                        )
                    }
                }

                // ── Right FAB column (utilities) ─────────────────────────────
                // Bottom positioning is done in the layout phase via Modifier.offset
                // so dragging never triggers recomposition of this subtree.
                HomeMapFabsLayer(
                    state = state,
                    visible = fabsVisible,
                    onMyLocation = onMyLocation,
                    onParkedCar = {
                        val sessions = state.activeSessions
                        if (sessions.isNotEmpty()) {
                            val currentSelected = state.selectedSession
                            val target = if (currentSelected != null) {
                                val idx = sessions.indexOfFirst { it.id == currentSelected.id }
                                sessions[(idx + 1) % sessions.size]
                            } else {
                                sessions.first()
                            }
                            onMyCarMarkerClick(target.id)
                        }
                    },
                    onMidpoint = onMidpoint,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset {
                            val sheetH = (rawContainerHeightPx - sheetOffsetPx.value)
                                .coerceAtLeast(0f)
                            val peekH = (rawContainerHeightPx - peekOffsetPx)
                                .coerceAtLeast(0f)
                            // Clamp to peek when expanded so FABs don't fly off-screen.
                            val base = if (sheetOffsetPx.value >= halfOffsetPx) sheetH else peekH
                            IntOffset(0, -(base + fabGapPx).roundToInt())
                        },
                )

                // ── Left FAB (report a free spot — entry to Reporting mode) ──
                AnimatedVisibility(
                    visible = overlayVisible,
                    // Left-edge control slides out to the left (mirrors the right FAB column).
                    enter = fadeIn(PapMotion.medium()) + slideInHorizontally(PapMotion.medium()) { -it / 2 },
                    exit = fadeOut(PapMotion.medium()) + slideOutHorizontally(PapMotion.medium()) { -it / 2 },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 14.dp)
                        .offset {
                            val sheetH = (rawContainerHeightPx - sheetOffsetPx.value)
                                .coerceAtLeast(0f)
                            IntOffset(0, -(sheetH + fabGapPx).roundToInt())
                        },
                ) {
                    HomeReportFab(onClick = onReportFabClick)
                }

                // ── "Following your trip" pill — REPLACES the GPS FAB during a trip: anchored bottom-end
                // where the location FAB sits, and tappable to recentre on the moving car (resume follow).
                // The report FAB stays at bottom-start, so there's no overlap. Hidden when the sheet
                // engages; lifts above the sheet with the same offset as the FABs. [FOLLOW-001]
                HomeMonitoringPill(
                    visible = state.detectionUiState == DetectionUiState.Monitoring && overlayVisible,
                    onClick = onMyLocation,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 14.dp)
                        .offset {
                            val sheetH = (rawContainerHeightPx - sheetOffsetPx.value)
                                .coerceAtLeast(0f)
                            IntOffset(0, -(sheetH + fabGapPx).roundToInt())
                        },
                )

                // ── Bottom sheet ─────────────────────────────────────────────
                HomeBottomSheet(
                    state = state,
                    containerHeightPx = rawContainerHeightPx,
                    sheetOffsetPx = sheetOffsetPx,
                    dragSnap = dragSnap,
                    lazyListState = lazyListState,
                    sheetNestedScroll = sheetNestedScroll,
                    bottomContentPadding = stableBottomPadding,
                    coroutineScope = coroutineScope,
                    onPeekHeightChanged = { h ->
                        // EXACT per-state height (no hysteresis): peekOffset = container - peekHeight,
                        // so the peek header's bottom edge — and its divider — lands flush on the
                        // bottom-nav divider, with no dp of gap, in every state. Each state keeps its
                        // own natural height (Browse short, SelectedCar taller). The height is whole-px
                        // already, so there's no sub-pixel churn. [BUG-PEEK-DIVIDER-ALIGN]
                        if (h != peekHeightPx) peekHeightPx = h
                    },
                    onIntent = onIntent,
                    onParkingClick = { parking ->
                        onIntent(HomeIntent.SelectItem(parking.id))
                        uiController.moveCamera(parking.location.latitude, parking.location.longitude)
                    },
                    onParkVehicle = { vehicleId ->
                        // Per-vehicle "Aparcar" pill — enter AddingParking pre-centred
                        // on the user's current GPS and tagged with the chosen
                        // vehicleId so ConfirmParkingUseCase persists the session
                        // for that specific car instead of the default. [MULTI-PARKING-001]
                        onIntent(
                            HomeIntent.EnterAddParkingMode(
                                initialGps = state.userGpsPoint,
                                targetVehicleId = vehicleId,
                            ),
                        )
                    },
                    onMoveParkingLocation = {
                        // "Mover ubicación" button on the parking peek — enter
                        // AddingParking pre-centred on the SELECTED session
                        // (not just the first active one) and tagged with its id
                        // so the confirm updates the row in place via
                        // UpdateParkingLocationUseCase. [MULTI-PARKING-001]
                        state.selectedSession?.let { parking ->
                            onIntent(
                                HomeIntent.EnterAddParkingMode(
                                    initialGps = parking.location,
                                    editingParkingId = parking.id,
                                ),
                            )
                        }
                    },
                    spotListExpanded = spotListExpanded,
                    onToggleSpotList = { spotListExpanded = !spotListExpanded },
                    onSpotSelect = { _, _, spotId -> onIntent(HomeIntent.SelectItem(spotId)) },
                    onCameraMove = { lat, lon -> uiController.moveCamera(lat, lon) },
                    onEnterReportMode = {
                        onIntent(
                            HomeIntent.EnterReportMode(
                                lat = uiController.cameraLat ?: state.userGpsPoint?.latitude ?: 0.0,
                                lon = uiController.cameraLon ?: state.userGpsPoint?.longitude ?: 0.0,
                            )
                        )
                    },
                    onRelease = { showReleaseDialog = true },
                    onNavigateExternal = openExternalNav,
                    onToggle = toggleSheet,
                    // Detection surface actions (reuses existing nav + AddingParking flow).
                    onDetectionAddVehicle = onAddVehicle,
                    // Only the CORE block still routes here (Inactive uses the unified EnableAutoDetection
                    // intent). Focus the permissions screen on the essential tier. [DET-TOGGLE-001]
                    onDetectionOpenPermissions = { onActivateDetection("core") },
                    onDetectionMarkSpot = {
                        val markVehicleId = state.vehicles.firstOrNull { it.isActive }?.id
                            ?: state.vehicles.firstOrNull()?.id
                        onIntent(
                            HomeIntent.EnterAddParkingMode(
                                initialGps = state.userGpsPoint,
                                targetVehicleId = markVehicleId,
                            ),
                        )
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

