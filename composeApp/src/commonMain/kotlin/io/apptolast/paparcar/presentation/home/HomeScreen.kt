package io.apptolast.paparcar.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.SatelliteAlt
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.vector.ImageVector
import com.swmansion.kmpmaps.core.MapType
import io.apptolast.paparcar.presentation.util.MapCircleFab
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import io.apptolast.paparcar.ui.components.GlassSurface
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
import io.apptolast.paparcar.presentation.util.locationDisplayText
import io.apptolast.paparcar.ui.components.ConfirmationBottomSheet
import io.apptolast.paparcar.presentation.home.components.HomeActionFab
import io.apptolast.paparcar.presentation.home.components.HomeGpsAccuracyBanner
import io.apptolast.paparcar.presentation.home.components.HomeMapFabColumn
import io.apptolast.paparcar.presentation.home.components.HomeNavBar
import io.apptolast.paparcar.presentation.home.components.HomePeekHandle
import io.apptolast.paparcar.presentation.home.components.HomeSearchBar
import io.apptolast.paparcar.presentation.home.components.homeSheetItems
import io.apptolast.paparcar.presentation.home.components.homeSheetSpotItemIndex
import io.apptolast.paparcar.presentation.home.components.PlatformMap
import io.apptolast.paparcar.domain.error.PaparcarError
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.error_gps_unavailable
import paparcar.composeapp.generated.resources.error_load_session
import paparcar.composeapp.generated.resources.error_load_spots
import paparcar.composeapp.generated.resources.error_parking_save_failed
import paparcar.composeapp.generated.resources.error_release_parking
import paparcar.composeapp.generated.resources.error_unknown
import paparcar.composeapp.generated.resources.home_cd_map_type
import paparcar.composeapp.generated.resources.home_release_dialog_cancel
import paparcar.composeapp.generated.resources.home_release_dialog_confirm
import paparcar.composeapp.generated.resources.home_release_dialog_message
import paparcar.composeapp.generated.resources.home_release_dialog_title
import paparcar.composeapp.generated.resources.home_manual_spot_reported
import paparcar.composeapp.generated.resources.home_spot_reported
import paparcar.composeapp.generated.resources.home_spot_signal_sent
import paparcar.composeapp.generated.resources.home_test_spot_sent
import paparcar.composeapp.generated.resources.settings_map_type_normal
import paparcar.composeapp.generated.resources.settings_map_type_satellite
import paparcar.composeapp.generated.resources.settings_map_type_terrain

private data class SelectedNavTarget(val lat: Double, val lon: Double)

// Initial/fallback peek size used before the handle has been measured.
// After first layout, peekHeightPx tracks the handle's real measured height
// so the sheet's collapsed edge sits exactly at the bottom-nav divider.
private val SheetPeekHeightInitial = 88.dp

// Glass stays "interacting" for this long after the last onCameraMove tick.
// Must be ≥ PlatformMap's CAMERA_MOVING_DEBOUNCE_MS (280) so the glass fade-out
// does not start while the map is still settling out of a fling.
private const val MAP_INTERACTION_IDLE_DELAY_MS = 320L

// Guard window for programmatic camera animations. Must cover the full
// PlatformMap CAMERA_ANIM_MS (700) + idle debounce so no synthetic
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

