@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.presentation.home.components.EcoFloatingHeader
import io.apptolast.paparcar.presentation.home.components.EcoMapFab
import io.apptolast.paparcar.presentation.home.components.EcoPeekHandle
import io.apptolast.paparcar.presentation.home.components.EcoReportBar
import io.apptolast.paparcar.presentation.home.components.EcoSheetContent
import io.apptolast.paparcar.presentation.map.CameraTarget
import io.apptolast.paparcar.presentation.map.PlatformMap
import io.apptolast.paparcar.ui.theme.EcoGreen
import io.apptolast.paparcar.ui.theme.EcoGreenMuted
import org.koin.compose.viewmodel.koinViewModel

// Peek = pill(22) + address row(74) = 96dp
private val SheetPeekHeight = 96.dp

// ─────────────────────────────────────────────────────────────────────────────
// Root
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun EcoHomeScreen(
    onNavigateToMap: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},   // ← NEW: wired from nav graph
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

    EcoHomeContent(
        state = state,
        onIntent = viewModel::handleIntent,
        onNavigateToSettings = onNavigateToSettings,
        snackbarHostState = snackbarHostState,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Content
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EcoHomeContent(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    onNavigateToSettings: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    var cameraTarget by remember { mutableStateOf<CameraTarget?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0),
        bottomBar = { EcoReportBar(onClick = { onIntent(HomeIntent.ReportTestSpot) }) },
        containerColor = Color.Transparent,
    ) { scaffoldPadding ->

        val scaffoldState = rememberBottomSheetScaffoldState()
        val sheetExpanded = scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded

        val density = LocalDensity.current
        // Map height = Y offset of the sheet's top edge from the scaffold top.
        // null until the first layout pass; fallback keeps the map filling all available space.
        val mapHeightDp by remember(density) {
            derivedStateOf {
                runCatching {
                    with(density) { scaffoldState.bottomSheetState.requireOffset().toDp() }
                }.getOrNull()
            }
        }

        // ── Helper lambdas for camera moves ───────────────────────────────
        fun moveToParkingSpot() {
            state.userParking?.let { p ->
                cameraTarget = CameraTarget(
                    lat = p.location.latitude, lon = p.location.longitude, zoom = 17f,
                    token = (cameraTarget?.token ?: 0) + 1,
                )
            }
        }

        fun moveToMidpoint() {
            val p = state.userParking ?: return
            val u = state.userLocation ?: return
            cameraTarget = CameraTarget(
                lat = (p.location.latitude + u.first) / 2.0,
                lon = (p.location.longitude + u.second) / 2.0,
                zoom = 15f,
                token = (cameraTarget?.token ?: 0) + 1,
            )
        }

        fun moveToUserLocation() {
            state.userLocation?.let { (lat, lon) ->
                cameraTarget = CameraTarget(
                    lat = lat, lon = lon, zoom = 17f,
                    token = (cameraTarget?.token ?: 0) + 1,
                )
            }
        }

        // ── Layout ────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
        ) {
            BottomSheetScaffold(
                modifier = Modifier.fillMaxSize(),
                scaffoldState = scaffoldState,
                containerColor = Color.Transparent,
                sheetPeekHeight = SheetPeekHeight,
                sheetContainerColor = MaterialTheme.colorScheme.surface,
                sheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                sheetDragHandle = {
                    EcoPeekHandle(
                        state = state,
                        onParkingClick = { moveToParkingSpot() },
                    )
                },
                sheetContent = {
                    EcoSheetContent(
                        state = state,
                        onIntent = onIntent,
                        onCameraMove = { lat, lon ->
                            cameraTarget = CameraTarget(
                                lat = lat, lon = lon, zoom = 17f,
                                token = (cameraTarget?.token ?: 0) + 1,
                            )
                        },
                        onParkingClick = { moveToParkingSpot() },
                    )
                },
            ) {
                // ── Map height tracks the sheet's top edge ────────────────
                Box(modifier = Modifier.fillMaxSize()) {

                    PlatformMap(
                        spots = state.nearbySpots,
                        userLocation = state.userGpsPoint,
                        userParking = state.userParking,
                        onSpotClick = {},
                        cameraTarget = cameraTarget,
                        contentPadding = PaddingValues(),
                        showMapControls = false,   // replaced by our custom FABs below
                        modifier = if (mapHeightDp != null)
                            Modifier.fillMaxWidth().height(mapHeightDp!! + 20.dp)
                        else
                            Modifier.fillMaxSize(),
                    )

                    // ── Custom FAB column — bottom-end, above peek handle ──
                    AnimatedVisibility(
                        visible = !sheetExpanded,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 14.dp, bottom = SheetPeekHeight + 12.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // 1. Go to parked vehicle (only when parked)
                            if (state.userParking != null) {
                                EcoMapFab(
                                    icon = Icons.Outlined.DirectionsCar,
                                    tint = EcoGreen,
                                    containerColor = EcoGreenMuted,
                                    onClick = { moveToParkingSpot() },
                                )
                            }
                            // 2. Go to midpoint between vehicle and user (only when both exist)
                            if (state.userParking != null && state.userLocation != null) {
                                EcoMapFab(
                                    icon = Icons.Outlined.Route,
                                    onClick = { moveToMidpoint() },
                                )
                            }
                            // 3. Go to user location
                            EcoMapFab(
                                icon = Icons.Outlined.MyLocation,
                                onClick = { moveToUserLocation() },
                            )
                        }
                    }

                    // ── Floating header ────────────────────────────────────
                    EcoFloatingHeader(
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
}
