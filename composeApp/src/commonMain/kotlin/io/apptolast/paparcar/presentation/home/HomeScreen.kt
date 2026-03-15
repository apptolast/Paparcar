package io.apptolast.paparcar.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import io.apptolast.paparcar.presentation.home.components.HomeFloatingHeader
import io.apptolast.paparcar.presentation.home.components.HomeMapFabColumn
import io.apptolast.paparcar.presentation.home.components.HomeNavBar
import io.apptolast.paparcar.presentation.home.components.HomePeekHandle
import io.apptolast.paparcar.presentation.home.components.HomeReportSpotFab
import io.apptolast.paparcar.presentation.home.components.HomeSheetContent
import io.apptolast.paparcar.presentation.home.components.PlatformMap
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paparcar.composeapp.generated.resources.Res
import kotlin.math.abs
import kotlin.math.roundToInt

private data class SelectedNavTarget(val lat: Double, val lon: Double)

// Peek = drag pill (22dp) + address row (74dp)
private val SheetPeekHeight = 96.dp

private val SnapSpec = tween<Float>(durationMillis = 300, easing = FastOutSlowInEasing)

// Minimum pixel gap between two snap points — avoids duplicates after rounding
private const val SNAP_THRESHOLD_PX = 120f

// ─────────────────────────────────────────────────────────────────────────────
// Root
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    onNavigateToMap: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onOpenMapsNavigation: (Double, Double) -> Unit = { _, _ -> },
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is HomeEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
                is HomeEffect.ShowSuccess -> snackbarHostState.showSnackbar(effect.message)
                HomeEffect.NavigateToMap -> onNavigateToMap()
                is HomeEffect.NavigateToHistory -> onNavigateToHistory()
                is HomeEffect.RequestLocationPermission -> {}
            }
        }
    }

    HomeContent(
        state = state,
        onIntent = viewModel::handleIntent,
        onNavigateToSettings = onNavigateToSettings,
        onOpenMapsNavigation = onOpenMapsNavigation,
        snackbarHostState = snackbarHostState,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HomeContent(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    onNavigateToSettings: () -> Unit,
    onOpenMapsNavigation: (Double, Double) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val uiController = rememberHomeUiController()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var selectedNavTarget by remember { mutableStateOf<SelectedNavTarget?>(null) }
    var selectedSpotId by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()
    val spotScrollPositions = remember { mutableMapOf<String, Int>() }

    // Auto-clear selection when the selected spot leaves the nearby list
    LaunchedEffect(state.nearbySpots) {
        if (selectedSpotId != null && state.nearbySpots.none { it.id == selectedSpotId }) {
            selectedSpotId = null
            selectedNavTarget = null
        }
    }

    LaunchedEffect(state.userGpsPoint) {
        state.userGpsPoint?.let { uiController.onUserLocationAvailable(it.latitude, it.longitude) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            AnimatedVisibility(
                visible = selectedNavTarget != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                HomeNavBar(
                    onNavigate = {
                        selectedNavTarget?.let { onOpenMapsNavigation(it.lat, it.lon) }
                    },
                    onDismiss = { selectedNavTarget = null; selectedSpotId = null },
                )
            }
        },
        containerColor = Color.Transparent,
    ) { scaffoldPadding ->

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
        ) {
            val containerHeightPx = constraints.maxHeight.toFloat()
            val peekHeightPx = with(density) { (SheetPeekHeight + navBarBottom).toPx() }
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
            LaunchedEffect(peekOffsetPx) {
                if (sheetOffsetPx.value >= peekOffsetPx) {
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
            val onNavigateToParking: () -> Unit = {
                state.userParking?.let { p ->
                    uiController.moveCamera(p.location.latitude, p.location.longitude)
                    selectedNavTarget = SelectedNavTarget(p.location.latitude, p.location.longitude)
                }
            }

            // ── Map ──────────────────────────────────────────────────────────
            PlatformMap(
                spots = state.nearbySpots,
                userLocation = state.userGpsPoint,
                parkingLocation = state.userParking?.location,
                selectedSpotId = selectedSpotId,
                reportMode = !sheetExpanded,
                onSpotClick = { spotId ->
                    state.nearbySpots.find { it.id == spotId }?.let { spot ->
                        selectedSpotId = spotId
                        selectedNavTarget = SelectedNavTarget(
                            spot.location.latitude,
                            spot.location.longitude,
                        )
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
                onCameraMove = { lat, lon -> uiController.onCameraMoved(lat, lon) },
                cameraTarget = uiController.cameraTarget,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(mapHeightDp),
            )

            // ── Floating header ───────────────────────────────────────────────
            HomeFloatingHeader(
                onHistoryClick = { onIntent(HomeIntent.OpenHistory) },
                onSettingsClick = onNavigateToSettings,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )

            // ── Report Spot Extended FAB ─────────────────────────────────────
            AnimatedVisibility(
                visible = !sheetExpanded,
                enter = fadeIn() + slideInHorizontally { -it },
                exit = fadeOut() + slideOutHorizontally { -it },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, bottom = SheetPeekHeight + navBarBottom + 12.dp),
            ) {
                HomeReportSpotFab(
                    onClick = {
                        val lat = uiController.cameraLat
                            ?: state.userParking?.location?.latitude
                            ?: state.userGpsPoint?.latitude
                            ?: return@HomeReportSpotFab
                        val lon = uiController.cameraLon
                            ?: state.userParking?.location?.longitude
                            ?: state.userGpsPoint?.longitude
                            ?: return@HomeReportSpotFab
                        onIntent(HomeIntent.ReleaseParking(lat, lon))
                    },
                )
            }

            // ── FAB column ────────────────────────────────────────────────────
            AnimatedVisibility(
                visible = !sheetExpanded,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 14.dp, bottom = SheetPeekHeight + navBarBottom + 12.dp),
            ) {
                HomeMapFabColumn(
                    userParking = state.userParking,
                    userGpsPoint = state.userGpsPoint,
                    onMyLocation = {
                        state.userGpsPoint?.let {
                            uiController.moveCamera(it.latitude, it.longitude, zoom = 16f)
                        }
                    },
                    onParkedCar = {
                        state.userParking?.let {
                            uiController.moveCamera(it.location.latitude, it.location.longitude)
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
                        modifier = Modifier.draggable(
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
                        ),
                    ) {
                        HomePeekHandle(
                            state = state,
                            onParkingClick = onNavigateToParking,
                            selectedSpotId = selectedSpotId,
                        )
                    }

                    HomeSheetContent(
                        state = state,
                        onIntent = onIntent,
                        onCameraMove = { lat, lon -> uiController.moveCamera(lat, lon) },
                        onParkingClick = onNavigateToParking,
                        onParkingRelease = {
                            state.userParking?.let { p ->
                                onIntent(HomeIntent.ReleaseParking(p.location.latitude, p.location.longitude))
                            }
                        },
                        onSpotSelect = { lat, lon, spotId ->
                            selectedSpotId = spotId
                            selectedNavTarget = SelectedNavTarget(lat, lon)
                        },
                        scrollState = scrollState,
                        selectedSpotId = selectedSpotId,
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
