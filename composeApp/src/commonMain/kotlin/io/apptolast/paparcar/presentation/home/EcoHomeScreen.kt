@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.ElectricCar
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Stars
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.SpotLocation
import io.apptolast.paparcar.domain.model.UserParkingSession
import io.apptolast.paparcar.presentation.map.PlatformMap
import io.apptolast.paparcar.presentation.util.distanceMeters
import io.apptolast.paparcar.presentation.util.formatCoords
import io.apptolast.paparcar.presentation.util.formatDistance
import io.apptolast.paparcar.presentation.util.formatRelativeTime
import io.apptolast.paparcar.presentation.util.formatWalkTime
import org.koin.compose.viewmodel.koinViewModel

// ── Design System — brand colors (fixed regardless of theme) ─────────────────
private val EcoForest = Color(0xFF0D1C14)
private val EcoForestCard = Color(0xFF102219)
private val EcoGreen = Color(0xFF25F48C)
private val EcoMintBorder = Color(0xFFD0EBD9)
private val EcoGreenMuted = Color(0xFF133D28)
private val EcoGreenElement = Color(0xFF226D49)

// Warm amber accent — used for "occupied" state to contrast with eco-green
private val AmberMuted = Color(0xFF3D2A10)
private val AmberAccent = Color(0xFFF4A825)

