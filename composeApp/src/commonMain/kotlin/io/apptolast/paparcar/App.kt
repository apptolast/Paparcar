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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import io.apptolast.paparcar.ui.components.AppBottomNavItem
import io.apptolast.paparcar.ui.components.AppBottomNavigation
import io.apptolast.paparcar.ui.components.ConnectivityBanner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import com.apptolast.customlogin.domain.model.AuthState
import com.apptolast.customlogin.presentation.navigation.AuthRoutesFlow
import com.apptolast.customlogin.presentation.navigation.LoginRoute
import com.apptolast.customlogin.presentation.navigation.NavTransitions
import com.apptolast.customlogin.presentation.navigation.authRoutesFlow
import io.apptolast.paparcar.domain.connectivity.ConnectivityBannerPhase
import io.apptolast.paparcar.domain.preferences.ThemeMode
import io.apptolast.paparcar.presentation.app.AppEffect
import io.apptolast.paparcar.presentation.app.AppIntent
import io.apptolast.paparcar.presentation.app.AppViewModel
import io.apptolast.paparcar.presentation.app.BootstrapFailure
import io.apptolast.paparcar.presentation.app.SplashEffect
import io.apptolast.paparcar.presentation.app.SplashViewModel
import io.apptolast.paparcar.presentation.home.HomeScreen
import io.apptolast.paparcar.presentation.map.HistoryParkingDetailScreen
import io.apptolast.paparcar.presentation.vehicles.VehiclesScreen
import io.apptolast.paparcar.presentation.onboarding.OnboardingScreen
import io.apptolast.paparcar.presentation.permissions.PermissionsScreen
import io.apptolast.paparcar.presentation.bluetooth.BluetoothConfigScreen
import io.apptolast.paparcar.presentation.settings.SettingsScreen
import io.apptolast.paparcar.presentation.util.DistanceUnit
import io.apptolast.paparcar.presentation.util.LocalDistanceUnit
import io.apptolast.paparcar.presentation.util.applyAppLocale
import io.apptolast.paparcar.presentation.vehicleregistration.VehicleRegistrationScreen
import io.apptolast.paparcar.presentation.vehicleregistration.VehicleSizeExplainerScreen
import io.apptolast.paparcar.ui.auth.paparcarAuthSlots
import io.apptolast.paparcar.ui.theme.PapMotion
import io.apptolast.paparcar.ui.theme.PaparcarTheme
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.error_bootstrap_fatal_body
import paparcar.composeapp.generated.resources.error_bootstrap_fatal_dismiss
import paparcar.composeapp.generated.resources.error_bootstrap_fatal_title
import paparcar.composeapp.generated.resources.error_bootstrap_offline_body
import paparcar.composeapp.generated.resources.error_bootstrap_offline_title
import paparcar.composeapp.generated.resources.error_bootstrap_retry
import paparcar.composeapp.generated.resources.gps_disclaimer_body
import paparcar.composeapp.generated.resources.gps_disclaimer_confirm
import paparcar.composeapp.generated.resources.gps_disclaimer_title
import paparcar.composeapp.generated.resources.nav_tab_home
import paparcar.composeapp.generated.resources.nav_tab_settings
import paparcar.composeapp.generated.resources.nav_tab_vehicles

object Routes {
    const val HOME = "home"
    const val PARKING_HISTORY_DETAIL = "parking_detail"
    const val VEHICLES = "vehicles"
    const val SETTINGS = "settings"
    const val ONBOARDING = "onboarding"
    const val PERMISSIONS = "permissions"
    const val VEHICLE_REGISTRATION = "vehicle_registration"
    /** First-run rationale shown before VEHICLE_REGISTRATION. Explains why size is required. */
    const val VEHICLE_SIZE_EXPLAINER = "vehicle_size_explainer"
    const val BT_CONFIG = "bt_config"
    const val GPS_DISCLAIMER = "gps_disclaimer"
}

private val BOTTOM_NAV_ROUTES = setOf(
    Routes.HOME,
    Routes.VEHICLES,
    Routes.SETTINGS,
)

