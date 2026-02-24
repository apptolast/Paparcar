import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.SpotLocation
import io.apptolast.paparcar.presentation.home.HomeEffect
import io.apptolast.paparcar.presentation.home.HomeIntent
import io.apptolast.paparcar.presentation.home.HomeState
import io.apptolast.paparcar.presentation.home.HomeViewModel
import org.koin.compose.viewmodel.koinViewModel

// Colores del nuevo diseño para fácil acceso
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
        snackbarHostState = snackbarHostState
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreenContent(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val fakeSpots = remember {
        val fakeCurrentTime = 1700000000000L
        listOf(
            Spot(
                id = "fake1",
                location = SpotLocation(40.416775, -3.703790, 10f, fakeCurrentTime - 120000, 0f),
                reportedBy = "Juan M.",
                isActive = true
            ),
            Spot(
                id = "fake2",
                location = SpotLocation(40.418775, -3.705790, 15f, fakeCurrentTime - 480000, 0f),
                reportedBy = "Elena S.",
                isActive = true
            ),
            Spot(
                id = "fake3",
                location = SpotLocation(40.414775, -3.701790, 20f, fakeCurrentTime - 60000, 0f),
                reportedBy = "Carlos P.",
                isActive = false // Ocupado
            )
        )
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
                text = { Text("Reportar spot", fontWeight = FontWeight.ExtraBold) }
            )
        },
