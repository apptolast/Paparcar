package io.apptolast.paparcar

import HomeScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.apptolast.paparcar.presentation.history.HistoryScreen
import io.apptolast.paparcar.presentation.map.MapScreen
import io.apptolast.paparcar.ui.theme.PaparcarTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

private object Routes {
    const val HOME = "home"
    const val MAP = "map"
    const val HISTORY = "history"
}

@Composable
fun App() {
    PaparcarTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val navController = rememberNavController()

            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onNavigateToMap = { navController.navigate(Routes.MAP) },
                        onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                    )
                }
                composable(Routes.MAP) {
                    MapScreen(
                        onNavigateBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.HISTORY) {
                    HistoryScreen(
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
