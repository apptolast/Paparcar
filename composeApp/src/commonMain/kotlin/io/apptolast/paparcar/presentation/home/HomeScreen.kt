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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.presentation.util.locationDisplayText
import io.apptolast.paparcar.ui.components.ConfirmationBottomSheet
import io.apptolast.paparcar.presentation.home.components.HomeActionFab
import io.apptolast.paparcar.presentation.home.components.HomeFloatingHeader
import io.apptolast.paparcar.presentation.home.components.HomeGpsAccuracyBanner
import io.apptolast.paparcar.presentation.home.components.HomeMapFabColumn
import io.apptolast.paparcar.presentation.home.components.HomeNavBar
import io.apptolast.paparcar.presentation.home.components.HomePeekHandle
import io.apptolast.paparcar.presentation.home.components.HomeSearchBar
import io.apptolast.paparcar.presentation.home.components.HomeSheetContent
import io.apptolast.paparcar.presentation.home.components.PlatformMap
import kotlinx.coroutines.launch
import io.apptolast.paparcar.domain.error.PaparcarError
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.error_gps_unavailable
import paparcar.composeapp.generated.resources.error_load_session
import paparcar.composeapp.generated.resources.error_load_spots
import paparcar.composeapp.generated.resources.error_release_parking
import paparcar.composeapp.generated.resources.error_unknown
import paparcar.composeapp.generated.resources.home_release_dialog_cancel
import paparcar.composeapp.generated.resources.home_release_dialog_confirm
import paparcar.composeapp.generated.resources.home_release_dialog_message
import paparcar.composeapp.generated.resources.home_release_dialog_title
import paparcar.composeapp.generated.resources.home_manual_spot_reported
import paparcar.composeapp.generated.resources.home_spot_reported
import paparcar.composeapp.generated.resources.home_test_spot_sent
import kotlin.math.abs
import kotlin.math.roundToInt

private data class SelectedNavTarget(val lat: Double, val lon: Double)

// Peek = drag pill (22dp) + content row (82dp)
private val SheetPeekHeight = 104.dp

private val SnapSpec = tween<Float>(durationMillis = 300, easing = FastOutSlowInEasing)

// Minimum pixel gap between two snap points — avoids duplicates after rounding
private const val SNAP_THRESHOLD_PX = 120f