// ─────────────────────────────────────────────────────────────────────────────
// Root
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    onNavigateToHistory: () -> Unit = {},
    onOpenMapsNavigation: (Double, Double) -> Unit = { _, _ -> },
    navProgressState: MutableFloatState = remember { mutableFloatStateOf(1f) },
    onItemSelectedChange: (Boolean) -> Unit = {},
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
    val msgManualSpotReported = stringResource(Res.string.home_manual_spot_reported)
    val msgTestSpotSent = stringResource(Res.string.home_test_spot_sent)
    val msgSpotSignalSent = stringResource(Res.string.home_spot_signal_sent)

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
                HomeEffect.ManualSpotReported -> snackbarHostState.showSnackbar(
                    msgManualSpotReported
                )

                HomeEffect.TestSpotSent -> snackbarHostState.showSnackbar(msgTestSpotSent)
                HomeEffect.SpotSignalSent -> snackbarHostState.showSnackbar(msgSpotSignalSent)
                is HomeEffect.NavigateToHistory -> onNavigateToHistory()
                HomeEffect.RequestLocationPermission -> {}
            }
        }
    }

    HomeContent(
        state = state,
        onIntent = viewModel::handleIntent,
        onOpenMapsNavigation = onOpenMapsNavigation,
        snackbarHostState = snackbarHostState,
        navProgressState = navProgressState,
        onItemSelectedChange = onItemSelectedChange,
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
    onOpenMapsNavigation: (Double, Double) -> Unit,
    snackbarHostState: SnackbarHostState,
    navProgressState: MutableFloatState,
    onItemSelectedChange: (Boolean) -> Unit,
    bottomPadding: Dp,
) {
    val uiController = rememberHomeUiController()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

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
    val selectedSpot = selectedSpotId?.let { id -> state.nearbySpots.find { it.id == id } }
    val selectedNavTarget = selectedSpot
        ?.let { spot -> SelectedNavTarget(spot.location.latitude, spot.location.longitude) }
    val navLabel: String = when {
        isParkingSelected -> state.userParking?.let { p ->
            locationDisplayText(p.placeInfo, p.address, p.location.latitude, p.location.longitude)
        } ?: ""

        selectedSpot != null -> locationDisplayText(
            selectedSpot.placeInfo, selectedSpot.address,
            selectedSpot.location.latitude, selectedSpot.location.longitude,
        )

        else -> ""
    }
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
        // HomeNavBar renders as an overlay inside BoxWithConstraints below —
        // NOT as Scaffold.bottomBar. When it was a bottomBar, its
        // AnimatedVisibility enter/exit resized the scaffold content slot and
        // the sheet shifted in the middle of the item-selection transition.
        // As an overlay the nav doesn't participate in sheet-sizing layout.
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
                                        p.location.latitude,
                                        p.location.longitude
                                    )
                                )
                            }
                        }
                    ) {
                        Text(stringResource(Res.string.home_release_dialog_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showReleaseDialog = false }) {
                        Text(stringResource(Res.string.home_release_dialog_cancel))
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
            // containerHeightPx   = screen − latched nav height. Used ONLY
            //                       for snap points (peek / half) so those
            //                       stay stable while the nav animates.
            //   - Peek  →  sheet top at (screen − nav − peekHeight).
            //   - Half  →  (screen − nav) / 2.
            //   - Full  →  0 (sheet spans the entire window).
            val rawContainerHeightPx = constraints.maxHeight.toFloat()
            val navHeightPx = with(density) { stableBottomPadding.toPx() }
            val containerHeightPx = (rawContainerHeightPx - navHeightPx).coerceAtLeast(0f)

            // Peek height tracks the HomePeekHandle's real measured height so the
            // sheet's collapsed top edge sits flush with the AppBottomNavigation
            // divider. It's bootstrapped from SheetPeekHeightInitial and updated
            // via onSizeChanged on the handle Box. Zero-height readings are
            // filtered: the handle layout depends on sheet height, which depends
            // on peekOffsetPx → peekHeightPx, so a 0 transient would otherwise
            // collapse the whole chain and make the sheet disappear.
            var peekHeightPx by remember {
                mutableFloatStateOf(with(density) { SheetPeekHeightInitial.toPx() })
            }
            val peekOffsetPx = (containerHeightPx - peekHeightPx).coerceAtLeast(0f)
            val halfOffsetPx = containerHeightPx / 2f

            // Measured height of the HomeNavBar overlay (reported via onSizeChanged below).
            // The LazyColumn reads this to reserve space so the last item never sits
            // behind the overlay while an item is selected.
            var homeNavBarHeightPx by remember { mutableFloatStateOf(0f) }

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

            // Discrete signal: when an item is selected, the per-screen
            // HomeNavBar takes over and the root-level AppBottomNavigation
            // should hide entirely.
            LaunchedEffect(state.selectedItemId) {
                onItemSelectedChange(state.selectedItemId != null)
            }

            // Instagram-style nested scroll: when the list is scrolled to the very top and
            // the user drags down, collapse the sheet instead of letting the gesture be wasted.
            // Upward gestures are never intercepted — they always scroll the list.
            val currentPeekOffset = rememberUpdatedState(peekOffsetPx)
            val sheetNestedScroll = remember {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        val listAtTop = lazyListState.firstVisibleItemIndex == 0 &&
                            lazyListState.firstVisibleItemScrollOffset == 0
                        val peek = currentPeekOffset.value
                        val sheetCanCollapse = sheetOffsetPx.value < peek
                        if (available.y > 0f && listAtTop && sheetCanCollapse) {
                            val newOffset = (sheetOffsetPx.value + available.y).coerceIn(FULL_SNAP_OFFSET_PX, peek)
                            val consumed = newOffset - sheetOffsetPx.value
                            coroutineScope.launch { sheetOffsetPx.snapTo(newOffset) }
                            return Offset(0f, consumed)
                        }
                        return Offset.Zero
                    }
                }
            }

            // Boolean — equality short-circuits recomposition for downstream readers
            // (e.g. PlatformMap.reportMode) on every drag frame.
            val sheetExpanded by remember {
                derivedStateOf { sheetOffsetPx.value <= FULL_SNAP_OFFSET_PX + 1f }
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
            PlatformMap(
                spots = state.nearbySpots,
                userLocation = state.userGpsPoint,
                parkingLocation = state.userParking?.location,
                selectedSpotId = selectedSpotId,
                reportMode = !sheetExpanded,
                isAnyItemSelected = state.selectedItemId != null,
                isLoading = state.isLoading,
                mapType = state.mapType,
                onSpotClick = { spotId ->
                    state.nearbySpots.find { it.id == spotId }?.let { spot ->
                        onIntent(HomeIntent.SelectItem(spotId))
                        uiController.moveCamera(spot.location.latitude, spot.location.longitude)
                        coroutineScope.launch {
                            sheetOffsetPx.animateTo(
                                halfOffsetPx.coerceIn(FULL_SNAP_OFFSET_PX, peekOffsetPx),
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
                    // Layers chip: same 56dp diameter as the search TextField's
                    // height so the two sit as visual peers in the row. Tapping
                    // opens a Google-Maps-style picker anchored to the chip; the
                    // active type is marked with a check.
                    Box {
                        var mapTypeMenuExpanded by remember { mutableStateOf(false) }
                        MapCircleFab(
                            icon = Icons.Outlined.Layers,
                            onClick = { mapTypeMenuExpanded = true },
                            contentDescription = stringResource(Res.string.home_cd_map_type),
                            size = 56.dp,
                            iconSize = 24.dp,
                        )
                        DropdownMenu(
                            expanded = mapTypeMenuExpanded,
                            onDismissRequest = { mapTypeMenuExpanded = false },
                        ) {
                            MapTypeMenuItem(
                                icon = Icons.Outlined.Map,
                                label = stringResource(Res.string.settings_map_type_normal),
                                selected = state.mapType == MapType.NORMAL,
                                onClick = {
                                    onIntent(HomeIntent.SetMapType(MapType.NORMAL))
                                    mapTypeMenuExpanded = false
                                },
                            )
                            MapTypeMenuItem(
                                icon = Icons.Outlined.SatelliteAlt,
                                label = stringResource(Res.string.settings_map_type_satellite),
                                selected = state.mapType == MapType.SATELLITE,
                                onClick = {
                                    onIntent(HomeIntent.SetMapType(MapType.SATELLITE))
                                    mapTypeMenuExpanded = false
                                },
                            )
                            MapTypeMenuItem(
                                icon = Icons.Outlined.Terrain,
                                label = stringResource(Res.string.settings_map_type_terrain),
                                selected = state.mapType == MapType.TERRAIN,
                                onClick = {
                                    onIntent(HomeIntent.SetMapType(MapType.TERRAIN))
                                    mapTypeMenuExpanded = false
                                },
                            )
                        }
                    }
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
                                        halfOffsetPx.coerceIn(FULL_SNAP_OFFSET_PX, peekOffsetPx),
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
                        hasActiveParking = state.userParking != null,
                        onReportManualSpot = {
                            val lat = uiController.cameraLat
                                ?: state.userGpsPoint?.latitude
                                ?: return@HomeActionFab
                            val lon = uiController.cameraLon
                                ?: state.userGpsPoint?.longitude
                                ?: return@HomeActionFab
                            onIntent(HomeIntent.ReportManualSpot(lat, lon))
                        },
                        onReleaseParking = { showReleaseDialog = true },
                    )
                }
            }

            // ── Bottom sheet ─────────────────────────────────────────────────
            // Instagram-style: sheet height = visible area (bottomBound - top).
            // Uses rawContainerHeightPx (current constraints) so the sheet fills
            // whatever space is actually available — when an item is selected
            // and the AppBottomNavigation hides, the sheet reclaims that area
            // (HomeNavBar overlays on top and LazyColumn pads around it).
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
                                                FULL_SNAP_OFFSET_PX,
                                                peekOffsetPx
                                            ),
                                        )
                                    }
                                },
                                onDragStopped = { velocity ->
                                    coroutineScope.launch {
                                        val halfPoint = (FULL_SNAP_OFFSET_PX + peekOffsetPx) / 2f
                                        val current = sheetOffsetPx.value
                                        val target = when {
                                            velocity < -FLING_SNAP_VELOCITY -> {
                                                // Fling up: collapsed → half, half → expanded
                                                if (current > halfPoint) halfPoint else FULL_SNAP_OFFSET_PX
                                            }
                                            velocity > FLING_SNAP_VELOCITY -> {
                                                // Fling down: expanded → half, half → collapsed
                                                if (current < halfPoint) halfPoint else peekOffsetPx
                                            }
                                            else -> {
                                                // Soft drag (no fling): if released near the bottom,
                                                // finish collapsing instead of leaving it floating.
                                                val collapseSnapZone = peekOffsetPx * SOFT_DRAG_COLLAPSE_ZONE
                                                if (current > collapseSnapZone) peekOffsetPx else current
                                            }
                                        }.coerceIn(FULL_SNAP_OFFSET_PX, peekOffsetPx)
                                        sheetOffsetPx.animateTo(target, SnapSpec)
                                    }
                                },
                            ),
                    ) {
                        HomePeekHandle(
                            state = state,
                            onDismiss = { onIntent(HomeIntent.SelectItem(null)) },
                            onRelease = { showReleaseDialog = true },
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
                            // The sheet extends the full window height now, so its
                            // bottom rows would sit behind either the AppBottomNavigation
                            // (when no item is selected) or the HomeNavBar overlay
                            // (when an item is selected). Reserve the height of
                            // whichever is covering the sheet so the last list row
                            // stays visible above it.
                            bottom = 16.dp + if (state.selectedItemId != null) {
                                with(density) { homeNavBarHeightPx.toDp() }
                            } else stableBottomPadding,
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

            // ── HomeNavBar overlay ───────────────────────────────────────────
            // Moved out of Scaffold.bottomBar so its enter/exit animation no
            // longer resizes the sheet's containing layout. Rendered last in
            // BoxWithConstraints so it draws on top of the sheet.
            AnimatedVisibility(
                visible = state.selectedItemId != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { size -> homeNavBarHeightPx = size.height.toFloat() },
            ) {
                HomeNavBar(
                    navLabel = navLabel,
                    onNavigate = {
                        if (isParkingSelected) {
                            state.userParking?.let { p ->
                                onOpenMapsNavigation(
                                    p.location.latitude,
                                    p.location.longitude,
                                )
                            }
                        } else {
                            selectedNavTarget?.let { onOpenMapsNavigation(it.lat, it.lon) }
                        }
                    },
                )
            }
        }
    }
    } // CompositionLocalProvider
}

// Map-type picker row used by the Layers chip dropdown. Mirrors Google Maps:
// type icon on the left, label, trailing check on the active type. Selection
// is represented visually only — DropdownMenuItem's interaction state still
// drives ripple/hover, so we don't need RadioButton semantics here.
@Composable
private fun MapTypeMenuItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        },
        trailingIcon = if (selected) {
            { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(20.dp)) }
        } else null,
        onClick = onClick,
    )
}

