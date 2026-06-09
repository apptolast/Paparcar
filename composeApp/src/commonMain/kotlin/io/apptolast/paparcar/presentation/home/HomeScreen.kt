package io.apptolast.paparcar.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
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
import io.apptolast.paparcar.presentation.util.PaparcarBackHandler
import io.apptolast.paparcar.presentation.home.sections.header.HomeHeaderSection
import io.apptolast.paparcar.presentation.home.sections.map.HomeMapFabsLayer
import io.apptolast.paparcar.presentation.home.sections.map.HomeMapSection
import io.apptolast.paparcar.presentation.home.sections.map.components.HomeReportFab
import io.apptolast.paparcar.presentation.home.sections.sheet.HomeBottomSheet
import io.apptolast.paparcar.presentation.home.sections.sheet.HomeSheetSnap
import io.apptolast.paparcar.presentation.home.sections.sheet.components.HomeReleaseDialog
import io.apptolast.paparcar.presentation.home.sections.sheet.components.homeSheetSpotItemIndex
import io.apptolast.paparcar.presentation.util.rememberOpenExternalNavigation
import io.apptolast.paparcar.presentation.util.zoneIconFor
import io.apptolast.paparcar.presentation.util.zoneIconOutlinedFor
import io.apptolast.paparcar.ui.components.CenterPinKind
import io.apptolast.paparcar.ui.components.ConfirmationBottomSheet
import io.apptolast.paparcar.ui.components.LocalMapInteracting
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
import paparcar.composeapp.generated.resources.home_spot_reported
import paparcar.composeapp.generated.resources.home_spot_signal_sent
import paparcar.composeapp.generated.resources.home_test_spot_sent
import paparcar.composeapp.generated.resources.home_zone_saved_message

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