// ─────────────────────────────────────────────────────────────────────────────
// Root
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    onNavigateToHistory: () -> Unit = {},
    onNavigateToMyCar: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onOpenMapsNavigation: (Double, Double) -> Unit = { _, _ -> },
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
                HomeEffect.ManualSpotReported -> snackbarHostState.showSnackbar(msgManualSpotReported)
                HomeEffect.TestSpotSent -> snackbarHostState.showSnackbar(msgTestSpotSent)
                is HomeEffect.NavigateToHistory -> onNavigateToHistory()
                HomeEffect.RequestLocationPermission -> {}
            }
        }
    }

    HomeContent(
        state = state,
        onIntent = viewModel::handleIntent,
        onNavigateToMyCar = onNavigateToMyCar,
        onNavigateToSettings = onNavigateToSettings,
        onOpenMapsNavigation = onOpenMapsNavigation,
        snackbarHostState = snackbarHostState,
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
    onNavigateToMyCar: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onOpenMapsNavigation: (Double, Double) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val uiController = rememberHomeUiController()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
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
    val scrollState = rememberScrollState()
    val spotScrollPositions = remember { mutableMapOf<String, Int>() }

    LaunchedEffect(state.userGpsPoint) {
        state.userGpsPoint?.let { uiController.onUserLocationAvailable(it.latitude, it.longitude) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            AnimatedVisibility(
                visible = state.selectedItemId != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                HomeNavBar(
                    navLabel = navLabel,
                    onNavigate = {
                        if (isParkingSelected) {
                            state.userParking?.let { p -> onOpenMapsNavigation(p.location.latitude, p.location.longitude) }
                        } else {
                            selectedNavTarget?.let { onOpenMapsNavigation(it.lat, it.lon) }
                        }
                    },
                )
            }
        },
        containerColor = Color.Transparent,
    ) { scaffoldPadding ->

        if (showReleaseDialog) {
            AlertDialog(
                onDismissRequest = { showReleaseDialog = false },
                title = { Text(stringResource(Res.string.home_release_dialog_title)) },
                text = { Text(stringResource(Res.string.home_release_dialog_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        showReleaseDialog = false
                        state.userParking?.let { p ->
                            onIntent(HomeIntent.ReleaseParking(p.location.latitude, p.location.longitude))
                        }
                    }) {
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
            val containerHeightPx = constraints.maxHeight.toFloat()
            var peekHeightPx by remember { mutableFloatStateOf(with(density) { (SheetPeekHeight + navBarBottom).toPx() }) }
            val containerHeightDp = with(density) { containerHeightPx.toDp() }

            val peekOffsetPx = (containerHeightPx - peekHeightPx).coerceAtLeast(0f)
            val halfOffsetPx = containerHeightPx / 2f

            // Measured height of the sheet Surface (updated after each layout pass).
            // Defaults to containerHeightPx so the initial fullSnapOffsetPx = 0 (same as before).
            var sheetHeightPx by remember(containerHeightPx) { mutableFloatStateOf(containerHeightPx) }

            // Full snap: how far to offset the sheet so ALL content is just visible.
            val fullSnapOffsetPx = (containerHeightPx - sheetHeightPx).coerceAtLeast(0f)

            // Build snap points, skipping half when content is shorter than half-screen.
            val snapPoints = remember(peekOffsetPx, halfOffsetPx, fullSnapOffsetPx) {
                buildList {
                    add(peekOffsetPx)
                    if (halfOffsetPx < peekOffsetPx - SNAP_THRESHOLD_PX &&
                        halfOffsetPx > fullSnapOffsetPx + SNAP_THRESHOLD_PX
                    ) add(halfOffsetPx)
                    if (fullSnapOffsetPx < peekOffsetPx - SNAP_THRESHOLD_PX) add(fullSnapOffsetPx)
                }
            }

            // Initialized at peek on first composition. Does NOT reset on subsequent peekOffsetPx
            // changes (e.g. nav bar appearing) so the sheet stays open when nav bar shows.
            val sheetOffsetPx = remember { Animatable(peekOffsetPx) }
            // If the sheet was at (or beyond) peek when the container resizes, keep it at peek.
            // If it was open, leave it in place — just clamp so it can't go past the new peek.
            // When no item is selected the sheet always lives at peek.
            // Key on both peekOffsetPx AND selectedItemId so we re-evaluate:
            //  • when the nav bar appears/disappears (container resizes → new peekOffsetPx)
            //  • when the AnimatedVisibility exit animation finishes (second peekOffsetPx change)
            LaunchedEffect(peekOffsetPx, state.selectedItemId) {
                if (state.selectedItemId == null) {
                    sheetOffsetPx.animateTo(peekOffsetPx, SnapSpec)
                } else if (sheetOffsetPx.value >= peekOffsetPx) {
                    sheetOffsetPx.snapTo(peekOffsetPx)
                }
            }
            LaunchedEffect(fullSnapOffsetPx) {
                if (sheetOffsetPx.value < fullSnapOffsetPx) {
                    sheetOffsetPx.animateTo(fullSnapOffsetPx, SnapSpec)
                }
            }

            val nestedScrollConnection = rememberSheetScrollConnection(
                sheetOffsetPx = sheetOffsetPx,
                snapPoints = snapPoints,
                peekOffsetPx = peekOffsetPx,
                fullSnapOffsetPx = fullSnapOffsetPx,
            )

            val sheetExpanded = sheetOffsetPx.value <= fullSnapOffsetPx + 1f
            val mapHeightDp = with(density) { sheetOffsetPx.value.toDp() } + 20.dp
            // FABs sit just above the sheet's current top edge and follow it as it moves.
            val fabBottomDp = with(density) { (containerHeightPx - sheetOffsetPx.value).toDp() } + 12.dp

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
                                halfOffsetPx.coerceIn(fullSnapOffsetPx, peekOffsetPx),
                                SnapSpec,
                            )
                            spotScrollPositions[spotId]?.let { yOffset ->
                                scrollState.scrollTo(yOffset)
                            }
                        }
                    }
                },
                onCameraMove = { lat, lon ->
                    uiController.onCameraMoved(lat, lon)
                    onIntent(HomeIntent.CameraPositionChanged(lat, lon))
                },
                cameraTarget = uiController.cameraTarget,
                modifier = Modifier
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
                    HomeFloatingHeader(
                        onHistoryClick = { onIntent(HomeIntent.OpenHistory) },
                        onMyCarClick = onNavigateToMyCar,
                        onSettingsClick = onNavigateToSettings,
                    )
                }
                HomeGpsAccuracyBanner(
                    accuracy = state.userGpsPoint?.accuracy,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            // ── Right FAB column (map controls + action speed-dial) ──────────
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
                        onLayersClick = { onIntent(HomeIntent.CycleMapType) },
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

            // ── 3-state bottom sheet ─────────────────────────────────────────
            // Surface wraps content height (no fillMaxSize) — onSizeChanged reports
            // the actual height so fullSnapOffsetPx positions the sheet to show all content.
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = containerHeightDp)
                    .offset { IntOffset(0, sheetOffsetPx.value.roundToInt()) }
                    .onSizeChanged { size -> sheetHeightPx = size.height.toFloat() },
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(modifier = Modifier.nestedScroll(nestedScrollConnection)) {
                    // Handle: drives the sheet via draggable
                    Box(
                        modifier = Modifier
                            .draggable(
                                orientation = Orientation.Vertical,
                                state = rememberDraggableState { delta ->
                                    coroutineScope.launch {
                                        sheetOffsetPx.snapTo(
                                            (sheetOffsetPx.value + delta).coerceIn(fullSnapOffsetPx, peekOffsetPx),
                                        )
                                    }
                                },
                                onDragStopped = { velocity ->
                                    coroutineScope.launch {
                                        val target = snapToNearest(snapPoints, sheetOffsetPx.value, velocity)
                                        sheetOffsetPx.animateTo(target, SnapSpec)
                                    }
                                },
                            )
                            .onSizeChanged { size -> peekHeightPx = size.height.toFloat() },
                    ) {
                        HomePeekHandle(
                            state = state,
                            onDismiss = { onIntent(HomeIntent.SelectItem(null)) },
                            onRelease = { showReleaseDialog = true },
                        )
                    }

                    HomeSheetContent(
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
                        scrollState = scrollState,
                        spotScrollPositions = spotScrollPositions,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sheet scroll connection
// ─────────────────────────────────────────────────────────────────────────────

/**
 * NestedScrollConnection that coordinates the 3-state bottom sheet with an
 * inner scrollable list:
 *  • onPreScroll  (UP)   — expand sheet before the list scrolls
 *  • onPostScroll (DOWN) — collapse only after the list has reached its top
 *  • onPreFling   (UP)   — snap sheet open on upward fling
 *  • onPostFling  (DOWN) — snap sheet closed on downward fling once list is exhausted
 */
@Composable
private fun rememberSheetScrollConnection(
    sheetOffsetPx: Animatable<Float, *>,
    snapPoints: List<Float>,
    peekOffsetPx: Float,
    fullSnapOffsetPx: Float,
): NestedScrollConnection {
    val coroutineScope = rememberCoroutineScope()
    // rememberUpdatedState ensures the lambdas below always read the latest values
    // even when the connection is not recreated (e.g. minor fullSnapOffsetPx changes).
    val fullSnapState = rememberUpdatedState(fullSnapOffsetPx)
    val peekState = rememberUpdatedState(peekOffsetPx)
    return remember(sheetOffsetPx, snapPoints) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                return if (delta < 0f && sheetOffsetPx.value > fullSnapState.value) {
                    val newOffset = (sheetOffsetPx.value + delta).coerceAtLeast(fullSnapState.value)
                    val consumed = newOffset - sheetOffsetPx.value
                    coroutineScope.launch { sheetOffsetPx.snapTo(newOffset) }
                    Offset(0f, consumed)
                } else Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                return if (delta > 0f && sheetOffsetPx.value < peekState.value) {
                    val newOffset = (sheetOffsetPx.value + delta).coerceAtMost(peekState.value)
                    val consumedY = newOffset - sheetOffsetPx.value
                    coroutineScope.launch { sheetOffsetPx.snapTo(newOffset) }
                    Offset(0f, consumedY)
                } else Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val vy = available.y
                return if (vy < -300f && sheetOffsetPx.value > fullSnapState.value) {
                    sheetOffsetPx.animateTo(snapToNearest(snapPoints, sheetOffsetPx.value, vy), SnapSpec)
                    available
                } else Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val vy = available.y
                return if (vy > 300f && sheetOffsetPx.value < peekState.value) {
                    sheetOffsetPx.animateTo(snapToNearest(snapPoints, sheetOffsetPx.value, vy), SnapSpec)
                    available
                } else Velocity.Zero
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Snap logic
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Returns the target snap point given current offset and drag velocity.
 * velocity < -300 → snap up (expand), velocity > 300 → snap down (collapse), else → nearest.
 */
private fun snapToNearest(snapPoints: List<Float>, current: Float, velocity: Float): Float =
    when {
        velocity < -300f -> snapPoints.filter { it < current }.maxOrNull() ?: snapPoints.first()
        velocity > 300f -> snapPoints.filter { it > current }.minOrNull() ?: snapPoints.last()
        else -> snapPoints.minByOrNull { abs(it - current) } ?: current
    }
