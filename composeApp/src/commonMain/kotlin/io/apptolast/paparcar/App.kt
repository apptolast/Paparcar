package io.apptolast.paparcar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.apptolast.customlogin.domain.model.AuthState
import com.apptolast.customlogin.presentation.navigation.AuthRoutesFlow
import com.apptolast.customlogin.presentation.navigation.LoginRoute
import com.apptolast.customlogin.presentation.navigation.NavTransitions
import com.apptolast.customlogin.presentation.navigation.authRoutesFlow
import io.apptolast.paparcar.presentation.app.AppIntent
import io.apptolast.paparcar.presentation.app.AppViewModel
import io.apptolast.paparcar.presentation.history.HistoryScreen
import io.apptolast.paparcar.presentation.home.HomeScreen
import io.apptolast.paparcar.presentation.map.MapScreen
import io.apptolast.paparcar.presentation.onboarding.OnboardingScreen
import io.apptolast.paparcar.presentation.permissions.PermissionsScreen
import io.apptolast.paparcar.presentation.settings.SettingsScreen
import io.apptolast.paparcar.ui.theme.PaparcarTheme
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

internal object Routes {
    const val HOME = "home"
    const val MAP = "map"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val ONBOARDING = "onboarding"
    const val PERMISSIONS = "permissions"
}

@Composable
fun App(
    splashViewModel: SplashViewModel = koinViewModel(),
    startRoute: String = Routes.HOME,
    onOpenMapsNavigation: (Double, Double) -> Unit = { _, _ -> },
) {
    val appViewModel = koinViewModel<AppViewModel>()
    val appState by appViewModel.state.collectAsStateWithLifecycle()
    val authState by splashViewModel.authState.collectAsStateWithLifecycle()

    PaparcarTheme {
        // Transparent so the map extends behind the status bar (edge-to-edge).
        // Each screen's Scaffold draws its own background.
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {

            // AnimatedContent provides smooth transitions when the authentication state changes.
            AnimatedContent(
                targetState = authState is AuthState.Authenticated,
                transitionSpec = {
                    // Use the shared slide transitions for a consistent feel.
                    NavTransitions.enter togetherWith NavTransitions.exit
                },
                label = "RootNavigationAnimation"
            ) { isAuthenticated ->
                if (isAuthenticated) {
                    MainAppNavigation(
                        startRoute,
                        appState.isFullyOperational,
                        { appViewModel.handleIntent(AppIntent.MarkOnboardingCompleted) },
                        onOpenMapsNavigation,
                    )
                } else {
                    // This will be shown for Loading, Unauthenticated, and Error states.
                    // The splash screen condition in MainActivity will handle hiding the app content
                    // while loading for the first time.
                    AuthNavigation()
                }
            }

        }
    }
}

@Composable
private fun AuthNavigation() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = AuthRoutesFlow,
        enterTransition = { NavTransitions.enter },
        exitTransition = { NavTransitions.exit },
        popEnterTransition = { NavTransitions.popEnter },
        popExitTransition = { NavTransitions.popExit },
    ) {
        authRoutesFlow(
            navController = navController,
            startDestination = LoginRoute,
            onNavigateToHome = { /* Handled by AuthState change */ },
        )
    }
}

@Composable
private fun MainAppNavigation(
    startRoute: String,
    isFullyOperational: Boolean,
    onHandleIntent: () -> Unit,
    onOpenMapsNavigation: (Double, Double) -> Unit
) {

    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    val gateScreens = setOf(Routes.PERMISSIONS, Routes.ONBOARDING)

    // When permissions or GPS are lost mid-session, navigate straight to PermissionsScreen.
    // Derived from state — never misses an update unlike a SharedFlow effect with no replay.
    // The condition guards against re-firing while already on a gate screen.
    LaunchedEffect(isFullyOperational, currentRoute) {
        if (!isFullyOperational
            && currentRoute != null
            && currentRoute !in gateScreens
        ) {
            navController.navigate(Routes.PERMISSIONS) {
                // Pop HOME so the back-stack is just PERMISSIONS.
                // Prevents the user from pressing back to a permission-less state.
                popUpTo(Routes.HOME) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startRoute,
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onComplete = {
                    onHandleIntent()
                    navController.navigate(Routes.PERMISSIONS) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.PERMISSIONS) {
            PermissionsScreen(
                onPermissionsGranted = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.PERMISSIONS) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToMap = { navController.navigate(Routes.MAP) },
                onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenMapsNavigation = onOpenMapsNavigation,
            )
        }
        composable(
            route = "${Routes.MAP}?lat={lat}&lon={lon}",
            arguments = listOf(
                navArgument("lat") { type = NavType.StringType; defaultValue = "" },
                navArgument("lon") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { backStack ->
            val lat = backStack.savedStateHandle.get<String>("lat")?.toDoubleOrNull()
            val lon = backStack.savedStateHandle.get<String>("lon")?.toDoubleOrNull()
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


@Preview
@Composable
fun AppPreview() {
    App()
}
