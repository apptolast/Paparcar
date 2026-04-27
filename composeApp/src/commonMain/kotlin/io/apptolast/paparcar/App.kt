package io.apptolast.paparcar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.ui.components.AppBottomNavItem
import io.apptolast.paparcar.ui.components.AppBottomNavigation
import io.apptolast.paparcar.ui.components.ConnectivityOfflineBanner
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
import io.apptolast.paparcar.presentation.app.AppEffect
import io.apptolast.paparcar.presentation.app.AppIntent
import io.apptolast.paparcar.presentation.app.AppViewModel
import io.apptolast.paparcar.presentation.app.SplashViewModel
import io.apptolast.paparcar.presentation.history.HistoryScreen
import io.apptolast.paparcar.presentation.home.HomeScreen
import io.apptolast.paparcar.presentation.map.ParkingLocationScreen
import io.apptolast.paparcar.presentation.mycar.MyCarScreen
import io.apptolast.paparcar.presentation.onboarding.OnboardingScreen
import io.apptolast.paparcar.presentation.permissions.PermissionsScreen
import io.apptolast.paparcar.presentation.bluetooth.BluetoothConfigScreen
import io.apptolast.paparcar.presentation.permissions.PermissionsRationaleScreen
import io.apptolast.paparcar.presentation.settings.SettingsScreen
import io.apptolast.paparcar.presentation.util.DistanceUnit
import io.apptolast.paparcar.presentation.util.LocalDistanceUnit
import io.apptolast.paparcar.presentation.vehicle.VehicleRegistrationScreen
import io.apptolast.paparcar.ui.theme.PaparcarTheme
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.connectivity_restored_snackbar
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
    const val PERMISSIONS_RATIONALE = "permissions_rationale"
    const val VEHICLE_REGISTRATION = "vehicle_registration"
    const val BT_CONFIG = "bt_config"
}

private val BOTTOM_NAV_ROUTES = setOf(
    Routes.HOME,
    Routes.HISTORY,
    Routes.MY_CAR,
    Routes.SETTINGS,
)

private val GATE_SCREENS = setOf(
    Routes.PERMISSIONS,
    Routes.PERMISSIONS_RATIONALE,
    Routes.ONBOARDING,
    "${Routes.VEHICLE_REGISTRATION}?origin={origin}",
)

// Subset of gate screens that are skippable when permissions are already granted.
// ONBOARDING and VEHICLE_REGISTRATION are intentional flows unrelated to permission state.
private val PERMISSION_GATE_SCREENS = setOf(
    Routes.PERMISSIONS,
    Routes.PERMISSIONS_RATIONALE,
)

