@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.presentation.home.components.PapFloatingHeader
import io.apptolast.paparcar.presentation.home.components.PapMapFab
import io.apptolast.paparcar.presentation.home.components.PapPeekHandle
import io.apptolast.paparcar.presentation.home.components.PapReportBar
import io.apptolast.paparcar.presentation.home.components.PapSheetContent
import io.apptolast.paparcar.presentation.map.PlatformMap
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

// Minimum visible height of the sheet (peek state = address bar + pill).
private val PeekHeight = 96.dp

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

    // Auto-center on first location fix
    LaunchedEffect(state.userGpsPoint) {
        state.userGpsPoint?.let { uiController.onUserLocationAvailable(it.latitude, it.longitude) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0),
        bottomBar = { PapReportBar(onClick = { onIntent(HomeIntent.ReportTestSpot) }) },
        containerColor = Color.Transparent,
    ) { scaffoldPadding ->

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
        ) {
            val density = LocalDensity.current

            // Sheet offset is measured from the TOP of this container.
            // Peek  = containerHeight - PeekHeight  (only the address bar is visible)
            // Half  = containerHeight / 2            (half the screen is visible)
            // Full  = 0                              (sheet covers the entire area)
            val containerHeightPx = with(density) { maxHeight.toPx() }
            val peekOffsetPx = with(density) { (maxHeight - PeekHeight).toPx() }
            val halfOffsetPx = containerHeightPx / 2f

            val snapOffsets = remember(peekOffsetPx, halfOffsetPx) {
                listOf(peekOffsetPx, halfOffsetPx, 0f)
            }

            // Animatable sheet top-offset. Initialized once per container size.
            val sheetOffsetPx = remember(peekOffsetPx) { Animatable(peekOffsetPx) }

            val snapToNearest: (velocity: Float) -> Unit = { velocity ->
                val current = sheetOffsetPx.value
                val target = when {
                    velocity < -300f ->
                        snapOffsets.lastOrNull { it < current - 1f } ?: snapOffsets.first()
                    velocity > 300f ->
                        snapOffsets.firstOrNull { it > current + 1f } ?: snapOffsets.last()
                    else ->
                        snapOffsets.minByOrNull { abs(it - current) } ?: current
                }
                coroutineScope.launch {
                    sheetOffsetPx.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow))
                }
            }

            val sheetExpanded = sheetOffsetPx.value <= 1f
            val mapHeightDp = with(density) { sheetOffsetPx.value.toDp() }

            Box(modifier = Modifier.fillMaxSize()) {

                // ── Map — height tracks the sheet's top edge ───────────────
                PlatformMap(
                    spots = state.nearbySpots,
                    userLocation = state.userGpsPoint,
                    userParking = state.userParking,
                    onSpotClick = {},
                    cameraTarget = uiController.cameraTarget,
                    contentPadding = PaddingValues(),
                    showMapControls = false,
                    modifier = Modifier.fillMaxWidth().height(mapHeightDp + 20.dp),
                )

                // ── 3-state bottom sheet ───────────────────────────────────
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .offset { IntOffset(0, sheetOffsetPx.value.roundToInt()) },
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column {
                        // Drag handle — the only draggable area so the list
                        // below can scroll freely without interference.
                        Box(
                            modifier = Modifier.draggable(
                                orientation = Orientation.Vertical,
                                state = rememberDraggableState { delta ->
                                    coroutineScope.launch {
                                        sheetOffsetPx.snapTo(
                                            (sheetOffsetPx.value + delta)
                                                .coerceIn(0f, peekOffsetPx),
                                        )
                                    }
                                },
                                onDragStopped = { velocity -> snapToNearest(velocity) },
                            ),
                        ) {
                            PapPeekHandle(
                                state = state,
                                onParkingClick = {
                                    state.userParking?.let { p ->
                                        uiController.moveCamera(
                                            p.location.latitude, p.location.longitude,
                                        )
                                    }
                                },
                            )
                        }

                        PapSheetContent(
                            state = state,
                            onIntent = onIntent,
                            onCameraMove = { lat, lon -> uiController.moveCamera(lat, lon) },
                            onParkingClick = {
                                state.userParking?.let { p ->
                                    uiController.moveCamera(
                                        p.location.latitude, p.location.longitude,
                                    )
                                }
                            },
                        )
                    }
                }

                // ── Custom FAB column — bottom-end, above peek handle ──────
                AnimatedVisibility(
                    visible = !sheetExpanded,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 14.dp, bottom = PeekHeight + 12.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (state.userParking != null) {
                            PapMapFab(
                                icon = Icons.Outlined.DirectionsCar,
                                tint = MaterialTheme.colorScheme.primary,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                onClick = {
                                    state.userParking.let { p ->
                                        uiController.moveCamera(
                                            p.location.latitude,
                                            p.location.longitude,
                                        )
                                    }
                                },
                            )
                        }
                        if (state.userParking != null && state.userGpsPoint != null) {
                            PapMapFab(
                                icon = Icons.Outlined.Route,
                                onClick = {
                                    uiController.moveCameraToBounds(
                                        lat1 = state.userParking.location.latitude,
                                        lon1 = state.userParking.location.longitude,
                                        lat2 = state.userGpsPoint.latitude,
                                        lon2 = state.userGpsPoint.longitude,
                                    )
                                },
                            )
                        }
                        PapMapFab(
                            icon = Icons.Outlined.MyLocation,
                            onClick = {
                                state.userGpsPoint?.let {
                                    uiController.moveCamera(it.latitude, it.longitude)
                                }
                            },
                        )
                    }
                }

                // ── Floating header ────────────────────────────────────────
                PapFloatingHeader(
                    onHistoryClick = { onIntent(HomeIntent.OpenHistory) },
                    onSettingsClick = onNavigateToSettings,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}