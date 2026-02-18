import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.apptolast.paparcar.presentation.home.HomeEffect
import io.apptolast.paparcar.presentation.home.HomeViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel = koinViewModel()) {

    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Manejar efectos
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is HomeEffect.ShowError -> {
                    snackbarHostState.showSnackbar(effect.message)
                }

                is HomeEffect.ShowSuccess -> {
                    snackbarHostState.showSnackbar(effect.message)
                }

                is HomeEffect.NavigateToMap -> {
                    // TODO: Navegar a pantalla de mapa
                }

                is HomeEffect.RequestLocationPermission -> {
                    // TODO: Solicitar permisos de ubicación
                }
            }
        }
    }

    Scaffold(
        topBar = {
            HomeHeader("Hola, Renedo!")
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { /* Reportar spot */ },
                icon = { Icon(Icons.Default.AddLocation, contentDescription = null) },
                text = { Text("Reportar spot") },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        },
        bottomBar = {
            HomeBottomBar()
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item { SearchBarSection() }
            item { StatsCarousel() }
            item { QuickActions() }
            item {
                Text(
                    "Spots recientes",
                    style = MaterialTheme.typography.titleMedium
                )
            }
// Simulación de Infinite Scroll / Feed
            items(10) { index ->
                SpotCard(
                    calle = "Calle Velázquez, a ${150 + index * 10}m",
                    tiempo = "hace ${index + 2} min",
                    usuario = "Juan M.",
                    reacciones = 28 - index
                )
            }
        }
    }
}

@Composable
fun HomeHeader(nombre: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "¡Hola, $nombre!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Surface(
                color =
                    MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "12 spots reportados hoy",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        Box(
            modifier = Modifier.size(45.dp).clip(CircleShape)
                .background(Color.Gray), contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = "Perfil",
                tint = Color.White
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBarSection() {
    TextField(
        value = "",
        onValueChange = {},
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Busca una zona, calle...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
}

@Composable
fun StatsCarousel() {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val stats = listOf(
            "3 spots libres a 200m",
            "Ahorras 5 min buscando",
            "Has ayudado a 12 personas"
        )
        items(stats) { stat ->
            Card(
                modifier = Modifier.width(200.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    stat,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun QuickActions() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(onClick = {}, modifier = Modifier.weight(1f)) {
            Icon(
                Icons.Default.Map,
                null
            )
            Spacer(Modifier.width(8.dp))
            Text("Ver mapa")
        }
        OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) {
            Icon(
                Icons.Default.History,
                null
            )
            Spacer(Modifier.width(8.dp))
            Text("Historial")
        }
    }
}

@Composable
fun SpotCard(calle: String, tiempo: String, usuario: String, reacciones: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                calle,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tiempo,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Text(" • ", color = Color.Gray)
                Text("Por $usuario", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ThumbUp,
                    contentDescription = null,
                )
                Spacer(Modifier.width(4.dp))
                Text("$reacciones", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun HomeBottomBar() {
    NavigationBar {
        NavigationBarItem(
            selected = true,
            onClick = {},
            icon = { Icon(Icons.Default.Home, "Home") },
            label = { Text("Home") })
        NavigationBarItem(selected = false, onClick = {}, icon = {
            Icon(
                Icons.Default.Map,
                "Map"
            )
        }, label = { Text("Map") })
        NavigationBarItem(selected = false, onClick = {}, icon = {
            Icon(
                Icons.Default.Add,
                "Report"
            )
        })
        NavigationBarItem(selected = false, onClick = {}, icon = {
            Icon(
                Icons.Default.History,
                "History"
            )
        }, label = { Text("History") })
        NavigationBarItem(selected = false, onClick = {}, icon = {
            Icon(
                Icons.Default.Person,
                "Profile"
            )
        }, label = { Text("Profile") })
    }
}