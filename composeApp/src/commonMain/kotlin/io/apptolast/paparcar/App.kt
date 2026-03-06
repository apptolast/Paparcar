package io.apptolast.paparcar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.apptolast.paparcar.presentation.history.HistoryScreen
import io.apptolast.paparcar.presentation.home.HomeScreen
import io.apptolast.paparcar.presentation.map.MapScreen
import io.apptolast.paparcar.presentation.settings.SettingsScreen
import io.apptolast.paparcar.ui.theme.PaparcarTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

private object Routes {
    const val HOME = "home"
    const val MAP = "map"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
}

@Composable
fun App() {
    PaparcarTheme {
        // Transparent so the map extends behind the status bar (edge-to-edge).
        // Each screen's Scaffold draws its own background.
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
            val navController = rememberNavController()

            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onNavigateToMap = { navController.navigate(Routes.MAP) },
                        onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                        onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                    )
                }
                composable(
                    route = "${Routes.MAP}?lat={lat}&lon={lon}",
                    arguments = listOf(
                        navArgument("lat") { type = NavType.StringType; defaultValue = "" },
                        navArgument("lon") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { backStack ->
                    val lat = backStack.arguments?.getString("lat")?.toDoubleOrNull()
                    val lon = backStack.arguments?.getString("lon")?.toDoubleOrNull()
                    MapScreen(
                        onNavigateBack = { navController.popBackStack() },
                        initialFocus = if (lat != null && lon != null) Pair(lat, lon) else null,
                    )
                }
                composable(Routes.HISTORY) {
                    HistoryScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToMap = { lat, lon ->
                            navController.navigate("${Routes.MAP}?lat=$lat&lon=$lon")
                        },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onNavigateBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun AppPreview() {
    App()
}