// ─────────────────────────────────────────────────────────────────────────────
// Root
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    navProgressState: MutableFloatState = remember { mutableFloatStateOf(1f) },
    bottomPadding: Dp = 0.dp,
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

    // System back navigates non-Browse states to Browse (silent discard).
    // Disabled in Browse so the back press still bubbles up to the host nav
    // (BottomNav / activity finish). [SHEET-BACKNAV-001]
    HomeBackNavigation(state = state, onIntent = onIntent)

    HomeContent(
        state = state,
        onIntent = onIntent,
        effects = viewModel.effect,
        snackbarHostState = snackbarHostState,
        navProgressState = navProgressState,
        bottomPadding = bottomPadding,
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
    var lastKnownNavHeight by remember { mutableStateOf(0.dp) }
    if (bottomPadding > 0.dp && bottomPadding != lastKnownNavHeight) {
        lastKnownNavHeight = bottomPadding
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
        delay(PROGRAMMATIC_MOVE_GUARD_MS)
        uiController.clearProgrammaticMove()
    }

    val onCameraFrame: () -> Unit = remember(coroutineScope, uiController) {
        {
            if (!uiController.isProgrammaticMove) {
                if (!isMapInteracting) isMapInteracting = true
                idleJobHolder[0]?.cancel()
                idleJobHolder[0] = coroutineScope.launch {
                    delay(MAP_INTERACTION_IDLE_DELAY_MS)
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

    LaunchedEffect(state.userParking, state.isReleasingParking) {
        if (state.userParking == null && !state.isReleasingParking) showReleaseDialog = false
    }

    LaunchedEffect(state.userGpsPoint) {
        val gps = state.userGpsPoint ?: return@LaunchedEffect
        val parking = state.userParking
        val spot = state.selectedSpot
        when {
            parking != null -> uiController.onUserLocationAvailable(parking.location.latitude, parking.location.longitude)
            spot != null -> uiController.onUserLocationAvailable(spot.location.latitude, spot.location.longitude)
            else -> uiController.onUserLocationAvailable(gps.latitude, gps.longitude)
        }
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
                    // Pin-positioning modes (Reporting / AddingZone) AND
                    // vehicle-selected lock the sheet to peek height because
                    // the peek handle owns the whole surface — no list to
                    // expose below.
                    state.mode !is HomeMode.Browse -> peekOffsetPx
                    isParkingSelected -> peekOffsetPx
                    // Zone selected: peek shows zone detail card, no list below.
                    state.selectedZoneId != null -> peekOffsetPx
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
                LaunchedEffect(peekOffsetPx, state.selectedItemId, state.selectedZoneId, state.mode) {
                    // Pin-positioning modes (Reporting / AddingZone) and the
                    // selected-vehicle state both lock the sheet to peek height
                    // because the peek handle hosts the entire state surface.
                    // Browse-with-no-selection also rests at peek.
                    val isPinning = state.mode is HomeMode.Reporting ||
                        state.mode is HomeMode.AddingZone
                    val lockToPeek = isPinning || isParkingSelected || state.selectedZoneId != null
                    if (state.selectedItemId == null || lockToPeek) {
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

                val dragSnap = remember(peekOffsetPx, halfOffsetPx, fullSnapOffsetPx) {
                    HomeSheetSnap(
                        peekOffsetPx = peekOffsetPx,
                        halfOffsetPx = halfOffsetPx,
                        fullSnapOffsetPx = fullSnapOffsetPx,
                        snapSpec = SnapSpec,
                    )
                }

                // rememberUpdatedState wrappers for floats that change when geometry
                // changes — used by lambdas that must be stable but always read the
                // latest snap values at call-time rather than capture-time.
                val currentHalfOffset = rememberUpdatedState(halfOffsetPx)
                val currentUserParking = rememberUpdatedState(state.userParking)
                val currentActiveSessions = rememberUpdatedState(state.activeSessions)
                val currentUserGpsPoint = rememberUpdatedState(state.userGpsPoint)

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

                // Toggles the sheet between peek (collapsed) and half-expanded.
                // At peek → animate to half; at or above half → collapse to peek.
                val toggleSheet: () -> Unit = remember(coroutineScope, sheetOffsetPx) {
                    {
                        coroutineScope.launch {
                            // > (strict): when the sheet is exactly at half, the
                            // next tap must collapse, not re-animate to the same spot.
                            val target = if (sheetOffsetPx.value > currentHalfOffset.value) {
                                currentHalfOffset.value.coerceIn(currentFullSnap.value, currentPeekOffset.value)
                            } else {
                                currentPeekOffset.value
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
                    is HomeMode.AddingZone -> CenterPinKind.Zone(zoneIconOutlinedFor(state.addingZoneIconKey))
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
                    isMyCarSelected = isParkingSelected,
                    reportMode = isPinningMode,
                    cameraTarget = uiController.cameraTarget,
                    centerPin = centerPinKind,
                    dimSpots = isPinningMode || state.selectedItemId != null,
                    onSpotClick = onSpotMarkerClick,
                    onMyCarClick = onMyCarMarkerClick,
                    onZoneClick = onZoneClick,
                    onCameraMove = onMapCameraMove,
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

                // ── Floating search bar + map-type picker + GPS banner ───────
                AnimatedVisibility(
                    visible = overlayVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopStart),
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

                // Stable event lambdas for FABs layer and report button.
                // GPS and parking are read via rememberUpdatedState at call-time.
                val onMyLocation: () -> Unit = remember(uiController) {
                    {
                        currentUserGpsPoint.value?.let {
                            uiController.moveCamera(it.latitude, it.longitude, zoom = 16f)
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
                    enter = fadeIn(),
                    exit = fadeOut(),
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
                    onPeekHeightChanged = { h -> peekHeightPx = h },
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
                    onZoneDismiss = { onIntent(HomeIntent.DismissZone) },
                    onEditZone = { id -> onIntent(HomeIntent.EnterEditZoneMode(id)) },
                    onDeleteZone = { id -> onIntent(HomeIntent.DeleteZone(id)) },
                    onToggle = toggleSheet,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// System back wiring
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Intercepts the system back gesture/key whenever Home is in a non-Browse
 * state (pin-positioning mode or any selection peek). Tapping back unwinds
 * exactly one level — same target as the peek's back arrow. Discards in-progress
 * input silently (e.g. half-filled Reporting/AddingZone form). [SHEET-BACKNAV-001]
 *
 * Disabled in Browse so the back press still bubbles up to the host nav
 * (BottomNav back behaviour / activity finish).
 */
@Composable
private fun HomeBackNavigation(state: HomeState, onIntent: (HomeIntent) -> Unit) {
    val backAction: (() -> Unit)? = when {
        state.mode is HomeMode.Reporting -> { { onIntent(HomeIntent.ExitReportMode) } }
        state.mode is HomeMode.AddingZone -> { { onIntent(HomeIntent.ExitAddZoneMode) } }
        state.mode is HomeMode.AddingParking -> { { onIntent(HomeIntent.ExitAddParkingMode) } }
        state.selectedItemId != null -> { { onIntent(HomeIntent.SelectItem(null)) } }
        state.selectedZoneId != null -> { { onIntent(HomeIntent.DismissZone) } }
        else -> null
    }
    PaparcarBackHandler(enabled = backAction != null) { backAction?.invoke() }
}