// ─────────────────────────────────────────────────────────────────────────────
// Root composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun EcoHomeScreen(
    onNavigateToMap: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
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
                is HomeEffect.RequestLocationPermission -> { /* handled by platform */
                }
            }
        }
    }

    EcoHomeContent(
        state = state,
        onIntent = viewModel::handleIntent,
        snackbarHostState = snackbarHostState,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Content — stateless layout
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EcoHomeContent(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val activeSpots = state.nearbySpots.count { it.isActive }
    var mapExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { EcoHeader(scrollBehavior = scrollBehavior) },
        // ── Single FAB — no duplicate button below ──
        floatingActionButton = {
            // Only shown when map is NOT expanded (would overlap map controls)
            if (!mapExpanded) {
                androidx.compose.material3.FloatingActionButton(
                    onClick = { onIntent(HomeIntent.ReportTestSpot) },
                    containerColor = EcoGreen,
                    contentColor = EcoForest,
                    shape = CircleShape,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Campaign,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            "Reportar",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            letterSpacing = 0.3.sp,
                        )
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
            userScrollEnabled = !mapExpanded,
        ) {

            // ── Impact stats ──────────────────────────────────────────────
            item {
                EcoImpactStats(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                )
            }

            // ── Weekly green goal ─────────────────────────────────────────
            item {
                EcoWeeklyGoal(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                )
            }

            // ── Active parking banner ─────────────────────────────────────
            state.userParking?.let { parking ->
                item {
                    EcoParkingBanner(
                        timestampMs = parking.timestamp,
                        onReleaseSpot = { onIntent(HomeIntent.ReleaseParking) },
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    )
                }
            }

            // ── Nearby spots header ───────────────────────────────────────
            item {
                EcoNearbySpotsHeader(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                )
            }

            // ── Map + address bar ─────────────────────────────────────────
            item {
                EcoMapSection(
                    spots = state.nearbySpots,
                    userSpotLocation = state.userSpotLocation,
                    userParking = state.userParking,
                    userAddress = state.userAddress,
                    activeSpotCount = activeSpots,
                    onSpotClick = { spotId -> onIntent(HomeIntent.SpotSelected(spotId)) },
                    expanded = mapExpanded,
                    onToggleExpand = { mapExpanded = !mapExpanded },
                )
            }

            // ── Below-map content (permissions / empty / cards) ───────────
            when {
                !state.allPermissionsGranted -> item {
                    EcoPermissionsCard(
                        onRequestPermissions = { onIntent(HomeIntent.LoadNearbySpots) },
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                    )
                }

                state.nearbySpots.isEmpty() -> item {
                    EcoEmptySpots(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                    )
                }

                else -> items(state.nearbySpots, key = { it.id }) { spot ->
                    EcoSpotCard(
                        spot = spot,
                        address = state.spotAddresses[spot.id],
                        userLocation = state.userLocation,
                        // First card: flat top (connects under address bar)
                        // Subsequent cards: flat top + flat bottom to stack flush
                        // Last card: rounded bottom
                        isFirst = state.nearbySpots.first() == spot,
                        isLast = state.nearbySpots.last() == spot,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        onClick = { onIntent(HomeIntent.SpotSelected(spot.id)) },
                    )
                }
            }

            // ── Recent activity ───────────────────────────────────────────
            if (state.nearbySpots.isNotEmpty()) {
                item {
                    EcoActivityHeader(
                        modifier = Modifier.padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 24.dp,
                            bottom = 12.dp,
                        ),
                    )
                }

                items(state.nearbySpots.take(3), key = { "feed_${it.id}" }) { spot ->
                    EcoFeedItem(
                        spot = spot,
                        address = state.spotAddresses[spot.id],
                        onLike = { /*onIntent(HomeIntent.LikeSpot(spot.id))*/ },
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EcoHeader(
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
) {
    TopAppBar(
        navigationIcon = {
            Box(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = "Perfil",
                    tint = EcoGreenElement,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "URBANPARK",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = EcoGreenElement,
                    letterSpacing = 1.2.sp,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "Eco-Driver",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Icon(
                        Icons.Outlined.Verified,
                        contentDescription = null,
                        tint = EcoGreen,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        },
        actions = {
            Box {
                IconButton(onClick = {}) {
                    Icon(
                        Icons.Outlined.Notifications,
                        contentDescription = "Notificaciones",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                // Badge with count — replace 3 with real unread count from state
                Badge(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp),
                    containerColor = EcoGreen,
                    contentColor = EcoForest,
                ) {
                    Text(
                        "3",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
            scrolledContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
        ),
        scrollBehavior = scrollBehavior,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Impact Stats — 2-column cards WITH proper card containers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EcoImpactStats(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EcoStatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.Eco,
            label = "CO₂ Ahorrado",
            value = "12.4 kg",
            subIcon = Icons.AutoMirrored.Outlined.TrendingUp,
            subLabel = "+15% esta semana",
        )
        EcoStatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.ElectricCar,
            label = "Eco Puntos",
            value = "850",
            subIcon = Icons.Outlined.Stars,
            subLabel = "Puesto #12",
        )
    }
}

@Composable
private fun EcoStatCard(
    icon: ImageVector,
    label: String,
    value: String,
    subIcon: ImageVector,
    subLabel: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, EcoMintBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = EcoGreenElement,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )
            }
            Text(
                value,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = (-0.5).sp,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(
                    subIcon,
                    contentDescription = null,
                    tint = EcoGreenElement,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    subLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = EcoGreenElement,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Weekly Green Goal
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EcoWeeklyGoal(modifier: Modifier = Modifier) {
    val progress = 0.75f

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = EcoGreenMuted),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Icon(
                Icons.Outlined.EmojiEvents,
                contentDescription = null,
                tint = EcoGreen.copy(alpha = 0.08f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(64.dp),
            )

            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            "Objetivo Semanal",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                        )
                        Text(
                            "Reduce la congestión urbana",
                            style = MaterialTheme.typography.bodySmall,
                            color = EcoGreen.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Text(
                        "750/1000 pts",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }

                Spacer(Modifier.height(14.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(CircleShape),
                    color = EcoGreen,
                    trackColor = Color.White.copy(alpha = 0.1f),
                    gapSize = 0.dp,
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text("🎉", fontSize = 13.sp)
                    Text(
                        "¡Casi! Estás en el top 5% de conductores",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = EcoGreen,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Parking Banner — now with "Liberar spot" action
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EcoParkingBanner(
    timestampMs: Long,
    onReleaseSpot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = EcoForestCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, EcoGreenMuted),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(EcoGreenMuted),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.DirectionsCar,
                        contentDescription = null,
                        tint = EcoGreen,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        "Tu coche está aparcado",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        "Desde ${formatRelativeTime(timestampMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                }
                Surface(color = EcoGreenMuted, shape = CircleShape) {
                    Text(
                        "Activo",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = EcoGreen,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Liberar spot ──
            Button(
                onClick = onReleaseSpot,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EcoGreen,
                    contentColor = EcoForest,
                ),
                contentPadding = PaddingValues(vertical = 10.dp),
            ) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "He dejado el sitio libre",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Nearby Spots header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EcoNearbySpotsHeader(modifier: Modifier = Modifier) {
    Text(
        "Spots cercanos",
        modifier = modifier,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Map Section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EcoMapSection(
    spots: List<Spot>,
    userSpotLocation: SpotLocation?,
    userParking: UserParkingSession?,
    userAddress: AddressInfo?,
    activeSpotCount: Int,
    onSpotClick: (String) -> Unit,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mapHeight by animateDpAsState(
        targetValue = if (expanded) 480.dp else 260.dp,
        label = "mapHeight",
    )
    val horizontalPadding by animateDpAsState(
        targetValue = if (expanded) 0.dp else 16.dp,
        label = "mapPadding",
    )

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = horizontalPadding)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(mapHeight)
                .clip(
                    RoundedCornerShape(
                        topStart = 14.dp,
                        topEnd = 14.dp,
                        bottomStart = 0.dp,
                        bottomEnd = 0.dp,
                    )
                ),
        ) {
            PlatformMap(
                spots = spots,
                userLocation = userSpotLocation,
                userParking = userParking,
                onSpotClick = onSpotClick,
                modifier = Modifier.fillMaxSize(),
            )

            IconButton(
                onClick = onToggleExpand,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.9f)),
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                    contentDescription = if (expanded) "Reducir mapa" else "Ampliar mapa",
                    tint = EcoForest,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Address bar — connects map to spot cards below
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Outlined.LocationOn,
                contentDescription = null,
                tint = EcoGreen,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = userAddress?.displayLine ?: "Obteniendo ubicación...",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (userAddress?.city != null) {
                    Text(
                        text = listOfNotNull(
                            userAddress.city,
                            userAddress.region
                        ).joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Surface(color = EcoGreenMuted, shape = CircleShape) {
                Text(
                    "$activeSpotCount libres",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = EcoGreen,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Spot Card — stacks flush under the address bar and between each other
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EcoSpotCard(
    spot: Spot,
    address: AddressInfo?,
    userLocation: Pair<Double, Double>?,
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val isActive = spot.isActive
    val distanceM = userLocation?.let { (uLat, uLon) ->
        distanceMeters(uLat, uLon, spot.location.latitude, spot.location.longitude)
    }

    val shape = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 0.dp,
        bottomStart = if (isLast) 14.dp else 0.dp,
        bottomEnd = if (isLast) 14.dp else 0.dp,
    )

    Card(
        onClick = { if (isActive) onClick() },
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, EcoMintBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Status dot
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) EcoGreen.copy(0.12f)
                        else AmberMuted.copy(alpha = 0.4f)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.LocationOn,
                    contentDescription = if (isActive) "Spot libre" else "Spot ocupado",
                    tint = if (isActive) EcoGreenElement else AmberAccent.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = address?.displayLine ?: formatCoords(
                        spot.location.latitude,
                        spot.location.longitude,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (distanceM != null) {
                        Icon(
                            Icons.AutoMirrored.Outlined.DirectionsWalk,
                            contentDescription = null,
                            modifier = Modifier.size(11.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                        Text(
                            "${formatDistance(distanceM)} · ${formatWalkTime(distanceM)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                        Text(
                            "·",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                    }
                    Text(
                        formatRelativeTime(spot.location.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }

            // Status pill — green libre / amber ocupado
            Surface(
                color = if (isActive) EcoGreenMuted else AmberMuted,
                shape = CircleShape,
            ) {
                Text(
                    text = if (isActive) "Libre" else "Ocupado",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) EcoGreen else AmberAccent,
                )
            }
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
        shape = RoundedCornerShape(0.dp, 0.dp, 14.dp, 14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Activa la ubicación para ver spots cerca de ti",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
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
                Text("Activar permisos", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EcoEmptySpots(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp, 0.dp, 14.dp, 14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Sin spots por aquí",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Sé el primero en reportar uno",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Activity header (renamed from "Live Updates")
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EcoActivityHeader(modifier: Modifier = Modifier) {
    Text(
        "Actividad reciente",
        modifier = modifier,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Feed Item — like now dispatches an intent
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EcoFeedItem(
    spot: Spot,
    address: AddressInfo?,
    onLike: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Local optimistic state — toggled immediately, synced via ViewModel
    var liked by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, EcoMintBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.ElectricCar,
                    contentDescription = null,
                    tint = EcoGreenElement,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    "Spot reportado por ${spot.reportedBy}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = address?.displayLine ?: formatCoords(
                        spot.location.latitude,
                        spot.location.longitude,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Like — dispatches intent on click
                    Row(
                        modifier = Modifier.clickable {
                            liked = !liked
                            if (liked) onLike()
                        },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Outlined.ThumbUp,
                            contentDescription = if (liked) "Quitar like" else "Dar like",
                            modifier = Modifier.size(15.dp),
                            tint = if (liked) EcoGreenElement else MaterialTheme.colorScheme.onSurface.copy(
                                alpha = 0.4f
                            ),
                        )
                        Text(
                            if (liked) "25" else "24",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (liked) EcoGreenElement else MaterialTheme.colorScheme.onSurface.copy(
                                alpha = 0.4f
                            ),
                        )
                    }

                    // Comentarios
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Chat,
                            contentDescription = "Comentarios",
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                        Text(
                            "3",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                    }
                }
            }
            Text(
                formatRelativeTime(spot.location.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
        }
    }
}