@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Stars
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.apptolast.paparcar.domain.model.AddressInfo
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.presentation.util.distanceMeters
import io.apptolast.paparcar.presentation.util.formatCoords
import io.apptolast.paparcar.presentation.util.formatDistance
import io.apptolast.paparcar.presentation.util.formatRelativeTime
import io.apptolast.paparcar.presentation.util.formatWalkTime
import io.apptolast.paparcar.presentation.util.greetingByHour
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.Instant

private val PaparcarGreen = Color(0xFF13EC5B)
private val DarkBackground = Color(0xFF102216)

@Composable
fun HomeScreen(
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
                is HomeEffect.RequestLocationPermission -> { /* handled by platform */ }
            }
        }
    }

    HomeScreenContent(
        state = state,
        onIntent = viewModel::handleIntent,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
private fun HomeScreenContent(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val currentHour = remember {
        Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .hour
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onIntent(HomeIntent.ReportTestSpot) },
                containerColor = PaparcarGreen,
                contentColor = DarkBackground,
                shape = CircleShape,
                icon = { Icon(Icons.Outlined.AddCircle, contentDescription = null) },
                text = { Text("Reportar spot", fontWeight = FontWeight.ExtraBold) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            item {
                HomeHeader(
                    greeting = greetingByHour(currentHour),
                    activeSpotCount = state.nearbySpots.count { it.isActive },
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(top = 8.dp, start = 24.dp, end = 24.dp, bottom = 16.dp),
                )
            }
            item { SearchBar(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) }
            item {
                StatsCarousel(
                    activeSpots = state.nearbySpots.count { it.isActive },
                    totalSpots = state.nearbySpots.size,
                    nearestDistance = state.userLocation?.let { (userLat, userLon) ->
                        state.nearbySpots
                            .filter { it.isActive }
                            .minOfOrNull { spot ->
                                distanceMeters(userLat, userLon, spot.location.latitude, spot.location.longitude)
                            }
                    },
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
            item {
                QuickActions(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    onMapClick = { onIntent(HomeIntent.OpenMap) },
                    onHistoryClick = { onIntent(HomeIntent.OpenHistory) },
                )
            }

            state.userParking?.let { parking ->
                item {
                    ActiveParkingBanner(
                        timestampMs = parking.location.timestamp,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            }

            item {
                SpotFeedHeader(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }

            if (!state.allPermissionsGranted) {
                item {
                    PermissionsRequestCard(
                        onRequestPermissions = { onIntent(HomeIntent.LoadNearbySpots) },
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            } else if (state.nearbySpots.isEmpty()) {
                item {
                    EmptySpotState(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
                    )
                }
            } else {
                items(state.nearbySpots, key = { it.id }) { spot ->
                    SpotFeedCard(
                        spot = spot,
                        address = state.spotAddresses[spot.id],
                        userLocation = state.userLocation,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        onClick = { onIntent(HomeIntent.SpotSelected(spot.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    greeting: String,
    activeSpotCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = "Perfil",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column {
                Text(
                    greeting,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Surface(
                    color = PaparcarGreen.copy(alpha = 0.1f),
                    shape = CircleShape,
                    modifier = Modifier.border(1.dp, PaparcarGreen.copy(alpha = 0.2f), CircleShape),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Stars,
                            contentDescription = null,
                            tint = PaparcarGreen,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            "$activeSpotCount spots libres",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = PaparcarGreen,
                            letterSpacing = 0.8.sp,
                        )
                    }
                }
            }
        }
        IconButton(onClick = { }) {
            Icon(
                Icons.Outlined.Notifications,
                contentDescription = "Notificaciones",
                tint = PaparcarGreen,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(modifier: Modifier = Modifier) {
    TextField(
        value = "",
        onValueChange = {},
        modifier = modifier.fillMaxWidth().height(56.dp),
        placeholder = { Text("Busca una zona, calle…", style = MaterialTheme.typography.bodyLarge) },
        leadingIcon = {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = PaparcarGreen)
        },
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        singleLine = true,
    )
}

@Composable
private fun StatsCarousel(
    activeSpots: Int,
    totalSpots: Int,
    nearestDistance: Float?,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            StatCard(
                title = "Spots libres",
                value = "$activeSpots cerca",
                icon = Icons.Outlined.LocationOn,
                isPrimary = true,
            )
        }
        item {
            StatCard(
                title = "Más cercano",
                value = nearestDistance?.let { formatDistance(it) } ?: "—",
                icon = Icons.Outlined.Timer,
                isPrimary = false,
            )
        }
        item {
            StatCard(
                title = "Reportados",
                value = "$totalSpots total",
                icon = Icons.Outlined.NearMe,
                isPrimary = false,
            )
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPrimary: Boolean,
) {
    val containerColor =
        if (isPrimary) PaparcarGreen.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isPrimary) PaparcarGreen else MaterialTheme.colorScheme.onSurface
    val titleColor =
        if (isPrimary) PaparcarGreen.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier.width(192.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = if (isPrimary) BorderStroke(1.dp, PaparcarGreen.copy(alpha = 0.2f)) else null,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape)
                    .background(contentColor.copy(alpha = if (isPrimary) 0.2f else 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = contentColor,
                )
            }
            Column {
                Text(title, style = MaterialTheme.typography.bodySmall, color = titleColor, fontWeight = FontWeight.Medium)
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = contentColor)
            }
        }
    }
}

@Composable
private fun QuickActions(
    modifier: Modifier = Modifier,
    onMapClick: () -> Unit,
    onHistoryClick: () -> Unit,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onMapClick,
            modifier = Modifier.weight(1f).height(52.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Icon(Icons.Outlined.Map, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Ver mapa", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Button(
            onClick = onHistoryClick,
            modifier = Modifier.weight(1f).height(52.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Icon(Icons.Outlined.History, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Mi historial", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ActiveParkingBanner(
    timestampMs: Long,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.DirectionsCar,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column {
                Text(
                    "Tu coche está aparcado",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "Desde ${formatRelativeTime(timestampMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun SpotFeedHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Cerca de ti", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        TextButton(onClick = { }) {
            Text("Ver todo", fontWeight = FontWeight.Bold, color = PaparcarGreen, fontSize = 14.sp)
        }
    }
}

@Composable
private fun PermissionsRequestCard(
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                Icons.Outlined.LocationOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
            Text(
                "Activa la ubicación para ver spots cerca de ti",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onRequestPermissions,
                shape = CircleShape,
            ) {
                Text("Activar permisos", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EmptySpotState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Outlined.NearMe,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(64.dp),
        )
        Text(
            "No hay spots cerca ahora",
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

@Composable
private fun SpotFeedCard(
    spot: Spot,
    address: AddressInfo?,
    userLocation: Pair<Double, Double>?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val isOccupied = !spot.isActive
    val cardAlpha = if (isOccupied) 0.7f else 1f
    val contentColor = if (isOccupied)
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    else
        MaterialTheme.colorScheme.onSurface

    var isExpanded by remember { mutableStateOf(false) }

    val distanceM = userLocation?.let { (userLat, userLon) ->
        distanceMeters(userLat, userLon, spot.location.latitude, spot.location.longitude)
    }

    Card(
        onClick = { if (!isOccupied) onClick() },
        modifier = modifier.fillMaxWidth().graphicsLayer(alpha = cardAlpha),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOccupied)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Always-visible header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = address?.displayLine
                        ?: formatCoords(spot.location.latitude, spot.location.longitude),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                    )

                    if (distanceM != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            val detailColor = if (isOccupied)
                                contentColor
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                            Icon(
                                Icons.AutoMirrored.Outlined.DirectionsWalk,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = detailColor,
                            )
                            Text(
                                text = formatWalkTime(distanceM),
                                style = MaterialTheme.typography.bodySmall,
                                color = detailColor,
                            )
                            Box(
                                modifier = Modifier.size(4.dp).clip(CircleShape)
                                    .background(detailColor.copy(alpha = 0.4f)),
                            )
                            Text(
                                text = formatDistance(distanceM),
                                style = MaterialTheme.typography.bodySmall,
                                color = detailColor,
                            )
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val statusText = if (isOccupied) "Ocupado" else "Libre"
                    val statusColor = if (isOccupied) contentColor else PaparcarGreen
                    val statusContainerColor = if (isOccupied)
                        MaterialTheme.colorScheme.surface
                    else
                        PaparcarGreen.copy(alpha = 0.1f)

                    Surface(color = statusContainerColor, shape = CircleShape) {
                        Text(
                            text = statusText,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            letterSpacing = 0.5.sp,
                        )
                    }

                    Text(
                        text = formatRelativeTime(spot.location.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }

            // Bottom row: reporter + expand toggle
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(24.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    )
                    Text(
                        "Por ${spot.reportedBy}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (isExpanded) "Colapsar" else "Expandir",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Expandable address section
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    Spacer(Modifier.height(4.dp))

                    if (address == null) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(2.dp),
                            color = PaparcarGreen,
                        )
                        Text(
                            "Obteniendo dirección…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        if (address.street != null) {
                            Text(
                                address.street,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        val cityRegion = listOfNotNull(address.city, address.region).joinToString(", ")
                        if (cityRegion.isNotBlank()) {
                            Text(
                                cityRegion,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (address.country != null) {
                            Text(
                                address.country,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                    Text(
                        formatCoords(spot.location.latitude, spot.location.longitude, decimals = 6),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}