// Ordered list used to determine slide direction when switching tabs.
private val BOTTOM_NAV_TAB_ORDER = listOf(Routes.HOME, Routes.VEHICLES, Routes.SETTINGS)

// Screens where the runtime-permission guard should NOT redirect: either they ARE the
// permission flow itself, or they are pre-permission setup screens (onboarding, vehicle
// registration in first-run). The guard only fires on top of normal app screens.
// Permissions route carries an optional `focus` tier arg so an in-Home CTA can open a focused view
// (only the relevant section) instead of the full first-run list. [DET-READY-001i]
private const val PERMISSIONS_ROUTE = "${Routes.PERMISSIONS}?focus={focus}"

private val GATE_SCREENS = setOf(
    PERMISSIONS_ROUTE,
    Routes.ONBOARDING,
    Routes.VEHICLE_SIZE_EXPLAINER,
    Routes.GPS_DISCLAIMER,
    "${Routes.VEHICLE_REGISTRATION}?origin={origin}&vehicleId={vehicleId}",
)

@Composable
fun App(
    splashViewModel: SplashViewModel = koinViewModel(),
) {
    val appViewModel = koinViewModel<AppViewModel>()
    val appState by appViewModel.state.collectAsStateWithLifecycle()
    val authState by splashViewModel.authState.collectAsStateWithLifecycle()
    val splashState by splashViewModel.state.collectAsStateWithLifecycle()

    val darkTheme = when (appState.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    PaparcarTheme(darkTheme = darkTheme) {
        CompositionLocalProvider(
            LocalDistanceUnit provides if (appState.imperialUnits) DistanceUnit.IMPERIAL else DistanceUnit.METRIC,
        ) {
        // Each screen's Scaffold draws its own background.
        Surface(modifier = Modifier.fillMaxSize()) {
            LaunchedEffect(Unit) {
                appViewModel.effect.collect { effect ->
                    when (effect) {
                        is AppEffect.ApplyLocale ->
                            applyAppLocale(effect.tag)
                    }
                }
            }
            // SplashEffect: offline errors surface a dialog with retry; fatal errors show a
            // one-shot dismissible dialog (sign-out is already done, auth state drives nav).
            var showBootstrapFatalDialog by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                splashViewModel.effect.collect { effect ->
                    when (effect) {
                        is SplashEffect.ShowOfflineError -> { /* handled by BootstrapOfflineDialog below */ }
                        is SplashEffect.ShowError -> showBootstrapFatalDialog = true
                    }
                }
            }

            // Root Column: the connectivity banner is the first child, so when it is visible it
            // takes real height and PUSHES the app content down (reflow) — it never overlays the
            // search bar or a screen header. When Hidden it collapses to zero height and the content
            // fills the screen edge-to-edge as before. [CONN-BANNER-001]
            val bannerVisible = appState.connectivityBanner != ConnectivityBannerPhase.Hidden
            Column(modifier = Modifier.fillMaxSize()) {
                ConnectivityBanner(phase = appState.connectivityBanner)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        // When the banner shows it already occupies (and colours) the status-bar strip,
                        // so consume that inset here — otherwise each screen's own statusBarsPadding()
                        // adds it a SECOND time, leaving a big gap under the banner (Home search bar,
                        // Vehicles/Settings headers…). When Hidden nothing is consumed and screens stay
                        // edge-to-edge exactly as before. [CONN-BANNER-001]
                        .then(if (bannerVisible) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier),
                ) {
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
                        is AuthState.Authenticated -> {
                            // Splash computed startRoute as soon as profile sync + vehicle sync + perms
                            // were resolved. Native splash stays up until non-null (see
                            // SplashViewModel.isReady → MainActivity.setKeepOnScreenCondition).
                            // This null guard is defensive — by the time we render here it should
                            // already be set.
                            val startRoute = splashState.startRoute
                            if (startRoute == null) {
                                Box(Modifier.fillMaxSize())
                            } else {
                                MainAppNavigation(
                                    startRoute = startRoute,
                                    isFullyOperational = appState.isFullyOperational,
                                    hasSeenGpsAccuracyDisclaimer = appState.hasSeenGpsAccuracyDisclaimer,
                                    postPermissionsRoute = splashState.postPermissionsRoute,
                                    onMarkOnboardingCompleted = { appViewModel.handleIntent(AppIntent.MarkOnboardingCompleted) },
                                    themeMode = appState.themeMode,
                                    onSetThemeMode = { appViewModel.handleIntent(AppIntent.SetThemeMode(it)) },
                                    imperialUnits = appState.imperialUnits,
                                    onToggleImperialUnits = { appViewModel.handleIntent(AppIntent.SetDistanceUnit(it)) },
                                    selectedLanguage = appState.selectedLanguage,
                                    onSetLanguage = { appViewModel.handleIntent(AppIntent.SetLanguage(it)) },
                                    onDismissGpsDisclaimer = { appViewModel.handleIntent(AppIntent.DismissGpsAccuracyDisclaimer) },
                                )
                            }
                        }
                        else -> AuthNavigation()
                    }
                }
                }
            }

            // Offline bootstrap dialog: shown when auth succeeded but Firestore was unreachable.
            // The user is still authenticated — they can retry without re-entering credentials.
            if (splashState.bootstrapFailure == BootstrapFailure.Offline) {
                BootstrapOfflineDialog(onRetry = { splashViewModel.retry() })
            }
            // Fatal bootstrap dialog: shown when profile or data sync failed unrecoverably.
            // The user was already signed out — dismissing navigates to login via auth state.
            if (showBootstrapFatalDialog) {
                BootstrapFatalDialog(onDismiss = { showBootstrapFatalDialog = false })
            }
        }
        }
    }
}

