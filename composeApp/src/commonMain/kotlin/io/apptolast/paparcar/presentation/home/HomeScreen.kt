@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.presentation.home.components.PapFloatingHeader
import io.apptolast.paparcar.presentation.home.components.PapMapFabColumn
import io.apptolast.paparcar.presentation.home.components.PapPeekHandle
import io.apptolast.paparcar.presentation.home.components.PapReportBar
import io.apptolast.paparcar.presentation.home.components.PapSheetContent
import io.apptolast.paparcar.presentation.map.PlatformMap
import org.koin.compose.viewmodel.koinViewModel

// Peek = pill(22) + address row(74) = 96dp
private val SheetPeekHeight = 96.dp

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    onNavigateToSettings: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val uiController = rememberHomeUiController()

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

        val density = LocalDensity.current
        val mapHeightDp by remember(density) {
            derivedStateOf {
                runCatching {
                    with(density) {
                        uiController.scaffoldState.bottomSheetState.requireOffset().toDp()
                    }
                }.getOrNull()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
        ) {
            BottomSheetScaffold(
                modifier = Modifier.fillMaxSize(),
                scaffoldState = uiController.scaffoldState,
                containerColor = Color.Transparent,
                sheetPeekHeight = SheetPeekHeight,
                sheetContainerColor = MaterialTheme.colorScheme.surface,
                sheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                sheetDragHandle = {
                    PapPeekHandle(
                        state = state,
                        onParkingClick = {
                            state.userParking?.let { p ->
                                uiController.moveCamera(p.location.latitude, p.location.longitude)
                            }
                        },
                    )
                },
                sheetContent = {
                    PapSheetContent(
                        state = state,
                        onIntent = onIntent,
                        onCameraMove = { lat, lon -> uiController.moveCamera(lat, lon) },
                        onParkingClick = {
                            state.userParking?.let { p ->
                                uiController.moveCamera(p.location.latitude, p.location.longitude)
                            }
                        },
                    )
                },
            ) {
                Box(modifier = Modifier.fillMaxSize()) {

                    PlatformMap(
                        spots = state.nearbySpots,
                        userLocation = state.userGpsPoint,
                        parkingLocation = state.userParking?.location,
                        onSpotClick = {},
                        cameraTarget = uiController.cameraTarget,
                        modifier = if (mapHeightDp != null)
                            Modifier.fillMaxWidth().height(mapHeightDp!! + 20.dp)
                        else
                            Modifier.fillMaxSize(),
                    )

                    // ── Custom FAB column — bottom-end, above peek handle ──
                    AnimatedVisibility(
                        visible = !uiController.sheetExpanded,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 14.dp, bottom = SheetPeekHeight + 12.dp),
                    ) {
                        PapMapFabColumn(
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

                    // ── Floating header ────────────────────────────────────
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
}
