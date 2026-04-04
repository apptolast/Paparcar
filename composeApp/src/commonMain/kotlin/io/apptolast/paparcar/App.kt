package io.apptolast.paparcar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
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
import io.apptolast.paparcar.presentation.app.SplashViewModel
import io.apptolast.paparcar.presentation.history.HistoryScreen
import io.apptolast.paparcar.presentation.home.HomeScreen
import io.apptolast.paparcar.presentation.map.ParkingLocationScreen
import io.apptolast.paparcar.presentation.mycar.MyCarScreen
import io.apptolast.paparcar.presentation.onboarding.OnboardingScreen
import io.apptolast.paparcar.presentation.permissions.PermissionsScreen
import io.apptolast.paparcar.presentation.settings.SettingsScreen
import io.apptolast.paparcar.ui.theme.PaparcarTheme
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.nav_tab_history
import paparcar.composeapp.generated.resources.nav_tab_map
import paparcar.composeapp.generated.resources.nav_tab_my_car
import paparcar.composeapp.generated.resources.nav_tab_settings

internal object Routes {
    const val HOME = "home"
    const val PARKING_LOCATION = "map"
    const val HISTORY = "history"
    const val MY_CAR = "my_car"
    const val SETTINGS = "settings"
    const val ONBOARDING = "onboarding"
    const val PERMISSIONS = "permissions"
}

private val BOTTOM_NAV_ROUTES = setOf(
    Routes.HISTORY,
    Routes.MY_CAR,
    Routes.SETTINGS,
)

private val GATE_SCREENS = setOf(Routes.PERMISSIONS, Routes.ONBOARDING)

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
        // Each screen's Scaffold draws its own background.
        Surface(modifier = Modifier.fillMaxSize()) {

            // AnimatedContent switches between auth and app content.
            // When coming from Loading (initial auth check), no animation is played because
            // the splash screen covers the content — animating here would cause a flash of
            // the login screen on cold start for already-authenticated users.
            AnimatedContent(
                targetState = authState,
                transitionSpec = {
                    if (initialState is AuthState.Loading) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        NavTransitions.enter togetherWith NavTransitions.exit
                    }
                },
                label = "RootNavigationAnimation"
            ) { state ->
                when (state) {
                    is AuthState.Loading -> Box(Modifier.fillMaxSize()) // splash covers this
                    is AuthState.Authenticated -> MainAppNavigation(
                        startRoute,
                        appState.isFullyOperational,
                        { appViewModel.handleIntent(AppIntent.MarkOnboardingCompleted) },
                        onOpenMapsNavigation,
                    )
                    else -> AuthNavigation()
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
    onOpenMapsNavigation: (Double, Double) -> Unit,
) {
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    // When permissions or GPS are lost mid-session, navigate straight to PermissionsScreen.
    // Derived from state — never misses an update unlike a SharedFlow effect with no replay.
    // The condition guards against re-firing while already on a gate screen.
    LaunchedEffect(isFullyOperational, currentRoute) {
        if (!isFullyOperational
            && currentRoute != null
            && currentRoute !in GATE_SCREENS
        ) {
            navController.navigate(Routes.PERMISSIONS) {
                // Pop HOME so the back-stack is just PERMISSIONS.
                // Prevents the user from pressing back to a permission-less state.
                popUpTo(Routes.HOME) { inclusive = true }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            AnimatedVisibility(
                visible = currentRoute in BOTTOM_NAV_ROUTES,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                PaparcarBottomNav(
                    currentRoute = currentRoute,
                    onNavigate = { route -> navController.navigateToTab(route) },
                )
            }
        },
    ) { _ ->
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier.fillMaxSize(),
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
                    onNavigateToHistory = { navController.navigateToTab(Routes.HISTORY) },
                    onNavigateToMyCar = { navController.navigateToTab(Routes.MY_CAR) },
                    onNavigateToSettings = { navController.navigateToTab(Routes.SETTINGS) },
                    onOpenMapsNavigation = onOpenMapsNavigation,
                )
            }
            composable(
                route = "${Routes.PARKING_LOCATION}?lat={lat}&lon={lon}",
                arguments = listOf(
                    navArgument("lat") { type = NavType.StringType; defaultValue = "" },
                    navArgument("lon") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { backStack ->
                val lat = backStack.savedStateHandle.get<String>("lat")?.toDoubleOrNull()
                val lon = backStack.savedStateHandle.get<String>("lon")?.toDoubleOrNull()
                ParkingLocationScreen(
                    onNavigateBack = { navController.popBackStack() },
                    initialFocus = if (lat != null && lon != null) Pair(lat, lon) else null,
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToMap = { lat, lon ->
                        navController.navigate("${Routes.PARKING_LOCATION}?lat=$lat&lon=$lon")
                    },
                )
            }
            composable(Routes.MY_CAR) {
                MyCarScreen()
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom Navigation Bar
// ─────────────────────────────────────────────────────────────────────────────

private data class BottomNavItem(
    val route: String,
    val labelRes: @Composable () -> String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

@Composable
private fun PaparcarBottomNav(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    val items = listOf(
        BottomNavItem(
            route = Routes.HOME,
            labelRes = { stringResource(Res.string.nav_tab_map) },
            selectedIcon = Icons.Filled.Map,
            unselectedIcon = Icons.Outlined.Map,
        ),
        BottomNavItem(
            route = Routes.HISTORY,
            labelRes = { stringResource(Res.string.nav_tab_history) },
            selectedIcon = Icons.Filled.History,
            unselectedIcon = Icons.Outlined.History,
        ),
        BottomNavItem(
            route = Routes.MY_CAR,
            labelRes = { stringResource(Res.string.nav_tab_my_car) },
            selectedIcon = Icons.Filled.DirectionsCar,
            unselectedIcon = Icons.Outlined.DirectionsCar,
        ),
        BottomNavItem(
            route = Routes.SETTINGS,
            labelRes = { stringResource(Res.string.nav_tab_settings) },
            selectedIcon = Icons.Filled.Settings,
            unselectedIcon = Icons.Outlined.Settings,
        ),
    )

    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            val label = item.labelRes()
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.route) },
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = label,
                    )
                },
                label = { Text(label) },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun NavController.navigateToTab(route: String) {
    navigate(route) {
        // Pop back to start so the back stack doesn't grow unbounded when switching tabs.
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
