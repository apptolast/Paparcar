package io.apptolast.paparcar.presentation.home

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import io.apptolast.paparcar.presentation.home.components.HomePeekHandle
import io.apptolast.paparcar.presentation.home.components.HomeReportBar
import io.apptolast.paparcar.presentation.home.components.HomeSheetContent
import io.apptolast.paparcar.presentation.home.components.PlatformMap
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

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
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is HomeEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
                is HomeEffect.ShowSuccess -> snackbarHostState.showSnackbar(effect.message)
                is HomeEffect.NavigateToMap -> onNavigateToMap()
                is HomeEffect.NavigateToHistory -> onNavigateToHistory()
                is HomeEffect.RequestLocationPermission -> {}
            }
        }
    }

    HomeContent(
        state = state,
        onIntent = viewModel::handleIntent,
        onNavigateToSettings = onNavigateToSettings,
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
    snackbarHostState: SnackbarHostState,
) {
    val uiController = rememberHomeUiController()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    LaunchedEffect(state.userGpsPoint) {
        state.userGpsPoint?.let { uiController.onUserLocationAvailable(it.latitude, it.longitude) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            HomeReportBar(onClick = {
                val lat = uiController.cameraLat
                    ?: state.userParking?.location?.latitude
                    ?: state.userGpsPoint?.latitude
                    ?: return@HomeReportBar
                val lon = uiController.cameraLon
                    ?: state.userParking?.location?.longitude
                    ?: state.userGpsPoint?.longitude
                    ?: return@HomeReportBar
                onIntent(HomeIntent.ReleaseParking(lat, lon))
            })
        },
        containerColor = Color.Transparent,
    ) { scaffoldPadding ->

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
        ) {
            val containerHeightPx = constraints.maxHeight.toFloat()
            val peekHeightPx = with(density) { SheetPeekHeight.toPx() }
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

            // Resets to peek on container resize; animates back if content shrinks while expanded.
            val sheetOffsetPx = remember(peekOffsetPx) { Animatable(peekOffsetPx) }
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
                }
            }

            // ── Map ──────────────────────────────────────────────────────────
            PlatformMap(
                spots = state.nearbySpots,
                userLocation = state.userGpsPoint,
                parkingLocation = state.userParking?.location,
                onSpotClick = {},
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

            // ── FAB column ────────────────────────────────────────────────────
            AnimatedVisibility(
                visible = !sheetExpanded,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 14.dp, bottom = SheetPeekHeight + 12.dp),
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
                        )
                    }

                    HomeSheetContent(
                        state = state,
                        onIntent = onIntent,
                        onCameraMove = { lat, lon -> uiController.moveCamera(lat, lon) },
                        onParkingClick = onNavigateToParking,
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
