@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.presentation.map.CameraTarget
import io.apptolast.paparcar.presentation.map.PlatformMap
import io.apptolast.paparcar.presentation.util.distanceMeters
import io.apptolast.paparcar.presentation.util.formatCoords
import io.apptolast.paparcar.presentation.util.formatDistance
import io.apptolast.paparcar.presentation.util.formatRelativeTime
import io.apptolast.paparcar.presentation.util.formatWalkTime
import io.apptolast.paparcar.ui.theme.AmberAccent
import io.apptolast.paparcar.ui.theme.EcoForest
import io.apptolast.paparcar.ui.theme.EcoGreen
import io.apptolast.paparcar.ui.theme.EcoGreenMuted
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_address_loading
import paparcar.composeapp.generated.resources.home_banner_accuracy
import paparcar.composeapp.generated.resources.home_cd_profile
import paparcar.composeapp.generated.resources.home_empty_subtitle
import paparcar.composeapp.generated.resources.home_empty_title
import paparcar.composeapp.generated.resources.home_fab_report_spot
import paparcar.composeapp.generated.resources.home_feed_activity
import paparcar.composeapp.generated.resources.home_feed_nearby
import paparcar.composeapp.generated.resources.home_permissions_button
import paparcar.composeapp.generated.resources.home_permissions_message
import paparcar.composeapp.generated.resources.home_spot_reported_by
import paparcar.composeapp.generated.resources.home_spot_status_free
import paparcar.composeapp.generated.resources.home_spot_status_occupied
import paparcar.composeapp.generated.resources.home_stats_free_spots_badge

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
                        modifier = Modifier.fillMaxSize(),
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

