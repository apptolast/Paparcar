package io.apptolast.paparcar.presentation.home

import kotlin.math.absoluteValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.swmansion.kmpmaps.core.MapType
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import io.apptolast.paparcar.ui.components.LocalMapInteracting
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.ui.components.ConfirmationBottomSheet
import io.apptolast.paparcar.presentation.home.components.HomeActionFab
import io.apptolast.paparcar.presentation.home.components.HomeGpsAccuracyBanner
import io.apptolast.paparcar.presentation.home.components.HomeMapFabColumn
import io.apptolast.paparcar.presentation.home.components.HomePeekHandle
import io.apptolast.paparcar.presentation.home.components.HomeSearchBar
import io.apptolast.paparcar.presentation.home.components.MapTypePicker
import io.apptolast.paparcar.presentation.util.rememberOpenExternalNavigation
import io.apptolast.paparcar.presentation.home.components.homeSheetItems
import io.apptolast.paparcar.presentation.home.components.homeSheetSpotItemIndex
import io.apptolast.paparcar.ui.components.PaparcarMapConfig
import io.apptolast.paparcar.ui.components.PaparcarMapView
import io.apptolast.paparcar.domain.error.PaparcarError
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
import paparcar.composeapp.generated.resources.home_cd_map_type
import paparcar.composeapp.generated.resources.home_release_dialog_delete_only
import paparcar.composeapp.generated.resources.home_release_dialog_message
import paparcar.composeapp.generated.resources.home_release_dialog_publish
import paparcar.composeapp.generated.resources.home_release_dialog_title
import paparcar.composeapp.generated.resources.home_spot_reported
import paparcar.composeapp.generated.resources.home_spot_signal_sent
import paparcar.composeapp.generated.resources.home_test_spot_sent
import paparcar.composeapp.generated.resources.settings_map_type_normal
import paparcar.composeapp.generated.resources.settings_map_type_satellite
import paparcar.composeapp.generated.resources.settings_map_type_terrain

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

// Velocity (px/s) required to snap the sheet on fling; below this the sheet stays in place
private const val FLING_SNAP_VELOCITY = 1200f

// Sheet top position when fully expanded ("sheet top at screen top").
private const val FULL_SNAP_OFFSET_PX = 0f

// Fraction of peek offset at/above which the global bottom nav starts hiding.
// Remaps raw sheet progress so the nav disappears well before the sheet is
// fully expanded — responsive feel instead of a linear fade across the drag.
private const val NAV_HIDE_START = 0.65f

// Soft-drag auto-collapse zone: if the user releases the drag past this fraction
// of the peek offset (i.e. very close to the collapsed position), snap fully
// collapsed instead of leaving the sheet floating near the bottom.
private const val SOFT_DRAG_COLLAPSE_ZONE = 0.85f

// The sheet only exposes its TOP edge over the map (sides + bottom hug the
// screen edges, where shadow is clipped). To match the perceived depth of the
// floating search bar / circular FABs — which cast shadow on all four sides —
// the sheet needs a heavier elevation than those 6dp peers. [HOME-DEPTH-001]
private val SHEET_SHADOW_ELEVATION = 12.dp