@Composable
fun App(
    splashViewModel: SplashViewModel = koinViewModel(),
    startRoute: String = Routes.HOME,
    onOpenMapsNavigation: (Double, Double) -> Unit = { _, _ -> },
) {
    val appViewModel = koinViewModel<AppViewModel>()
    val appState by appViewModel.state.collectAsStateWithLifecycle()
    val authState by splashViewModel.authState.collectAsStateWithLifecycle()

    PaparcarTheme(darkTheme = appState.darkTheme) {
        CompositionLocalProvider(
            LocalDistanceUnit provides if (appState.imperialUnits) DistanceUnit.IMPERIAL else DistanceUnit.METRIC,
        ) {
        // Each screen's Scaffold draws its own background.
        Surface(modifier = Modifier.fillMaxSize()) {
            val rootSnackbarHostState = remember { SnackbarHostState() }
            val connectionRestored = stringResource(Res.string.connectivity_restored_snackbar)
            LaunchedEffect(Unit) {
                appViewModel.effect.collect { effect ->
                    when (effect) {
                        AppEffect.ShowConnectionRestored ->
                            rootSnackbarHostState.showSnackbar(connectionRestored)
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
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
                            startRoute = startRoute,
                            isFullyOperational = appState.isFullyOperational,
                            darkTheme = appState.darkTheme,
                            onMarkOnboardingCompleted = { appViewModel.handleIntent(AppIntent.MarkOnboardingCompleted) },
                            onToggleDarkMode = { appViewModel.handleIntent(AppIntent.ToggleDarkMode(it)) },
                            imperialUnits = appState.imperialUnits,
                            onToggleImperialUnits = { appViewModel.handleIntent(AppIntent.SetDistanceUnit(it)) },
                            onOpenMapsNavigation = onOpenMapsNavigation,
                        )
                        else -> AuthNavigation()
                    }
                }

                // Persistent offline banner — anchored to the root scaffold so it
                // survives navigation between auth and app, between tabs, etc.
                ConnectivityOfflineBanner(
                    visible = appState.isOffline,
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                SnackbarHost(
                    hostState = rootSnackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 80.dp),
                )
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
    darkTheme: Boolean,
    onMarkOnboardingCompleted: () -> Unit,
    onToggleDarkMode: (Boolean) -> Unit,
    imperialUnits: Boolean,
    onToggleImperialUnits: (Boolean) -> Unit,
    onOpenMapsNavigation: (Double, Double) -> Unit,
) {
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    // Bidirectional permission gate guard — derived from state so it never misses an update.
    LaunchedEffect(isFullyOperational, currentRoute) {
        when {
            // Permissions lost mid-session → push to PERMISSIONS, clear HOME from back-stack.
            !isFullyOperational && currentRoute != null && currentRoute !in GATE_SCREENS -> {
                navController.navigate(Routes.PERMISSIONS) {
                    popUpTo(Routes.HOME) { inclusive = true }
                }
            }
            // Permissions already granted but stuck on a permission screen (e.g. deep-link or
            // back-navigation landed here) → skip straight to HOME.
            isFullyOperational && currentRoute in PERMISSION_GATE_SCREENS -> {
                navController.navigate(Routes.HOME) {
                    popUpTo(currentRoute!!) { inclusive = true }
                }
            }
        }
    }

    // Nav progress is a lifted state holder: HomeScreen writes the sheet-drag
    // progress into it each frame, and the bottom bar reads it inside a
    // graphicsLayer lambda so the visual update stays in the layer phase with
    // no cross-tree recomposition. Other screens leave it at 1f (fully shown).
    val navProgress = remember { mutableFloatStateOf(1f) }
    // Discrete visibility override driven by HomeScreen when an item is
    // selected — at that point the per-screen PaparcarBottomActionBar takes over the
    // bottom slot and the global bar should step aside.
    var showBottomNav by remember { mutableStateOf(true) }

    // Reset nav state when leaving HOME so other screens see a pristine bar.
    LaunchedEffect(currentRoute) {
        if (currentRoute != Routes.HOME) {
            navProgress.floatValue = 1f
            showBottomNav = true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            AnimatedVisibility(
                visible = currentRoute in BOTTOM_NAV_ROUTES && showBottomNav,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                AppBottomNavigation(
                    items = bottomNavItems,
                    currentRoute = currentRoute,
                    onNavigate = { route -> navController.navigateToTab(route) },
                    modifier = Modifier.graphicsLayer {
                        alpha = navProgress.floatValue
                        translationY = (1f - navProgress.floatValue) * size.height
                    },
                )
            }
        },
    ) { scaffoldPadding ->
        // NavHost intentionally fills the full screen — the Home route needs to
        // extend its bottom sheet under the AppBottomNavigation slot so the
        // sheet background fills the gap left when the nav fades away during
        // a drag. Each non-Home destination wraps its content in a Box that
        // applies scaffoldPadding so their content sits above the nav.
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { fadeIn(tween(NAV_ENTER_MS)) + slideInHorizontally { it / NAV_SLIDE_FRACTION } },
            exitTransition = { fadeOut(tween(NAV_EXIT_MS)) + slideOutHorizontally { -it / NAV_SLIDE_FRACTION } },
            popEnterTransition = { fadeIn(tween(NAV_ENTER_MS)) + slideInHorizontally { -it / NAV_SLIDE_FRACTION } },
            popExitTransition = { fadeOut(tween(NAV_EXIT_MS)) + slideOutHorizontally { it / NAV_SLIDE_FRACTION } },
        ) {
            composable(
                route = "${Routes.VEHICLE_REGISTRATION}?origin={origin}&vehicleId={vehicleId}",
                arguments = listOf(
                    navArgument("origin") { defaultValue = "onboarding" },
                    navArgument("vehicleId") { nullable = true; defaultValue = null },
                ),
            ) { backStack ->
                val origin = backStack.arguments?.getString("origin") ?: "onboarding"
                val vehicleId = backStack.arguments?.getString("vehicleId")
                VehicleRegistrationScreen(
                    vehicleId = vehicleId,
                    onRegistrationComplete = {
                        if (origin == "my_car") {
                            navController.popBackStack()
                        } else {
                            navController.navigate(Routes.ONBOARDING) {
                                popUpTo(Routes.VEHICLE_REGISTRATION) { inclusive = true }
                            }
                        }
                    },
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onComplete = {
                        onMarkOnboardingCompleted()
                        navController.navigate(Routes.PERMISSIONS_RATIONALE) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.PERMISSIONS_RATIONALE) {
                PermissionsRationaleScreen(
                    onAccept = {
                        navController.navigate(Routes.PERMISSIONS) {
                            popUpTo(Routes.PERMISSIONS_RATIONALE) { inclusive = true }
                        }
                    },
                    onSkip = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.PERMISSIONS_RATIONALE) { inclusive = true }
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
                    onOpenMapsNavigation = onOpenMapsNavigation,
                    navProgressState = navProgress,
                    onItemSelectedChange = { selected -> showBottomNav = !selected },
                    bottomPadding = scaffoldPadding.calculateBottomPadding(),
                )
            }
            composable(
                route = "${Routes.PARKING_LOCATION}?lat={lat}&lon={lon}",
                arguments = listOf(
                    navArgument("lat") { type = NavType.StringType; defaultValue = "" },
                    navArgument("lon") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { backStack ->
                val lat = backStack.arguments?.getString("lat")?.toDoubleOrNull()
                val lon = backStack.arguments?.getString("lon")?.toDoubleOrNull()
                ParkingLocationScreen(
                    onNavigateBack = { navController.popBackStack() },
                    initialFocus = if (lat != null && lon != null) Pair(lat, lon) else null,
                )
            }
            composable(Routes.HISTORY) {
                // consumeWindowInsets prevents the nested HistoryScreen Scaffold
                // from re-applying navigationBars padding on top of scaffoldPadding
                // (which already includes the nav inset via AppBottomNavigation),
                // which would otherwise leave a blank band above the bottom nav.
                Box(
                    modifier = Modifier
                        .padding(scaffoldPadding)
                        .consumeWindowInsets(scaffoldPadding),
                ) {
                    HistoryScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToMap = { lat, lon ->
                            navController.navigate("${Routes.PARKING_LOCATION}?lat=$lat&lon=$lon")
                        },
                    )
                }
            }
            composable(Routes.MY_CAR) {
                Box(
                    modifier = Modifier
                        .padding(scaffoldPadding)
                        .consumeWindowInsets(scaffoldPadding),
                ) {
                    MyCarScreen(
                        onAddVehicle = {
                            navController.navigate("${Routes.VEHICLE_REGISTRATION}?origin=my_car")
                        },
                        onEditVehicle = { vehicleId ->
                            navController.navigate("${Routes.VEHICLE_REGISTRATION}?origin=my_car&vehicleId=$vehicleId")
                        },
                        onConfigureBluetooth = { vehicleId ->
                            navController.navigate("${Routes.BT_CONFIG}/$vehicleId")
                        },
                    )
                }
            }
            composable(Routes.SETTINGS) {
                Box(
                    modifier = Modifier
                        .padding(scaffoldPadding)
                        .consumeWindowInsets(scaffoldPadding),
                ) {
                    SettingsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToMyCar = { navController.navigateToTab(Routes.MY_CAR) },
                        onNavigateToAuth = { /* AuthState change triggers auth nav automatically */ },
                        darkMode = darkTheme,
                        onToggleDarkMode = onToggleDarkMode,
                        imperialUnits = imperialUnits,
                        onToggleImperialUnits = onToggleImperialUnits,
                    )
                }
            }
            composable(
                route = "${Routes.BT_CONFIG}/{vehicleId}",
                arguments = listOf(navArgument("vehicleId") { type = NavType.StringType }),
            ) { backStack ->
                val vehicleId = backStack.arguments?.getString("vehicleId") ?: return@composable
                BluetoothConfigScreen(
                    vehicleId = vehicleId,
                    onNavigateBack = { navController.popBackStack() },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom Navigation Bar — single source of truth for the app's tab items
// ─────────────────────────────────────────────────────────────────────────────

private val bottomNavItems = listOf(
    AppBottomNavItem(
        route = Routes.HOME,
        label = { stringResource(Res.string.nav_tab_map) },
        iconFilled = Icons.Filled.Map,
        iconOutline = Icons.Outlined.Map,
    ),
    AppBottomNavItem(
        route = Routes.HISTORY,
        label = { stringResource(Res.string.nav_tab_history) },
        iconFilled = Icons.Filled.History,
        iconOutline = Icons.Outlined.History,
    ),
    AppBottomNavItem(
        route = Routes.MY_CAR,
        label = { stringResource(Res.string.nav_tab_my_car) },
        iconFilled = Icons.Filled.DirectionsCar,
        iconOutline = Icons.Outlined.DirectionsCar,
    ),
    AppBottomNavItem(
        route = Routes.SETTINGS,
        label = { stringResource(Res.string.nav_tab_settings) },
        iconFilled = Icons.Filled.Settings,
        iconOutline = Icons.Outlined.Settings,
    ),
)

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private const val NAV_ENTER_MS = 280
private const val NAV_EXIT_MS = 200
private const val NAV_SLIDE_FRACTION = 6

private fun NavController.navigateToTab(route: String) {
    navigate(route) {
        // Pop back to start so the back stack doesn't grow unbounded when switching tabs.
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
