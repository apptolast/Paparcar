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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.presentation.util.locationDisplayText
import io.apptolast.paparcar.ui.components.ConfirmationBottomSheet
import io.apptolast.paparcar.presentation.home.components.HomeActionFab
import io.apptolast.paparcar.presentation.home.components.HomeGlassNavBar
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
import paparcar.composeapp.generated.resources.home_release_dialog_cancel
import paparcar.composeapp.generated.resources.home_release_dialog_confirm
import paparcar.composeapp.generated.resources.home_release_dialog_message
import paparcar.composeapp.generated.resources.home_release_dialog_title
import paparcar.composeapp.generated.resources.home_manual_spot_reported
import paparcar.composeapp.generated.resources.home_spot_reported
import paparcar.composeapp.generated.resources.home_spot_signal_sent
import paparcar.composeapp.generated.resources.home_test_spot_sent

private data class SelectedNavTarget(val lat: Double, val lon: Double)

// Peek = drag pill (22dp) + content row (82dp)
private val SheetPeekHeight = 104.dp

private const val MAP_INTERACTION_IDLE_DELAY_MS = 150L
// Material3 NavigationBar default content height (80dp) — its window insets add to this once measured
private const val GLASS_NAV_BAR_DEFAULT_HEIGHT_DP = 80

private val SnapSpec = tween<Float>(durationMillis = 300, easing = FastOutSlowInEasing)

// Velocity (px/s) required to snap the sheet on fling; below this the sheet stays in place
private const val FLING_SNAP_VELOCITY = 1200f

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
    var isMapInteracting by remember { mutableStateOf(false) }
    val mapIdleJob = remember { mutableStateOf<Job?>(null) }
    var glassNavBarHeightPx by remember { mutableFloatStateOf(with(density) { GLASS_NAV_BAR_DEFAULT_HEIGHT_DP.dp.toPx() }) }
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
                            state.userParking?.let { p ->
                                onOpenMapsNavigation(
                                    p.location.latitude,
                                    p.location.longitude
                                )
                            }
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
            val containerHeightPx = constraints.maxHeight.toFloat()
            var peekHeightPx by remember { mutableFloatStateOf(with(density) { (SheetPeekHeight + navBarBottom).toPx() }) }
            // Reserve space for the glass nav bar only when it's visible (no item selected).
            // When an item is selected the HomeNavBar replaces it and the Scaffold already
            // shrinks containerHeightPx, so no extra reserve is needed.
            // Material3 NavigationBar includes its own bottom inset, so glassNavBarHeightPx
            // already accounts for navigation bar padding.
            val navBarReservePx = if (state.selectedItemId == null) glassNavBarHeightPx else 0f
            val peekOffsetPx = (containerHeightPx - peekHeightPx - navBarReservePx).coerceAtLeast(0f)
            val halfOffsetPx = containerHeightPx / 2f

            // Sheet is always full-height — fullSnap = 0 means "sheet top at screen top".
            // List inside is a LazyColumn with weight(1f), always scrollable.
            val fullSnapOffsetPx = 0f

            val sheetOffsetPx = remember { Animatable(peekOffsetPx) }
            LaunchedEffect(peekOffsetPx, state.selectedItemId) {
                if (state.selectedItemId == null) {
                    sheetOffsetPx.animateTo(peekOffsetPx, SnapSpec)
                } else if (sheetOffsetPx.value >= peekOffsetPx) {
                    sheetOffsetPx.snapTo(peekOffsetPx)
                }
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
                        val full = currentFullSnap.value
                        val sheetCanCollapse = sheetOffsetPx.value < peek
                        if (available.y > 0f && listAtTop && sheetCanCollapse) {
                            val newOffset = (sheetOffsetPx.value + available.y).coerceIn(full, peek)
                            val consumed = newOffset - sheetOffsetPx.value
                            coroutineScope.launch { sheetOffsetPx.snapTo(newOffset) }
                            return Offset(0f, consumed)
                        }
                        return Offset.Zero
                    }
                }
            }

            val sheetExpanded = sheetOffsetPx.value <= fullSnapOffsetPx + 1f
            // FABs sit just above the sheet's current top edge and follow it as it moves.
            val fabBottomDp =
                with(density) { (containerHeightPx - sheetOffsetPx.value).toDp() } + 12.dp

            // Map shrinks as the sheet expands — its bottom edge tracks the sheet top.
            val mapHeightDp = with(density) { sheetOffsetPx.value.toDp() }

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
                        }
                    }
                },
                onCameraMove = { lat, lon ->
                    uiController.onCameraMoved(lat, lon)
                    onIntent(HomeIntent.CameraPositionChanged(lat, lon))
                    isMapInteracting = true
                    mapIdleJob.value?.cancel()
                    mapIdleJob.value = coroutineScope.launch {
                        delay(MAP_INTERACTION_IDLE_DELAY_MS)
                        isMapInteracting = false
                    }
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
                        modifier = Modifier.fillMaxWidth(),
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

            // ── Bottom sheet ─────────────────────────────────────────────────
            // Instagram-style: sheet height = visible area (containerHeight - top).
            // This gives the inner LazyColumn a viewport that matches the visible
            // portion of the sheet, so content outside the viewport actually
            // scrolls. Anchored to BottomCenter; drag updates sheetOffsetPx, which
            // in turn changes the sheet height (top moves up/down).
            val sheetHeightDp = with(density) {
                (containerHeightPx - sheetOffsetPx.value).coerceAtLeast(0f).toDp()
            }
            GlassSurface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(sheetHeightDp),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Handle: the only area that drags the sheet
                    Box(
                        modifier = Modifier
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
                                        val target = when {
                                            velocity < -FLING_SNAP_VELOCITY -> fullSnapOffsetPx
                                            velocity > FLING_SNAP_VELOCITY -> peekOffsetPx
                                            else -> sheetOffsetPx.value
                                        }.coerceIn(fullSnapOffsetPx, peekOffsetPx)
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

                    // navigationBarsPadding() covers the system nav bar inset; the extra
                    // GLASS_NAV_BAR_DEFAULT_HEIGHT_DP pushes content above the floating
                    // HomeGlassNavBar so the last row is not hidden behind it.
                    val listBottomPadding = if (state.selectedItemId == null) {
                        GLASS_NAV_BAR_DEFAULT_HEIGHT_DP.dp + 16.dp
                    } else {
                        16.dp
                    }
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .nestedScroll(sheetNestedScroll),
                        contentPadding = PaddingValues(top = 4.dp, bottom = listBottomPadding),
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

            // ── Glass BottomNav — replaces HomeFloatingHeader ────────────────
            // Visible when no item is selected. When a spot/parking is selected
            // HomeNavBar (navigate bar) takes over via Scaffold bottomBar.
            AnimatedVisibility(
                visible = state.selectedItemId == null,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { if (it.height > 0) glassNavBarHeightPx = it.height.toFloat() },
            ) {
                HomeGlassNavBar(
                    onMapClick = {
                        state.userGpsPoint?.let {
                            uiController.moveCamera(it.latitude, it.longitude, zoom = 16f)
                        }
                    },
                    onHistoryClick = { onIntent(HomeIntent.OpenHistory) },
                    onMyCarClick = onNavigateToMyCar,
                    onSettingsClick = onNavigateToSettings,
                )
            }
        }
    }
    } // CompositionLocalProvider
}