// ─────────────────────────────────────────────────────────────────────────────
// Reusable circular map FAB
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EcoMapFab(
    icon: ImageVector,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    containerColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        shadowElevation = 6.dp,
        modifier = Modifier.size(44.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Peek handle — deliberadamente minimalista (Google Maps style)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EcoPeekHandle(
    state: HomeState,
    onParkingClick: () -> Unit,
) {
    val freeCount = state.nearbySpots.count { it.isActive }

    Column(modifier = Modifier.fillMaxWidth()) {

        // Drag pill
        Box(
            modifier = Modifier
                .padding(top = 10.dp, bottom = 8.dp)
                .size(width = 32.dp, height = 4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                .align(Alignment.CenterHorizontally),
        )

        // Fila única: dirección + badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Outlined.LocationOn,
                contentDescription = null,
                tint = EcoGreen,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.userAddress?.displayLine
                        ?: stringResource(Res.string.home_address_loading),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.userAddress?.city != null) {
                    Text(
                        text = listOfNotNull(
                            state.userAddress.city,
                            state.userAddress.region,
                        ).joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        maxLines = 1,
                    )
                }
            }
            // Badge de spots libres
            Surface(
                color = if (freeCount > 0) EcoGreenMuted
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (freeCount > 0) EcoGreen
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            ),
                    )
                    Text(
                        stringResource(Res.string.home_stats_free_spots_badge, freeCount),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (freeCount > 0) EcoGreen
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Floating header — Eco-Driver pill opens a dropdown; each menu item is its
// own independent pill Surface so they look visually separate from each other.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EcoFloatingHeader(
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dropdownExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            // ── Eco-Driver identity pill ───────────────────────────────────
            Surface(
                onClick = { dropdownExpanded = true },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shadowElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(EcoGreenMuted),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Person,
                            contentDescription = stringResource(Res.string.home_cd_profile),
                            tint = EcoGreen,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                    Text(
                        "Eco-Driver",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Icon(
                        imageVector = if (dropdownExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // Inside EcoFloatingHeader (replace your DropdownMenu block):

            var item1Visible by remember { mutableStateOf(false) }
            var item2Visible by remember { mutableStateOf(false) }

// Stagger open AND close
            LaunchedEffect(dropdownExpanded) {
                if (dropdownExpanded) {
                    item1Visible = false
                    item2Visible = false
                    item1Visible = true          // item 1 slides in immediately
                    delay(90)
                    item2Visible = true          // item 2 follows 90 ms later
                } else {
                    item2Visible = false         // item 2 leaves first
                    delay(90)
                    item1Visible = false         // item 1 follows 90 ms later
                }
            }

            DropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false },
                containerColor = Color.Transparent,
                shadowElevation = 0.dp,
                modifier = Modifier.padding(horizontal = 4.dp),
            ) {
                AnimatedVisibility(
                    visible = item1Visible,
                    enter = slideInVertically(
                        initialOffsetY = { -it },          // comes from above
                        animationSpec = tween(220),
                    ) + fadeIn(tween(220)),
                    exit = slideOutVertically(
                        targetOffsetY = { -it },
                        animationSpec = tween(150),
                    ) + fadeOut(tween(150)),
                ) {
                    EcoDropdownPillItem(
                        icon = Icons.Outlined.History,
                        label = "Historial",
                        onClick = { dropdownExpanded = false; onHistoryClick() },
                    )
                }

                AnimatedVisibility(
                    visible = item2Visible,
                    enter = slideInVertically(
                        initialOffsetY = { -it },
                        animationSpec = tween(220),
                    ) + fadeIn(tween(220)),
                    exit = slideOutVertically(
                        targetOffsetY = { -it },
                        animationSpec = tween(150),
                    ) + fadeOut(tween(150)),
                ) {
                    EcoDropdownPillItem(
                        icon = Icons.Outlined.Settings,
                        label = "Ajustes",
                        onClick = { dropdownExpanded = false; onSettingsClick() },
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dropdown pill item — each one is its own Surface(CircleShape) so it has an
// independent background, identical in language to the Eco-Driver pill.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EcoDropdownPillItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        shadowElevation = 5.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(EcoGreenMuted),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = EcoGreen,
                    modifier = Modifier.size(12.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sheet content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EcoSheetContent(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    onCameraMove: (Double, Double) -> Unit,
    onParkingClick: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {

        // ── Parking row — solo si está aparcado ───────────────────────────
        state.userParking?.let { parking ->
            item {
                EcoParkingRow(
                    parking = parking,
                    address = null,
                    userLocation = state.userLocation,
                    onClick = onParkingClick,
                    modifier = Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 8.dp,
                    ),
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.07f),
                )
            }
        }

        // ── Sección: Cerca de ti ──────────────────────────────────────────
        item {
            EcoSectionHeader(
                title = stringResource(Res.string.home_feed_nearby),
                badge = if (state.nearbySpots.isNotEmpty())
                    stringResource(
                        Res.string.home_stats_free_spots_badge,
                        state.nearbySpots.count { it.isActive },
                    )
                else null,
                modifier = Modifier.padding(
                    start = 20.dp, end = 20.dp,
                    top = 16.dp, bottom = 8.dp,
                ),
            )
        }

        when {
            !state.allPermissionsGranted -> item {
                EcoPermissionsCard(
                    onRequestPermissions = { onIntent(HomeIntent.LoadNearbySpots) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            state.nearbySpots.isEmpty() -> item {
                EcoEmptySpots(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            else -> items(state.nearbySpots, key = { it.id }) { spot ->
                EcoSpotRow(
                    spot = spot,
                    address = state.spotAddresses[spot.id],
                    userLocation = state.userLocation,
                    onClick = { onCameraMove(spot.location.latitude, spot.location.longitude) },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.06f),
                )
            }
        }

        // ── Sección: Actividad ────────────────────────────────────────────
        if (state.nearbySpots.isNotEmpty()) {
            item {
                EcoSectionHeader(
                    title = stringResource(Res.string.home_feed_activity),
                    modifier = Modifier.padding(
                        start = 20.dp, end = 20.dp,
                        top = 24.dp, bottom = 8.dp,
                    ),
                )
            }
            items(state.nearbySpots.take(5), key = { "feed_${it.id}" }) { spot ->
                EcoActivityRow(
                    spot = spot,
                    address = state.spotAddresses[spot.id],
                    onClick = { onCameraMove(spot.location.latitude, spot.location.longitude) },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.06f),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Parking row — muestra AddressInfo (o coords como fallback).
// Al hacer tap la cámara anima directamente al punto de aparcamiento.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EcoParkingRow(
    parking: UserParking,
    address: AddressInfo?,
    userLocation: Pair<Double, Double>?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryLabel = address?.displayLine ?: "Tu vehículo"
    val distanceM = userLocation?.let { (uLat, uLon) ->
        distanceMeters(uLat, uLon, parking.location.latitude, parking.location.longitude)
    }

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = EcoGreenMuted.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Outlined.DirectionsCar,
                contentDescription = null,
                tint = EcoGreen,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = primaryLabel,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = EcoGreen,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        formatRelativeTime(parking.location.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = EcoGreen.copy(alpha = 0.6f),
                    )
                    if (distanceM != null) {
                        Text(
                            "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = EcoGreen.copy(alpha = 0.3f),
                        )
                        Icon(
                            Icons.AutoMirrored.Outlined.DirectionsWalk,
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = EcoGreen.copy(alpha = 0.5f),
                        )
                        Text(
                            "${formatDistance(distanceM)} · ${formatWalkTime(distanceM)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = EcoGreen.copy(alpha = 0.6f),
                        )
                    }
                }
            }
            Surface(shape = CircleShape, color = EcoGreenMuted) {
                Text(
                    stringResource(
                        Res.string.home_banner_accuracy,
                        parking.location.accuracy.toInt()
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = EcoGreen,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Spot row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EcoSpotRow(
    spot: Spot,
    address: AddressInfo?,
    userLocation: Pair<Double, Double>?,
    onClick: () -> Unit,
) {
    val isActive = spot.isActive
    val distanceM = userLocation?.let { (uLat, uLon) ->
        distanceMeters(uLat, uLon, spot.location.latitude, spot.location.longitude)
    }

    Surface(
        onClick = onClick,
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) EcoGreenMuted
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isActive) Icons.Outlined.RadioButtonChecked
                    else Icons.Outlined.DirectionsCar,
                    contentDescription = null,
                    tint = if (isActive) EcoGreen
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = address?.displayLine ?: formatCoords(
                        spot.location.latitude, spot.location.longitude,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (isActive) stringResource(Res.string.home_spot_status_free)
                        else stringResource(Res.string.home_spot_status_occupied),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isActive) EcoGreen else AmberAccent,
                    )
                    if (distanceM != null) {
                        Text(
                            "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        )
                        Icon(
                            Icons.AutoMirrored.Outlined.DirectionsWalk,
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                        Text(
                            "${formatDistance(distanceM)} · ${formatWalkTime(distanceM)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                    }
                }
            }

            Text(
                formatRelativeTime(spot.location.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Activity row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EcoActivityRow(
    spot: Spot,
    address: AddressInfo?,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.DirectionsCar,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = address?.displayLine ?: formatCoords(
                        spot.location.latitude, spot.location.longitude,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(Res.string.home_spot_reported_by, spot.reportedBy),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                formatRelativeTime(spot.location.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EcoSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            letterSpacing = 0.8.sp,
        )
        if (badge != null) {
            Text(
                badge,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = EcoGreen,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Report bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EcoReportBar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        color = EcoGreen,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Outlined.Campaign,
                    contentDescription = null,
                    tint = EcoForest,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(Res.string.home_fab_report_spot),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = EcoForest,
                    letterSpacing = 0.3.sp,
                )
            }
            // navBar inset below the content — keeps the text visually centred
            // within the clickable zone while the green extends behind the nav bar.
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Permissions card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EcoPermissionsCard(
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(Res.string.home_permissions_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onRequestPermissions,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = EcoGreen,
                    contentColor = EcoForest,
                ),
            ) {
                Text(
                    stringResource(Res.string.home_permissions_button),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EcoEmptySpots(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Outlined.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
            modifier = Modifier.size(32.dp),
        )
        Text(
            stringResource(Res.string.home_empty_title),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
        Text(
            stringResource(Res.string.home_empty_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
        )
    }
}