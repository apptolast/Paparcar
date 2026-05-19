package io.apptolast.paparcar.presentation.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.error.PaparcarError
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


// ─────────────────────────────────────────────────────────────────────────────
// Root
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    onNavigateToHistory: () -> Unit = {},
    navProgressState: MutableFloatState = remember { mutableFloatStateOf(1f) },
    bottomPadding: Dp = 0.dp,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
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
                is HomeEffect.NavigateToHistory -> onNavigateToHistory()
                HomeEffect.RequestLocationPermission -> {}
                // MoveCameraTo needs the HomeUiController which lives inside
                // HomeContent; a dedicated collector down there handles it.
                is HomeEffect.MoveCameraTo -> Unit
            }
        }
    }

    HomeContent(
        state = state,
        onIntent = viewModel::handleIntent,
        effects = viewModel.effect,
        snackbarHostState = snackbarHostState,
        navProgressState = navProgressState,
        bottomPadding = bottomPadding,
    )

    state.pendingParkingGps?.let { pending ->
        ConfirmationBottomSheet(
            onConfirm = { viewModel.handleIntent(HomeIntent.ConfirmDetectedParking) },
            onDismiss = { viewModel.handleIntent(HomeIntent.DismissConfirmation) },
            addressLine = state.cameraLocationInfo?.displayLine,
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
    var showReleaseDialog by remember { mutableStateOf(false) }
    val lazyListState = rememberLazyListState()

    LaunchedEffect(state.userGpsPoint) {
        state.userGpsPoint?.let { uiController.onUserLocationAvailable(it.latitude, it.longitude) }
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
                    onDismiss = { showReleaseDialog = false },
                    onPublishSpot = {
                        showReleaseDialog = false
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
                        showReleaseDialog = false
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
                    // expose below. Spot-selected is intentionally NOT
                    // locked: the user may want to compare with the list.
                    state.mode !is HomeMode.Browse -> peekOffsetPx
                    isParkingSelected -> peekOffsetPx
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

                val sheetOffsetPx = remember { Animatable(peekOffsetPx) }
                LaunchedEffect(peekOffsetPx, state.selectedItemId, state.mode) {
                    // Pin-positioning modes (Reporting / AddingZone) and the
                    // selected-vehicle state both lock the sheet to peek height
                    // because the peek handle hosts the entire state surface.
                    // Browse-with-no-selection also rests at peek.
                    val isPinning = state.mode is HomeMode.Reporting ||
                        state.mode is HomeMode.AddingZone
                    val lockToPeek = isPinning || isParkingSelected
                    if (state.selectedItemId == null || lockToPeek) {
                        sheetOffsetPx.animateTo(peekOffsetPx, SnapSpec)
                    } else if (sheetOffsetPx.value >= peekOffsetPx) {
                        sheetOffsetPx.snapTo(peekOffsetPx)
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
                val sheetExpanded by remember {
                    derivedStateOf { sheetOffsetPx.value < halfOffsetPx }
                }

                // Derived sizing: FAB sits just above the sheet's current top edge;
                // map extends slightly past the sheet top so its rounded corners
                // sit on opaque tiles; sheet height fills the rest below its top.
                val fabBottomDp =
                    with(density) { (rawContainerHeightPx - sheetOffsetPx.value).toDp() } +
                        FAB_ABOVE_SHEET_GAP
                val mapHeightDp = with(density) { sheetOffsetPx.value.toDp() } + MAP_BOTTOM_BLEED
                val sheetHeightDp = with(density) {
                    (rawContainerHeightPx - sheetOffsetPx.value).coerceAtLeast(0f).toDp()
                }

                val dragSnap = remember(peekOffsetPx, halfOffsetPx, fullSnapOffsetPx) {
                    HomeSheetSnap(
                        peekOffsetPx = peekOffsetPx,
                        halfOffsetPx = halfOffsetPx,
                        fullSnapOffsetPx = fullSnapOffsetPx,
                        snapSpec = SnapSpec,
                    )
                }

                // Shared action that selects an item and animates the sheet to its
                // half-snap to keep the map and the selected card visible at once.
                val animateSheetToHalf: () -> Unit = {
                    coroutineScope.launch {
                        sheetOffsetPx.animateTo(
                            halfOffsetPx.coerceIn(fullSnapOffsetPx, peekOffsetPx),
                            SnapSpec,
                        )
                    }
                }

                // O(1) spot lookup keyed by nearbySpots reference equality.
                // Saves the O(n) `nearbySpots.find { }` scan that fired on
                // every map-marker tap. The lambda itself isn't memoized
                // because it captures `animateSheetToHalf` (a freshly
                // created lambda each compose) — wrapping it in remember
                // would freeze a stale snap-target.
                val spotsById = remember(state.nearbySpots) {
                    state.nearbySpots.associateBy { it.id }
                }
                val onSpotMarkerClick: (String) -> Unit = { spotId ->
                    spotsById[spotId]?.let { spot ->
                        onIntent(HomeIntent.SelectItem(spotId))
                        uiController.moveCamera(spot.location.latitude, spot.location.longitude)
                        animateSheetToHalf()
                    }
                }

                val onMyCarMarkerClick: () -> Unit = {
                    state.userParking?.let { p ->
                        onIntent(HomeIntent.SelectItem(HomeState.PARKING_ITEM_ID))
                        uiController.moveCamera(p.location.latitude, p.location.longitude)
                        animateSheetToHalf()
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

                // ── Map ──────────────────────────────────────────────────────
                HomeMapSection(
                    state = state,
                    selectedSpotId = selectedSpotId,
                    isMyCarSelected = isParkingSelected,
                    // Switch the marker stroke style to "report" while in any
                    // pin-positioning mode (Reporting / AddingZone). Keeps
                    // Browse cleanly off the report visual.
                    reportMode = isPinningMode,
                    cameraTarget = uiController.cameraTarget,
                    mapHeightDp = mapHeightDp,
                    centerPin = centerPinKind,
                    // Dim non-focus markers across all focus states. Selected
                    // items bypass the dim pass (selected spot via SELECTED
                    // contentId, parking via isMyCarSelected).
                    dimSpots = isPinningMode || state.selectedItemId != null,
                    onSpotClick = onSpotMarkerClick,
                    onMyCarClick = onMyCarMarkerClick,
                    onZoneClick = { zoneId -> onIntent(HomeIntent.SelectZone(zoneId)) },
                    onCameraMove = { lat, lon ->
                        uiController.onCameraMoved(lat, lon)
                        onIntent(HomeIntent.CameraPositionChanged(lat, lon))
                        onCameraFrame()
                    },
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                // ── Floating search bar + map-type picker + GPS banner ───────
                HomeHeaderSection(
                    state = state,
                    onSearchQueryChanged = { onIntent(HomeIntent.SearchQueryChanged(it)) },
                    onSearchResultClick = { result ->
                        uiController.moveCamera(result.lat, result.lon, zoom = 15f)
                        onIntent(HomeIntent.SelectSearchResult(result))
                    },
                    onSearchClear = { onIntent(HomeIntent.ClearSearch) },
                    onMapTypeSelected = { onIntent(HomeIntent.SetMapType(it)) },
                    modifier = Modifier.align(Alignment.TopStart),
                )

                // ── Right FAB column (utilities) ─────────────────────────────
                HomeMapFabsLayer(
                    state = state,
                    // Hidden in any pin-positioning mode so the user focuses
                    // on the centre pin without competing controls.
                    visible = !isPinningMode && sheetOffsetPx.value >= halfOffsetPx,
                    bottomInset = fabBottomDp,
                    onMyLocation = {
                        state.userGpsPoint?.let {
                            uiController.moveCamera(it.latitude, it.longitude, zoom = 16f)
                        }
                    },
                    onParkedCar = onMyCarMarkerClick,
                    onMidpoint = {
                        val parking = state.userParking
                        val gps = state.userGpsPoint
                        if (parking != null && gps != null) {
                            uiController.moveCameraToBounds(
                                lat1 = parking.location.latitude,
                                lon1 = parking.location.longitude,
                                lat2 = gps.latitude,
                                lon2 = gps.longitude,
                            )
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomEnd),
                )

                // ── Left FAB (report a free spot — entry to Reporting mode) ──
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isPinningMode && sheetOffsetPx.value >= halfOffsetPx,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 14.dp, bottom = fabBottomDp),
                ) {
                    HomeReportFab(
                        onClick = { onIntent(HomeIntent.EnterReportMode) },
                    )
                }

                // ── Bottom sheet ─────────────────────────────────────────────
                HomeBottomSheet(
                    state = state,
                    sheetHeightDp = sheetHeightDp,
                    sheetOffsetPx = sheetOffsetPx,
                    dragSnap = dragSnap,
                    lazyListState = lazyListState,
                    sheetNestedScroll = sheetNestedScroll,
                    bottomContentPadding = stableBottomPadding,
                    coroutineScope = coroutineScope,
                    onPeekHeightChanged = { h -> peekHeightPx = h },
                    onIntent = onIntent,
                    onParkingClick = {
                        onIntent(HomeIntent.SelectItem(HomeState.PARKING_ITEM_ID))
                        state.userParking?.location?.let { loc ->
                            uiController.moveCamera(loc.latitude, loc.longitude)
                        }
                    },
                    onManualPark = {
                        // Empty-state CTA — enter AddingParking pre-centred on
                        // the user's current GPS. Confirming with no drag is
                        // equivalent to the old snap-to-GPS behaviour, but the
                        // user can reposition the pin first.
                        onIntent(HomeIntent.EnterAddParkingMode(initialGps = state.userGpsPoint))
                    },
                    onMoveParkingLocation = {
                        // "Mover ubicación" button on the parking peek — enter
                        // AddingParking pre-centred on the existing parking
                        // and tagged with its id so the confirm updates the
                        // row in place (UpdateParkingLocationUseCase) instead
                        // of creating a new session.
                        state.userParking?.let { parking ->
                            onIntent(
                                HomeIntent.EnterAddParkingMode(
                                    initialGps = parking.location,
                                    editingParkingId = parking.id,
                                ),
                            )
                        }
                    },
                    onSpotSelect = { _, _, spotId -> onIntent(HomeIntent.SelectItem(spotId)) },
                    onCameraMove = { lat, lon -> uiController.moveCamera(lat, lon) },
                    onRelease = { showReleaseDialog = true },
                    onNavigateExternal = openExternalNav,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}