//        bottomBar = { HomeBottomNavigation() },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp) // Espacio extra para el FAB
        ) {
            item {
                HomeHeader(
                    modifier = Modifier.statusBarsPadding().padding(
                        top = 8.dp,
                        start = 24.dp,
                        end = 24.dp,
                        bottom = 16.dp
                    )
                )
            }
            item { SearchBar(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) }
            item { StatsCarousel(modifier = Modifier.padding(vertical = 16.dp)) }
            item {
                QuickActions(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    onMapClick = { onIntent(HomeIntent.OpenMap) },
                    onHistoryClick = { onIntent(HomeIntent.OpenHistory) }
                )
            }
            item {
                SpotFeedHeader(
                    modifier = Modifier.padding(
                        horizontal = 24.dp,
                        vertical = 16.dp
                    )
                )
            }

            if (state.nearbySpots.isEmpty()) {

                items(fakeSpots, key = { it.id }) { spot ->
                    SpotFeedCard(
                        spot = spot,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        onLikeClick = { /* No action for fake items */ },
                        onClick = { /* No action for fake items */ }
                    )
                }
            } else {
                items(state.nearbySpots, key = { it.id }) { spot ->
                    SpotFeedCard(
                        spot = spot,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        onLikeClick = { /* TODO */ },
                        onClick = { onIntent(HomeIntent.SpotSelected(spot.id)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = "Perfil",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column {
                Text(
                    "¡Hola, Juan!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = PaparcarGreen.copy(alpha = 0.1f),
                    shape = CircleShape,
                    modifier = Modifier.border(1.dp, PaparcarGreen.copy(alpha = 0.2f), CircleShape)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Stars,
                            contentDescription = null,
                            tint = PaparcarGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            "3 spots hoy",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = PaparcarGreen,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
            }
        }
        IconButton(onClick = { /*TODO*/ }) {
            Icon(
                Icons.Outlined.Notifications,
                contentDescription = "Notificaciones",
                tint = PaparcarGreen
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
        placeholder = {
            Text(
                "Busca una zona, calle…",
                style = MaterialTheme.typography.bodyLarge
            )
        },
        leadingIcon = {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = PaparcarGreen
            )
        },
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        ),
        singleLine = true
    )
}

@Composable
private fun StatsCarousel(modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            StatCard(
                title = "Spots libres",
                value = "3 a 200m",
                icon = Icons.Outlined.LocationOn,
                isPrimary = true
            )
        }
        item {
            StatCard(
                title = "Ahorras",
                value = "5 min",
                icon = Icons.Outlined.Timer,
                isPrimary = false
            )
        }
        item {
            StatCard(
                title = "Has ayudado",
                value = "12 personas",
                icon = Icons.Outlined.VolunteerActivism,
                isPrimary = false
            )
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPrimary: Boolean
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
        border = if (isPrimary) BorderStroke(1.dp, PaparcarGreen.copy(alpha = 0.2f)) else null
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape)
                    .background(contentColor.copy(alpha = if (isPrimary) 0.2f else 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = contentColor
                )
            }
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.bodySmall,
                    color = titleColor,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
private fun QuickActions(
    modifier: Modifier = Modifier,
    onMapClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onMapClick,
            modifier = Modifier.weight(1f).height(52.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Icon(Icons.Outlined.History, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Mi historial", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun SpotFeedHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "Cerca de ti",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold
        )
        TextButton(onClick = { /*TODO*/ }) {
            Text("Ver todo", fontWeight = FontWeight.Bold, color = PaparcarGreen, fontSize = 14.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpotFeedCard(
    spot: Spot,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLikeClick: () -> Unit
) {
    val isOccupied = !spot.isActive
    val cardAlpha = if (isOccupied) 0.7f else 1f
    val contentColor =
        if (isOccupied) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface

    Card(
        onClick = { if (!isOccupied) onClick() },
        modifier = modifier.fillMaxWidth().graphicsLayer(alpha = cardAlpha),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOccupied) MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = 0.5f
            ) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val address = when (spot.id) {
                        "fake1" -> "Calle Velázquez, 42"
                        "fake2" -> "Paseo de la Castellana, 12"
                        else -> "Calle Jorge Juan, 10"
                    }
                    Text(
                        text = address,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )

                    val distance = when (spot.id) {
                        "fake1" -> "a 150m"
                        "fake2" -> "a 450m"
                        else -> "a 280m"
                    }
                    val walkTime = if (isOccupied) "3 min" else when (spot.id) {
                        "fake1" -> "2 min"; else -> "5 min"
                    }
                    val detailColor =
                        if (isOccupied) contentColor else MaterialTheme.colorScheme.onSurfaceVariant

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.DirectionsWalk,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = detailColor
                        )
                        Text(
                            text = walkTime,
                            style = MaterialTheme.typography.bodySmall,
                            color = detailColor
                        )
                        Box(
                            modifier = Modifier.size(4.dp).clip(CircleShape)
                                .background(detailColor.copy(alpha = 0.4f))
                        )
                        Text(
                            text = distance,
                            style = MaterialTheme.typography.bodySmall,
                            color = detailColor
                        )
                    }
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val statusText = if (isOccupied) "Ocupado" else "Libre"
                    val statusColor = if (isOccupied) contentColor else PaparcarGreen
                    val statusContainerColor =
                        if (isOccupied) MaterialTheme.colorScheme.surface else PaparcarGreen.copy(
                            alpha = 0.1f
                        )

                    Surface(color = statusContainerColor, shape = CircleShape) {
                        Text(
                            text = statusText,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            letterSpacing = 0.5.sp
                        )
                    }

                    val timeAgo = when (spot.id) {
                        "fake1" -> "hace 2 min"
                        "fake2" -> "hace 8 min"
                        else -> "hace 1 min"
                    }
                    Text(
                        text = timeAgo,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            if (!isOccupied) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(24.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                        )
                        Text(
                            "Por ${spot.reportedBy}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Button(
                        onClick = onLikeClick,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.ThumbUp,
                            contentDescription = "Likes",
                            tint = PaparcarGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        val likes = when (spot.id) {
                            "fake1" -> "28"
                            "fake2" -> "14"
                            else -> "0"
                        }
                        Text(likes, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeBottomNavigation() {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        tonalElevation = 0.dp
    ) {
        val selectedItem = "Inicio"
        NavigationBarItem(
            selected = selectedItem == "Inicio",
            onClick = { /* TODO */ },
            icon = {
                Icon(
                    if (selectedItem == "Inicio") Icons.Filled.Home else Icons.Outlined.Home,
                    contentDescription = "Inicio"
                )
            },
            label = {
                Text(
                    "Inicio",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = PaparcarGreen,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedTextColor = PaparcarGreen,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                indicatorColor = PaparcarGreen.copy(alpha = 0.1f)
            )
        )
        NavigationBarItem(
            selected = selectedItem == "Mapa",
            onClick = { /* TODO */ },
            icon = { Icon(Icons.Outlined.Map, contentDescription = "Mapa") },
            label = {
                Text(
                    "Mapa",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = PaparcarGreen,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedTextColor = PaparcarGreen,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                indicatorColor = PaparcarGreen.copy(alpha = 0.1f)
            )
        )
        NavigationBarItem(
            selected = selectedItem == "Historial",
            onClick = { /* TODO */ },
            icon = { Icon(Icons.Outlined.History, contentDescription = "Historial") },
            label = {
                Text(
                    "Historial",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = PaparcarGreen,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedTextColor = PaparcarGreen,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                indicatorColor = PaparcarGreen.copy(alpha = 0.1f)
            )
        )
        NavigationBarItem(
            selected = selectedItem == "Perfil",
            onClick = { /* TODO */ },
            icon = { Icon(Icons.Outlined.Person, contentDescription = "Perfil") },
            label = {
                Text(
                    "Perfil",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = PaparcarGreen,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedTextColor = PaparcarGreen,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                indicatorColor = PaparcarGreen.copy(alpha = 0.1f)
            )
        )
    }
}