@Composable
private fun BootstrapOfflineDialog(onRetry: () -> Unit) {
    AlertDialog(
        onDismissRequest = { /* not dismissable — user must retry or kill the app */ },
        title = { Text(stringResource(Res.string.error_bootstrap_offline_title)) },
        text = { Text(stringResource(Res.string.error_bootstrap_offline_body)) },
        confirmButton = {
            TextButton(onClick = onRetry) {
                Text(stringResource(Res.string.error_bootstrap_retry))
            }
        },
    )
}

@Composable
private fun BootstrapFatalDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.error_bootstrap_fatal_title)) },
        text = { Text(stringResource(Res.string.error_bootstrap_fatal_body)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.error_bootstrap_fatal_dismiss))
            }
        },
    )
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
            slots = paparcarAuthSlots(),
            onNavigateToHome = { /* Handled by AuthState change */ },
        )
    }
}

@Composable
private fun MainAppNavigation(
    startRoute: String,
    isFullyOperational: Boolean,
    hasSeenGpsAccuracyDisclaimer: Boolean,
    postPermissionsRoute: String,
    onMarkOnboardingCompleted: () -> Unit,
    themeMode: ThemeMode,
    onSetThemeMode: (ThemeMode) -> Unit,
    imperialUnits: Boolean,
    onToggleImperialUnits: (Boolean) -> Unit,
    selectedLanguage: String,
    onSetLanguage: (String) -> Unit,
    onDismissGpsDisclaimer: () -> Unit,
) {
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    // Permission gate guard. [DET-READY-001i]
    // We deliberately do NOT force-navigate to PERMISSIONS when a permission or GPS is lost
    // mid-session: that ejected the user to a full-screen gate even for non-blocking losses
    // (e.g. notifications). Runtime loss now stays in Home, where the detection surfaces
    // (Blocked·CORE / Blocked·PRODUCER) report it and route to permissions on the user's action.
    // The cold-start gate (SplashViewModel) still requires CORE before the first entry.
    LaunchedEffect(isFullyOperational, hasSeenGpsAccuracyDisclaimer, currentRoute) {
        // Only safety net left: a fully-operational user sitting on a normal screen who has never
        // seen the mandatory GPS disclaimer (e.g. granted permissions outside the first-run chain).
        // The old "operational + on a gate screen → HOME" branch was removed: it duplicated Home
        // when the user voluntarily opened PERMISSIONS from an in-Home CTA while already operational.
        // Forward navigation is owned by each screen's own onComplete (context-aware, see below). [NAV-DEDUP-001]
        if (isFullyOperational && !hasSeenGpsAccuracyDisclaimer &&
            currentRoute != Routes.GPS_DISCLAIMER && currentRoute !in GATE_SCREENS
        ) {
            navController.navigate(Routes.GPS_DISCLAIMER)
        }
    }

    // Nav progress is a lifted state holder: HomeScreen writes the sheet-drag
    // progress into it each frame, and the bottom bar reads it inside a
    // graphicsLayer lambda so the visual update stays in the layer phase with
    // no cross-tree recomposition. Other screens leave it at 1f (fully shown).
    val navProgress = remember { mutableFloatStateOf(1f) }

    // Reset nav progress when leaving HOME so other screens see a pristine bar.
    LaunchedEffect(currentRoute) {
        if (currentRoute != Routes.HOME) {
            navProgress.floatValue = 1f
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            // Visibility is route-only — the Home sheet drag fades the bar via
            // navProgress's graphicsLayer below, but the bar stays mounted while
            // the sheet is collapsed (peek). The previous selection-discrete
            // hide overrode that and surprised users who expected tab navigation
            // to stay reachable whenever the modal was at peek.
            AnimatedVisibility(
                visible = (currentRoute ?: startRoute) in BOTTOM_NAV_ROUTES,
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
            enterTransition = {
                val fromIdx = BOTTOM_NAV_TAB_ORDER.indexOf(initialState.destination.route)
                val toIdx = BOTTOM_NAV_TAB_ORDER.indexOf(targetState.destination.route)
                if (fromIdx >= 0 && toIdx >= 0) {
                    val dir = if (toIdx > fromIdx) 1 else -1
                    fadeIn(navEnterSpec()) + slideInHorizontally(navEnterSpec()) { dir * it / NAV_SLIDE_FRACTION }
                } else {
                    fadeIn(navEnterSpec()) + slideInHorizontally(navEnterSpec()) { it / NAV_SLIDE_FRACTION }
                }
            },
            exitTransition = {
                val fromIdx = BOTTOM_NAV_TAB_ORDER.indexOf(initialState.destination.route)
                val toIdx = BOTTOM_NAV_TAB_ORDER.indexOf(targetState.destination.route)
                if (fromIdx >= 0 && toIdx >= 0) {
                    val dir = if (toIdx > fromIdx) 1 else -1
                    fadeOut(navExitSpec()) + slideOutHorizontally(navExitSpec()) { -dir * it / NAV_SLIDE_FRACTION }
                } else {
                    fadeOut(navExitSpec()) + slideOutHorizontally(navExitSpec()) { -it / NAV_SLIDE_FRACTION }
                }
            },
            popEnterTransition = { fadeIn(navEnterSpec()) + slideInHorizontally(navEnterSpec()) { -it / NAV_SLIDE_FRACTION } },
            popExitTransition = { fadeOut(navExitSpec()) + slideOutHorizontally(navExitSpec()) { it / NAV_SLIDE_FRACTION } },
        ) {
            composable(
                route = "${Routes.VEHICLE_REGISTRATION}?origin={origin}&vehicleId={vehicleId}",
                arguments = listOf(
                    navArgument("origin") { defaultValue = "onboarding" },
                    navArgument("vehicleId") { nullable = true; defaultValue = null },
                ),
            ) { backStack ->
                val origin = backStack.arguments?.read { getStringOrNull("origin") } ?: "onboarding"
                val vehicleId = backStack.arguments?.read { getStringOrNull("vehicleId") }
                VehicleRegistrationScreen(
                    vehicleId = vehicleId,
                    onRegistrationComplete = {
                        // origin=vehicles: came from the Vehicles tab → pop back to it.
                        // first-run: this is the last screen of the linear flow before Home.
                        //   Pop the VEHICLE_SIZE_EXPLAINER too (kept in the stack so that
                        //   back from the form returned to it) so that back from Home
                        //   doesn't traverse the onboarding flow again.
                        if (origin == "vehicles") {
                            navController.popBackStack()
                        } else {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.VEHICLE_SIZE_EXPLAINER) { inclusive = true }
                            }
                        }
                    },
                    onConfigureBluetooth = { newVehicleId ->
                        // Drop the registration form from the back stack so the user's
                        // "back" from BT_CONFIG returns to the prior screen, not the form
                        // they just submitted. For first-run we first navigate to HOME so
                        // back from BT_CONFIG lands on Home rather than exiting the app —
                        // BT_CONFIG sits on top of HOME in the stack. [VEH-BT-001]
                        if (origin == "vehicles") {
                            navController.navigate("${Routes.BT_CONFIG}/$newVehicleId") {
                                popUpTo(Routes.VEHICLES) { inclusive = false }
                            }
                        } else {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.VEHICLE_SIZE_EXPLAINER) { inclusive = true }
                            }
                            navController.navigate("${Routes.BT_CONFIG}/$newVehicleId")
                        }
                    },
                    onNavigateBack = { navController.popBackStack() },
                )
            }
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onComplete = {
                        onMarkOnboardingCompleted()
                        // The onboarding's last page narrates "set up permissions". The permissions
                        // screen is now the single explain-and-grant surface (no separate rationale).
                        navController.navigate(Routes.PERMISSIONS) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }
            composable(
                route = PERMISSIONS_ROUTE,
                arguments = listOf(
                    navArgument("focus") {
                        type = NavType.StringType
                        defaultValue = io.apptolast.paparcar.presentation.permissions.PermissionsFocus.All.name
                    },
                ),
            ) { backStackEntry ->
                val focus = io.apptolast.paparcar.presentation.permissions.PermissionsFocus
                    .fromArg(backStackEntry.arguments?.read { getStringOrNull("focus") })
                PermissionsScreen(
                    focus = focus,
                    onPermissionsGranted = {
                        // Voluntary visit (an in-Home "Activar" CTA — Home already in the back stack):
                        // pop straight back to the existing Home, never push a duplicate. Cold-start
                        // chain (no Home below): continue forward to the mandatory GPS disclaimer. [NAV-DEDUP-001]
                        if (!navController.popBackStack(Routes.HOME, inclusive = false)) {
                            navController.navigate(Routes.GPS_DISCLAIMER) {
                                popUpTo(PERMISSIONS_ROUTE) { inclusive = true }
                            }
                        }
                    },
                )
            }
            composable(Routes.GPS_DISCLAIMER) {
                io.apptolast.paparcar.presentation.permissions.GpsDisclaimerScreen(
                    onAccepted = {
                        onDismissGpsDisclaimer()
                        // Same dedup rule: return to an existing Home if present, else continue the
                        // cold-start chain forward to the resolved post-permissions route. [NAV-DEDUP-001]
                        if (!navController.popBackStack(Routes.HOME, inclusive = false)) {
                            navController.navigate(postPermissionsRoute) {
                                popUpTo(Routes.GPS_DISCLAIMER) { inclusive = true }
                            }
                        }
                    }
                )
            }
            composable(Routes.VEHICLE_SIZE_EXPLAINER) {
                VehicleSizeExplainerScreen(
                    onContinue = {
                        // Do NOT popUpTo(inclusive=true) here — keep the explainer in the
                        // back stack so the back button on the registration screen returns
                        // here, letting the user re-read the rationale if they want to.
                        navController.navigate("${Routes.VEHICLE_REGISTRATION}?origin=onboarding")
                    },
                )
            }
            composable(Routes.HOME) {
                HomeScreen(
                    navProgressState = navProgress,
                    bottomPadding = scaffoldPadding.calculateBottomPadding(),
                    // [DET-READY-001f] Banner "Turn on" → reuse the permission flow (disclosure +
                    // escalation already implemented there). Once PRODUCER perms land and the app is
                    // fully operational, the gate LaunchedEffect routes back to HOME automatically.
                    // focus = which tier triggered it (core/producer) → the permissions screen shows
                    // only that section instead of the full list. [DET-READY-001i]
                    onActivateDetection = { focus -> navController.navigate("${Routes.PERMISSIONS}?focus=$focus") },
                    // Banner "Add" → vehicle registration. origin=vehicles so completion pops back
                    // to HOME (the screen below), instead of replaying the onboarding pop chain.
                    onAddVehicle = { navController.navigate("${Routes.VEHICLE_REGISTRATION}?origin=vehicles") },
                )
            }
            composable(
                route = "${Routes.PARKING_HISTORY_DETAIL}?lat={lat}&lon={lon}&sessionId={sessionId}",
                arguments = listOf(
                    navArgument("lat") { type = NavType.StringType; defaultValue = "" },
                    navArgument("lon") { type = NavType.StringType; defaultValue = "" },
                    navArgument("sessionId") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { backStack ->
                val lat = backStack.arguments?.read { getStringOrNull("lat") }?.toDoubleOrNull()
                val lon = backStack.arguments?.read { getStringOrNull("lon") }?.toDoubleOrNull()
                val sessionId = backStack.arguments?.read { getStringOrNull("sessionId") } ?: ""
                HistoryParkingDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    initialFocus = if (lat != null && lon != null) Pair(lat, lon) else null,
                    sessionId = sessionId,
                )
            }
            composable(Routes.VEHICLES) {
                Box(
                    modifier = Modifier
                        .padding(scaffoldPadding)
                        .consumeWindowInsets(scaffoldPadding),
                ) {
                    VehiclesScreen(
                        onAddVehicle = {
                            navController.navigate("${Routes.VEHICLE_REGISTRATION}?origin=vehicles")
                        },
                        onEditVehicle = { vehicleId ->
                            navController.navigate("${Routes.VEHICLE_REGISTRATION}?origin=vehicles&vehicleId=$vehicleId")
                        },
                        onNavigateToMap = { lat, lon, sessionId ->
                            navController.navigate("${Routes.PARKING_HISTORY_DETAIL}?lat=$lat&lon=$lon&sessionId=$sessionId")
                        },
                        onShowExplainer = {
                            navController.navigate(Routes.VEHICLE_SIZE_EXPLAINER)
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
                        onNavigateToVehicles = { navController.navigateToTab(Routes.VEHICLES) },
                        onNavigateToAuth = { /* AuthState change triggers auth nav automatically */ },
                        themeMode = themeMode,
                        onSetThemeMode = onSetThemeMode,
                        imperialUnits = imperialUnits,
                        onToggleImperialUnits = onToggleImperialUnits,
                        selectedLanguage = selectedLanguage,
                        onSetLanguage = onSetLanguage,
                    )
                }
            }
            composable(
                route = "${Routes.BT_CONFIG}/{vehicleId}",
                arguments = listOf(navArgument("vehicleId") { type = NavType.StringType }),
            ) { backStack ->
                val vehicleId = backStack.arguments?.read { getStringOrNull("vehicleId") } ?: return@composable
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
        label = { stringResource(Res.string.nav_tab_home) },
        icon = Icons.Rounded.Home,
    ),
    AppBottomNavItem(
        route = Routes.VEHICLES,
        label = { stringResource(Res.string.nav_tab_vehicles) },
        icon = Icons.Rounded.DirectionsCar,
    ),
    AppBottomNavItem(
        route = Routes.SETTINGS,
        label = { stringResource(Res.string.nav_tab_settings) },
        icon = Icons.Rounded.Settings,
    ),
)

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private const val NAV_ENTER_MS = 280
private const val NAV_EXIT_MS = 200
private const val NAV_SLIDE_FRACTION = 6

// Shared specs so the fade and the horizontal slide of a tab transition run on the
// exact same curve/duration (default slide* uses a spring → it would desync the fade).
private fun <T> navEnterSpec() = tween<T>(NAV_ENTER_MS, easing = PapMotion.Standard)
private fun <T> navExitSpec() = tween<T>(NAV_EXIT_MS, easing = PapMotion.Standard)

private fun NavController.navigateToTab(route: String) {
    navigate(route) {
        // Pop back to HOME by explicit route, not graph.startDestinationId — the graph's
        // start destination may be an onboarding screen that has already been popped, in
        // which case popUpTo would find nothing and tabs would stack unboundedly.
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