// ─────────────────────────────────────────────────────────────────────────────
// Root
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    onNavigateToHistory: () -> Unit = {},
    onNavigateToAddFreeSpot: () -> Unit = {},
    navProgressState: MutableFloatState = remember { mutableFloatStateOf(1f) },
    bottomPadding: Dp = 0.dp,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Pre-resolve strings in Composable scope — cannot use stringResource inside LaunchedEffect
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
            }
        }
    }

    HomeContent(
        state = state,
        onIntent = viewModel::handleIntent,
        onNavigateToAddFreeSpot = onNavigateToAddFreeSpot,
        snackbarHostState = snackbarHostState,
        navProgressState = navProgressState,
        bottomPadding = bottomPadding,
    )

    if (state.pendingParkingGps != null) {
        ConfirmationBottomSheet(
            onConfirm = { viewModel.handleIntent(HomeIntent.ConfirmDetectedParking) },
            onDismiss = { viewModel.handleIntent(HomeIntent.DismissConfirmation) },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HomeContent(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    onNavigateToAddFreeSpot: () -> Unit,
    snackbarHostState: SnackbarHostState,
    navProgressState: MutableFloatState,
    bottomPadding: Dp,
) {
    val uiController = rememberHomeUiController()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    // Platform-aware launcher for external navigation (Google Maps on Android,
    // no-op on iOS for now). Captured inside HomeContent so the peek handle's
    // primary action can invoke it directly without round-tripping through the
    // VM. [PEEK-ACTIONS-001]
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
    // never invalidate a composition scope — HomeContent does not recompose
    // per frame while the user is dragging the map.
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
    val isParkingSelected = state.selectedItemId == HomeState.PARKING_ITEM_ID
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

    CompositionLocalProvider(LocalMapInteracting provides isMapInteracting) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0),
        containerColor = Color.Transparent,
    ) { scaffoldPadding ->

        if (showReleaseDialog) {
            AlertDialog(
                onDismissRequest = { showReleaseDialog = false },
                title = { Text(stringResource(Res.string.home_release_dialog_title)) },
                text = { Text(stringResource(Res.string.home_release_dialog_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
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
                    ) {
                        Text(stringResource(Res.string.home_release_dialog_publish))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
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
                    ) {
                        Text(stringResource(Res.string.home_release_dialog_delete_only))
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
            //   - Peek  →  sheet top at (screen − bar − peekHeight).
            //   - Half  →  midpoint between fullSnap and peek.
            //   - Full  →  fullSnapOffsetPx (0 when content overflows, content-fit otherwise).
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
            val fullSnapOffsetPx =
                if (allItemsFit && peekHeightPx > 0f)
                    (containerHeightPx - peekHeightPx - listNaturalHeightPx).coerceAtLeast(0f)
                else FULL_SNAP_OFFSET_PX

            // True midpoint between the two expansion extremes — more accurate than
            // containerHeight/2 when content-aware full snap is in play.
            val halfOffsetPx = (fullSnapOffsetPx + peekOffsetPx) / 2f

            val sheetOffsetPx = remember { Animatable(peekOffsetPx) }
            LaunchedEffect(peekOffsetPx, state.selectedItemId) {
                if (state.selectedItemId == null) {
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
            // FABs sit just above the sheet's current top edge. The sheet is
            // aligned BottomCenter of BoxWithConstraints (whose inner area =
            // rawContainerHeightPx), so its top is at rawContainer - sheetHeight
            // = sheetOffsetPx measured from the container top — hence the FAB
            // bottom inset equals (rawContainer - sheetOffsetPx) + 12dp.
            val fabBottomDp =
                with(density) { (rawContainerHeightPx - sheetOffsetPx.value).toDp() } + 12.dp

            // Map shrinks as the sheet expands — its bottom edge extends 20dp past
            // the sheet top so the sheet's rounded top corners sit on top of the
            // map tiles instead of exposing the dark background behind the curves.
            // The sheet itself is opaque (it does NOT participate in glass mode),
            // so there is no need for the map to render under it.
            val mapHeightDp = with(density) { sheetOffsetPx.value.toDp() } + 20.dp

            // ── Map ──────────────────────────────────────────────────────────
            PaparcarMapView(
                config = PaparcarMapConfig(mapType = state.mapType),
                spots = state.nearbySpots,
                userLocation = state.userGpsPoint,
                parkingLocation = state.userParking?.location,
                selectedSpotId = selectedSpotId,
                reportMode = !sheetExpanded,
                isAnyItemSelected = state.selectedItemId != null,
                isLoading = state.isLoading,
                onSpotClick = { spotId ->
                    state.nearbySpots.find { it.id == spotId }?.let { spot ->
                        onIntent(HomeIntent.SelectItem(spotId))
                        uiController.moveCamera(spot.location.latitude, spot.location.longitude)
                        coroutineScope.launch {
                            sheetOffsetPx.animateTo(
                                halfOffsetPx.coerceIn(fullSnapOffsetPx, peekOffsetPx),
                                SnapSpec,
                            )
                        }
                    }
                },
                onMyCarClick = {
                    state.userParking?.let { p ->
                        onIntent(HomeIntent.SelectItem(HomeState.PARKING_ITEM_ID))
                        uiController.moveCamera(p.location.latitude, p.location.longitude)
                        coroutineScope.launch {
                            sheetOffsetPx.animateTo(
                                halfOffsetPx.coerceIn(fullSnapOffsetPx, peekOffsetPx),
                                SnapSpec,
                            )
                        }
                    }
                },
                onCameraMove = { lat, lon ->
                    uiController.onCameraMoved(lat, lon)
                    onIntent(HomeIntent.CameraPositionChanged(lat, lon))
                    onCameraFrame()
                },
                cameraTarget = uiController.cameraTarget,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(mapHeightDp),
            )

            // ── Floating search bar + action pills + GPS accuracy banner ─────
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    HomeSearchBar(
                        query = state.searchQuery,
                        results = state.searchResults,
                        isActive = state.isSearchActive,
                        isSearching = state.isSearching,
                        onQueryChange = { onIntent(HomeIntent.SearchQueryChanged(it)) },
                        onResultClick = { result ->
                            uiController.moveCamera(result.lat, result.lon, zoom = 15f)
                            onIntent(HomeIntent.SelectSearchResult(result))
                        },
                        onClear = { onIntent(HomeIntent.ClearSearch) },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    // Layers picker: a vertical stack of circular FABs that mirrors
                    // the trigger's shape. The active type is highlighted with a primary
                    // selection ring. [MAP-TYPE-001]
                    MapTypePicker(
                        currentType = state.mapType,
                        onTypeSelected = { onIntent(HomeIntent.SetMapType(it)) },
                    )
                }
                HomeGpsAccuracyBanner(
                    accuracy = state.userGpsPoint?.accuracy,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            // ── Right FAB column (MyLocation + Coche + contextual primary) ───
            AnimatedVisibility(
                visible = sheetOffsetPx.value >= halfOffsetPx,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 14.dp, bottom = fabBottomDp),
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    HomeMapFabColumn(
                        userParking = state.userParking,
                        userGpsPoint = state.userGpsPoint,
                        onMyLocation = {
                            state.userGpsPoint?.let {
                                uiController.moveCamera(it.latitude, it.longitude, zoom = 16f)
                            }
                        },
                        onParkedCar = {
                            state.userParking?.let { p ->
                                onIntent(HomeIntent.SelectItem(HomeState.PARKING_ITEM_ID))
                                uiController.moveCamera(p.location.latitude, p.location.longitude)
                                coroutineScope.launch {
                                    sheetOffsetPx.animateTo(
                                        halfOffsetPx.coerceIn(fullSnapOffsetPx, peekOffsetPx),
                                        SnapSpec,
                                    )
                                }
                            }
                        },
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
                    )
                    Spacer(Modifier.height(10.dp))
                    HomeActionFab(
                        onReportFreeSpot = onNavigateToAddFreeSpot,
                    )
                }
            }

            // ── Bottom sheet ─────────────────────────────────────────────────
            // Instagram-style: sheet height = visible area (bottomBound - top).
            // Uses rawContainerHeightPx (current constraints) so the sheet fills
            // whatever space is actually available — when an item is selected
            // and the AppBottomNavigation hides, the sheet reclaims that area.
            val sheetHeightDp = with(density) {
                (rawContainerHeightPx - sheetOffsetPx.value).coerceAtLeast(0f).toDp()
            }
            // Sheet stays opaque even while the map is being dragged. Glass would
            // require the map to render full-screen behind it (otherwise the
            // "frosted" pass exposes the empty Scaffold background), and there's
            // no UX gain — the sheet's whole purpose is to host content the user
            // is reading. Glass treatment is reserved for the search bar and the
            // map FABs, which sit directly over the map.
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(sheetHeightDp),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                // Lift the top edge above the map tiles with the same depth
                // language as the floating search bar and circular FABs.
                // [HOME-DEPTH-001]
                shadowElevation = SHEET_SHADOW_ELEVATION,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Handle: the only area that drags the sheet
                    Box(
                        modifier = Modifier
                            .onSizeChanged { size ->
                                // Guard 0 transients — see peekHeightPx comment above.
                                if (size.height > 0) peekHeightPx = size.height.toFloat()
                            }
                            .draggable(
                                orientation = Orientation.Vertical,
                                state = rememberDraggableState { delta ->
                                    coroutineScope.launch {
                                        sheetOffsetPx.snapTo(
                                            (sheetOffsetPx.value + delta).coerceIn(
                                                fullSnapOffsetPx,
                                                peekOffsetPx
                                            ),
                                        )
                                    }
                                },
                                onDragStopped = { velocity ->
                                    coroutineScope.launch {
                                        val current = sheetOffsetPx.value
                                        val target = when {
                                            velocity < -FLING_SNAP_VELOCITY -> {
                                                // Fling up: collapsed → half, half → full
                                                if (current > halfOffsetPx) halfOffsetPx else fullSnapOffsetPx
                                            }
                                            velocity > FLING_SNAP_VELOCITY -> {
                                                // Fling down: full → half, half → collapsed
                                                if (current < halfOffsetPx) halfOffsetPx else peekOffsetPx
                                            }
                                            else -> {
                                                // Soft drag: snap to nearest of full/half/peek
                                                val distFull = (current - fullSnapOffsetPx).absoluteValue
                                                val distHalf = (current - halfOffsetPx).absoluteValue
                                                val distPeek = (current - peekOffsetPx).absoluteValue
                                                when {
                                                    distPeek <= distHalf && distPeek <= distFull -> peekOffsetPx
                                                    distHalf <= distFull -> halfOffsetPx
                                                    else -> fullSnapOffsetPx
                                                }
                                            }
                                        }.coerceIn(fullSnapOffsetPx, peekOffsetPx)
                                        sheetOffsetPx.animateTo(target, SnapSpec)
                                    }
                                },
                            ),
                    ) {
                        HomePeekHandle(
                            state = state,
                            onDismiss = { onIntent(HomeIntent.SelectItem(null)) },
                            onRelease = { showReleaseDialog = true },
                            onNavigateExternal = openExternalNav,
                        )
                    }

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .nestedScroll(sheetNestedScroll),
                        contentPadding = PaddingValues(
                            top = 4.dp,
                            // Reserve the AppBottomNavigation height so the last
                            // list row stays visible above the global nav bar.
                            bottom = 16.dp + stableBottomPadding,
                        ),
                    ) {
                        homeSheetItems(
                            state = state,
                            onIntent = onIntent,
                            onCameraMove = { lat, lon -> uiController.moveCamera(lat, lon) },
                            onParkingClick = {
                                onIntent(HomeIntent.SelectItem(HomeState.PARKING_ITEM_ID))
                                state.userParking?.location?.let { loc ->
                                    uiController.moveCamera(loc.latitude, loc.longitude)
                                }
                            },
                            onManualPark = { onIntent(HomeIntent.ManualPark) },
                            onSpotSelect = { _, _, spotId ->
                                onIntent(HomeIntent.SelectItem(spotId))
                            },
                        )
                    }
                }
            }

        }
    }
    } // CompositionLocalProvider
}

