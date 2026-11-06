package com.rndeveloper.paparcar

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
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.ui.components.AppBottomNavItem
import com.rndeveloper.paparcar.ui.components.AppBottomNavigation
import com.rndeveloper.paparcar.ui.components.ConnectivityBanner
import com.rndeveloper.paparcar.ui.components.PapSpotAlertBannerHost
import com.rndeveloper.paparcar.ui.components.PapAlertDialog
import com.rndeveloper.paparcar.ui.components.PapDialogAccent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import com.apptolast.baselogin.domain.model.AuthState
import com.apptolast.baselogin.presentation.navigation.AuthRoutesFlow
import com.apptolast.baselogin.presentation.navigation.LoginRoute
import com.apptolast.baselogin.presentation.navigation.NavTransitions
import com.apptolast.baselogin.presentation.navigation.authRoutesFlow
import com.rndeveloper.paparcar.domain.connectivity.ConnectivityBannerPhase
import com.rndeveloper.paparcar.domain.entitlement.PaywallReason
import com.rndeveloper.paparcar.domain.preferences.ThemeMode
import com.rndeveloper.paparcar.presentation.app.AppIntent
import com.rndeveloper.paparcar.presentation.app.SpotAlertCue
import com.rndeveloper.paparcar.presentation.app.SpotAlertOrigin
import com.rndeveloper.paparcar.presentation.app.AppViewModel
import com.rndeveloper.paparcar.presentation.app.BootstrapFailure
import com.rndeveloper.paparcar.presentation.app.SplashEffect
import com.rndeveloper.paparcar.presentation.app.SplashViewModel
import com.rndeveloper.paparcar.presentation.home.HomeScreen
import com.rndeveloper.paparcar.presentation.map.ParkingHistoryDetailScreen
import com.rndeveloper.paparcar.presentation.paywall.PaywallScreen
import com.rndeveloper.paparcar.presentation.stats.StatsScreen
import com.rndeveloper.paparcar.presentation.vehicles.VehiclesScreen
import com.rndeveloper.paparcar.presentation.onboarding.OnboardingScreen
import com.rndeveloper.paparcar.presentation.permissions.PermissionsScreen
import com.rndeveloper.paparcar.domain.onboarding.FirstStep
import com.rndeveloper.paparcar.presentation.bluetooth.BluetoothConfigScreen
import com.rndeveloper.paparcar.presentation.onboarding.FirstStepExplainerScreen
import com.rndeveloper.paparcar.presentation.licenses.LicenseDetailScreen
import com.rndeveloper.paparcar.presentation.licenses.LicensesScreen
import com.rndeveloper.paparcar.presentation.settings.SettingsScreen
import com.rndeveloper.paparcar.presentation.util.DistanceUnit
import com.rndeveloper.paparcar.presentation.util.LocalDistanceUnit
import com.rndeveloper.paparcar.presentation.util.LocalPumpGallon
import com.rndeveloper.paparcar.presentation.vehicleregistration.VehicleRegistrationScreen
import com.rndeveloper.paparcar.presentation.vehicleregistration.VehicleSizeExplainerScreen
import com.rndeveloper.paparcar.ui.auth.paparcarAuthSlots
import com.rndeveloper.paparcar.ui.theme.PapMotion
import com.rndeveloper.paparcar.ui.theme.PaparcarTheme
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.error_bootstrap_fatal_body
import paparcar.composeapp.generated.resources.spot_alert_nearby_in_app
import paparcar.composeapp.generated.resources.spot_alert_nearby_in_app_unconfirmed
import paparcar.composeapp.generated.resources.zone_alert_in_app_unconfirmed
import paparcar.composeapp.generated.resources.zone_alert_in_app
import paparcar.composeapp.generated.resources.error_bootstrap_fatal_dismiss
import paparcar.composeapp.generated.resources.error_bootstrap_fatal_title
import paparcar.composeapp.generated.resources.error_bootstrap_offline_body
import paparcar.composeapp.generated.resources.error_bootstrap_offline_title
import paparcar.composeapp.generated.resources.error_bootstrap_retry
import paparcar.composeapp.generated.resources.error_bootstrap_still_offline
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
    /** Atribución OSS: lista de librerías → texto de una licencia.
     *  [SET-LICENSES-ARE-SHOWN-IN-THE-APP-001] */
    const val LICENSES = "licenses"
    const val LICENSE_DETAIL = "license_detail"
    const val GPS_DISCLAIMER = "gps_disclaimer"
    /** A guided first step's explanation. Its own screen, not a sheet over Home — see
     *  `FirstStepExplainerScreen`. [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001] */
    const val FIRST_STEP_EXPLAINER = "first_step_explainer"
    /** Paparcar Plus paywall — every gate (history footer, zone alerts, Settings) lands here.
     *  [MONEY-001] */
    const val PAYWALL = "paywall"
    /** The full figures panel, opened from a vehicle's figures card. Carries which car was on
     *  screen so the panel opens on the same one. [STATS-PANEL-001] */
    const val STATS = "stats"
}
/**
 * La frase del aviso de plaza: cuatro, y las cuatro obligadas por el tipo.
 * [ALERT-A-PROVISIONAL-SPOT-IS-PUSHED-BUT-NOT-SHOWN-001]
 *
 * Dos preguntas independientes, y por eso cuatro frases y no un sufijo pegado:
 * - **De dónde sale** — la de zona nombra la zona, la de cerca de ti no tiene nombre que dar.
 *   [ZONE-AN-ALERT-NEAR-YOU-IS-NOT-AN-ALERT-IN-YOUR-ZONE-001]
 * - **Si la salida se midió o se dedujo** — una deducida dice *«puede haber quedado libre»*, porque es
 *   el único aviso que esa plaza va a tener y prometer certeza gastaría la confianza de la que vive
 *   todo el aviso.
 *
 * ⛔ Cuatro frases enteras y no una plantilla con un «posiblemente» delante: en ocho de los nueve
 * idiomas la duda cambia el verbo, no añade una palabra — *«acaba de quedar libre»* → *«puede haber
 * quedado libre»*, *«właśnie zwolniło się»* → *«mogło zwolnić się»*. Una plantilla obligaría a cada
 * traducción a caber en la gramática del inglés.
 */
@Composable
private fun spotAlertText(cue: SpotAlertCue): String = when (val origin = cue.origin) {
    is SpotAlertOrigin.InZone -> if (cue.unconfirmed) {
        stringResource(Res.string.zone_alert_in_app_unconfirmed, origin.zoneName)
    } else {
        stringResource(Res.string.zone_alert_in_app, origin.zoneName)
    }
    SpotAlertOrigin.NearYou -> if (cue.unconfirmed) {
        stringResource(Res.string.spot_alert_nearby_in_app_unconfirmed)
    } else {
        stringResource(Res.string.spot_alert_nearby_in_app)
    }
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
            // ⛔ El surtidor NO lo decide el interruptor de arriba: un español con la app en millas
            // sigue pagando por litro. Esta unidad sale del locale y por eso no lleva `appState`.
            // [I18N-A-PRICE-IS-QUOTED-IN-THE-UNIT-YOU-BUY-IT-001]
            LocalPumpGallon provides localePumpGallon(),
        ) {
        // Each screen's Scaffold draws its own background.
        Surface(modifier = Modifier.fillMaxSize()) {
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
                // ⛔ FLOTA sobre el contenido, no lo empuja: el de conectividad habla de un estado
                // que DURA y desplaza la app; éste dura cuatro segundos y habla de algo que está
                // justo debajo, así que empujar movería lo que el aviso quiere que mires.
                // [ZONE-AN-ALERT-IS-DELIVERED-WHERE-YOU-CAN-SEE-IT-001]
                PapSpotAlertBannerHost(
                    visible = appState.spotAlert != null,
                    text = appState.spotAlert?.let { spotAlertText(it) },
                    onDismiss = { appViewModel.handleIntent(AppIntent.DismissSpotAlert) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = SPOT_ALERT_BOTTOM_PADDING_DP.dp)
                        .zIndex(SPOT_ALERT_Z),
                )
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
                                    isDetectionReady = appState.isDetectionReady,
                                    hasSeenGpsAccuracyDisclaimer = appState.hasSeenGpsAccuracyDisclaimer,
                                    postSetupRoute = splashState.postSetupRoute,
                                    onMarkOnboardingCompleted = { appViewModel.handleIntent(AppIntent.MarkOnboardingCompleted) },
                                    themeMode = appState.themeMode,
                                    onSetThemeMode = { appViewModel.handleIntent(AppIntent.SetThemeMode(it)) },
                                    imperialUnits = appState.imperialUnits,
                                    onToggleImperialUnits = { appViewModel.handleIntent(AppIntent.SetDistanceUnit(it)) },
                                    onDismissGpsDisclaimer = { appViewModel.handleIntent(AppIntent.DismissGpsAccuracyDisclaimer) },
                                )
                            }
                        }
                        else -> AuthNavigation(
                            darkTheme = darkTheme,
                            onLegalConsentAccepted = { appViewModel.handleIntent(AppIntent.AcceptLegalConsent) },
                        )
                    }
                }
                }
            }

            // Offline bootstrap dialog: shown when auth succeeded but Firestore was unreachable.
            // The user is still authenticated — they can retry without re-entering credentials.
            if (splashState.bootstrapFailure == BootstrapFailure.Offline) {
                // Auto-proceed the moment connectivity returns — the user shouldn't have to time the
                // manual tap, and a retry while still offline just re-shows this dialog (which read as
                // "the button does nothing"). [RETRY-GATE-001]
                LaunchedEffect(appState.isOffline) {
                    if (!appState.isOffline) splashViewModel.retry()
                }
                BootstrapOfflineDialog(
                    isOffline = appState.isOffline,
                    onRetry = { splashViewModel.retry() },
                )
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
private fun BootstrapOfflineDialog(isOffline: Boolean, onRetry: () -> Unit) {
    // Tapping "Retry" while still offline re-shows this same dialog, so give immediate feedback that
    // the tap DID register but there is still no connection. Cleared automatically once connectivity
    // returns (the dialog also auto-retries then, via the caller). [RETRY-GATE-001]
    var stillOffline by remember { mutableStateOf(false) }
    LaunchedEffect(isOffline) { if (!isOffline) stillOffline = false }
    // The app's dialog mould — these two bootstrap dialogs were the last raw M3 AlertDialogs,
    // showing an off-system first impression on cold start. [SETTINGS-AUDIT-REMEDIATION-001]
    PapAlertDialog(
        onDismiss = { /* not dismissable — user must retry or kill the app */ },
        icon = Icons.Rounded.WifiOff,
        title = stringResource(Res.string.error_bootstrap_offline_title),
        body = stringResource(Res.string.error_bootstrap_offline_body),
        primaryLabel = stringResource(Res.string.error_bootstrap_retry),
        onPrimary = {
            if (isOffline) stillOffline = true
            onRetry()
        },
        content = {
            AnimatedVisibility(visible = stillOffline) {
                Text(
                    text = stringResource(Res.string.error_bootstrap_still_offline),
                    color = MaterialTheme.colorScheme.error,
                    style = PaparcarType.current.caption,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
    )
}

@Composable
private fun BootstrapFatalDialog(onDismiss: () -> Unit) {
    PapAlertDialog(
        onDismiss = onDismiss,
        icon = Icons.Rounded.Warning,
        title = stringResource(Res.string.error_bootstrap_fatal_title),
        body = stringResource(Res.string.error_bootstrap_fatal_body),
        primaryLabel = stringResource(Res.string.error_bootstrap_fatal_dismiss),
        onPrimary = onDismiss,
        accent = PapDialogAccent.Destructive,
    )
}

@Composable
private fun AuthNavigation(
    darkTheme: Boolean,
    onLegalConsentAccepted: () -> Unit,
) {
    val navController = rememberNavController()
    // Built here and not inside the NavHost builder: the builder lambda is not a composable
    // context, and the slots are since they remember the consent tick.
    val slots = paparcarAuthSlots(
        onLegalConsentAccepted = onLegalConsentAccepted,
    )
    // Stay in the same page palette, with a slightly deeper base on dark screens.
    val authBaseColor = if (darkTheme) {
        lerp(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.surfaceContainerLow, 0.5f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val authTopColor = MaterialTheme.colorScheme.primary.copy(alpha = if (darkTheme) 0.06f else 0.08f)
        .compositeOver(authBaseColor)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        authTopColor,
                        authBaseColor,
                        authBaseColor,
                    ),
                ),
            ),
    ) {
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
                slots = slots,
                onNavigateToHome = { /* Handled by AuthState change */ },
            )
        }
        // The library scrolls its status-bar padding with the form. Keep an opaque, fixed
        // protection layer so fields cannot become unreadable behind the system icons.
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(authTopColor),
        )
    }
}

@Composable
private fun MainAppNavigation(
    startRoute: String,
    isDetectionReady: Boolean,
    hasSeenGpsAccuracyDisclaimer: Boolean,
    postSetupRoute: String,
    onMarkOnboardingCompleted: () -> Unit,
    themeMode: ThemeMode,
    onSetThemeMode: (ThemeMode) -> Unit,
    imperialUnits: Boolean,
    onToggleImperialUnits: (Boolean) -> Unit,
    onDismissGpsDisclaimer: () -> Unit,
) {
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route
    // La IDENTIDAD de una pestana es su ruta BASE. Vehiculos acepta ahora un `?vehicleId=`
    // opcional (abrir la ficha de un coche concreto desde el modal de Home), y con el argumento
    // en la ruta registrada `destination.route` vale `vehicles?vehicleId={vehicleId}`: comparar
    // la cadena entera dejaba la bottom nav sin marcar y sin deslizamiento.
    // [UI-A-PARKED-CARD-OFFERS-THE-WAY-ACROSS-001]
    val currentTabRoute = currentRoute?.substringBefore('?')

    // We deliberately do NOT force-navigate to PERMISSIONS when a permission or GPS is lost
    // mid-session: that ejected the user to a full-screen gate even for non-blocking losses
    // (e.g. notifications). Runtime loss stays in Home, where the detection surfaces
    // (Blocked·CORE / Blocked·PRODUCER) report it and route to permissions on the user's action.
    // There is no cold-start gate either: Home leads, and asks on the map.
    // [ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001]
    LaunchedEffect(isDetectionReady, hasSeenGpsAccuracyDisclaimer, currentRoute) {
        // GPS-accuracy disclaimer — it talks about DETECTION, so it fires the first time detection
        // becomes fully armed (producer permissions granted), not the first time the map gets a
        // location fix. A user who never activates detection never sees it. Reactive rather than
        // chained so it fires no matter which surface completed the producer grant. [NAV-DEDUP-001]
        if (isDetectionReady && !hasSeenGpsAccuracyDisclaimer &&
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
                visible = (currentTabRoute ?: startRoute) in BOTTOM_NAV_ROUTES,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                AppBottomNavigation(
                    items = bottomNavItems,
                    currentRoute = currentTabRoute,
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
                val fromIdx = BOTTOM_NAV_TAB_ORDER.indexOf(initialState.destination.tabRoute())
                val toIdx = BOTTOM_NAV_TAB_ORDER.indexOf(targetState.destination.tabRoute())
                if (fromIdx >= 0 && toIdx >= 0) {
                    val dir = if (toIdx > fromIdx) 1 else -1
                    fadeIn(navEnterSpec()) + slideInHorizontally(navEnterSpec()) { dir * it / NAV_SLIDE_FRACTION }
                } else {
                    fadeIn(navEnterSpec()) + slideInHorizontally(navEnterSpec()) { it / NAV_SLIDE_FRACTION }
                }
            },
            exitTransition = {
                val fromIdx = BOTTOM_NAV_TAB_ORDER.indexOf(initialState.destination.tabRoute())
                val toIdx = BOTTOM_NAV_TAB_ORDER.indexOf(targetState.destination.tabRoute())
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
                        // Straight into the product: vehicle registration if needed, else Home —
                        // where the map asks for location itself. No permissions stop.
                        // [ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001]
                        navController.navigate(postSetupRoute) {
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
                        defaultValue = com.rndeveloper.paparcar.presentation.permissions.PermissionsFocus.Core.name
                    },
                ),
            ) { backStackEntry ->
                val focus = com.rndeveloper.paparcar.presentation.permissions.PermissionsFocus
                    .fromArg(backStackEntry.arguments?.read { getStringOrNull("focus") })
                PermissionsScreen(
                    focus = focus,
                    onPermissionsGranted = {
                        // Every visit is voluntary now (an in-Home/Settings CTA — Home already in
                        // the back stack): pop straight back, never push a duplicate. The GPS
                        // disclaimer is no longer chained here — it fires reactively when detection
                        // becomes ready (see MainAppNavigation's LaunchedEffect). The fallback
                        // covers a stray deep-link with no Home below. [NAV-DEDUP-001]
                        if (!navController.popBackStack(Routes.HOME, inclusive = false)) {
                            navController.navigate(postSetupRoute) {
                                popUpTo(PERMISSIONS_ROUTE) { inclusive = true }
                            }
                        }
                    },
                )
            }
            composable(Routes.GPS_DISCLAIMER) {
                com.rndeveloper.paparcar.presentation.permissions.GpsDisclaimerScreen(
                    onAccepted = {
                        onDismissGpsDisclaimer()
                        // Same dedup rule: return to an existing Home if present, else fall back to
                        // the resolved post-setup route. [NAV-DEDUP-001]
                        if (!navController.popBackStack(Routes.HOME, inclusive = false)) {
                            navController.navigate(postSetupRoute) {
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
            composable(Routes.HOME) { backStack ->
                // La ficha pidio ver AQUI un aparcamiento vivo. Viaja por el savedStateHandle de la
                // propia entrada de Home y no por un argumento de ruta, porque a Home no se NAVEGA
                // desde una pestana hermana: se vuelve a el (esta debajo en la pila), y una ruta con
                // argumento obligaria a recrearlo —mapa incluido— para entregar un id.
                // [UI-A-PARKED-CARD-OFFERS-THE-WAY-ACROSS-001]
                val focusParkingId by backStack.savedStateHandle
                    .getStateFlow<String?>(HOME_FOCUS_PARKING_KEY, null)
                    .collectAsState()
                HomeScreen(
                    focusParkingId = focusParkingId,
                    onFocusParkingConsumed = {
                        backStack.savedStateHandle[HOME_FOCUS_PARKING_KEY] = null
                    },
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
                    // [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001] The fortify step's
                    // Bluetooth face → the same car-Bluetooth screen Settings deep-links to, so
                    // there is one destination for "link this car", not two.
                    onLinkVehicleBluetooth = { vehicleId ->
                        navController.navigate("${Routes.BT_CONFIG}/$vehicleId")
                    },
                    // [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001] The explanation is a
                    // page, so it gets a page. Back returns to Home with the checklist as it was.
                    onOpenStepExplainer = { step ->
                        navController.navigate("${Routes.FIRST_STEP_EXPLAINER}/${step.name}")
                    },
                    onOpenPaywall = { reason -> navController.navigate("${Routes.PAYWALL}?reason=${reason.name}") },
                    // [UI-A-PARKED-CARD-OFFERS-THE-WAY-ACROSS-001] "Ver vehiculo" en el modal de
                    // aparcamiento: la pestana Vehiculos, abierta en ESE coche.
                    onOpenVehicle = { vehicleId -> navController.navigateToVehicle(vehicleId) },
                )
            }
            composable(
                route = "${Routes.FIRST_STEP_EXPLAINER}/{step}",
                arguments = listOf(navArgument("step") { type = NavType.StringType }),
            ) { backStack ->
                val name = backStack.arguments?.read { getStringOrNull("step") }
                // An unknown name means a build that renamed or retired a step: go back rather than
                // crash, the same way the persisted set drops names it does not know.
                val step = FirstStep.entries.firstOrNull { it.name == name }
                if (step == null) {
                    navController.popBackStack()
                } else {
                    FirstStepExplainerScreen(step = step, onClose = { navController.popBackStack() })
                }
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
                ParkingHistoryDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    initialFocus = if (lat != null && lon != null) Pair(lat, lon) else null,
                    sessionId = sessionId,
                )
            }
            composable(
                route = "${Routes.VEHICLES}?vehicleId={vehicleId}",
                arguments = listOf(
                    navArgument("vehicleId") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { backStack ->
                Box(
                    modifier = Modifier
                        .padding(scaffoldPadding)
                        .consumeWindowInsets(scaffoldPadding),
                ) {
                    VehiclesScreen(
                        // Con que coche abrir el pager. Nulo en el toque normal de la pestana, que
                        // conserva el que estuviera. [UI-A-PARKED-CARD-OFFERS-THE-WAY-ACROSS-001]
                        focusVehicleId = backStack.arguments?.read { getStringOrNull("vehicleId") },
                        onAddVehicle = {
                            navController.navigate("${Routes.VEHICLE_REGISTRATION}?origin=vehicles")
                        },
                        onEditVehicle = { vehicleId ->
                            navController.navigate("${Routes.VEHICLE_REGISTRATION}?origin=vehicles&vehicleId=$vehicleId")
                        },
                        onNavigateToMap = { lat, lon, sessionId ->
                            navController.navigate("${Routes.PARKING_HISTORY_DETAIL}?lat=$lat&lon=$lon&sessionId=$sessionId")
                        },
                        // Un aparcamiento VIVO se ve en Home, no en el detalle de una sesion cerrada:
                        // es el AHORA, y es donde se puede uno ir, navegar o corregir el pin. Se deja
                        // el id en la entrada de Home y se VUELVE a el —Home esta debajo en la pila—,
                        // en vez de apilar una pantalla mas sobre la pestana.
                        // [UI-A-PARKED-CARD-OFFERS-THE-WAY-ACROSS-001]
                        onNavigateToLiveParking = { sessionId ->
                            navController.getBackStackEntry(Routes.HOME)
                                .savedStateHandle[HOME_FOCUS_PARKING_KEY] = sessionId
                            navController.popBackStack(Routes.HOME, inclusive = false)
                        },
                        onShowExplainer = {
                            navController.navigate(Routes.VEHICLE_SIZE_EXPLAINER)
                        },
                        onOpenPaywall = { reason -> navController.navigate("${Routes.PAYWALL}?reason=${reason.name}") },
                        onOpenStats = { vehicleId ->
                            navController.navigate("${Routes.STATS}?vehicleId=$vehicleId")
                        },
                        // El mismo flujo enfocado que abren el banner de Home y el interruptor de
                        // Ajustes: activar la deteccion es UNA accion. [DET-TOGGLE-001]
                        onNavigateToPermissions = { focus ->
                            navController.navigate("${Routes.PERMISSIONS}?focus=$focus")
                        },
                    )
                }
            }
            composable(
                route = "${Routes.STATS}?vehicleId={vehicleId}",
                arguments = listOf(
                    navArgument("vehicleId") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { backStack ->
                StatsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onOpenPaywall = { reason -> navController.navigate("${Routes.PAYWALL}?reason=${reason.name}") },
                    onNavigateToPermissions = { focus ->
                        navController.navigate("${Routes.PERMISSIONS}?focus=$focus")
                    },
                    // `read { getStringOrNull(...) }` and not Android's Bundle.getString: this
                    // graph is commonMain and compiles for iOS too.
                    vehicleId = backStack.arguments?.read { getStringOrNull("vehicleId") },
                )
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
                        // Reuse the permission flow focused on the failing tier (as Home's banner does).
                        onNavigateToPermissions = { focus -> navController.navigate("${Routes.PERMISSIONS}?focus=$focus") },
                        // Deep-link into car-Bluetooth config for the active vehicle.
                        onNavigateToBluetoothConfig = { vehicleId -> navController.navigate("${Routes.BT_CONFIG}/$vehicleId") },
                        onNavigateToLicenses = { navController.navigate(Routes.LICENSES) },
                        onOpenPaywall = { reason -> navController.navigate("${Routes.PAYWALL}?reason=${reason.name}") },
                        themeMode = themeMode,
                        onSetThemeMode = onSetThemeMode,
                        imperialUnits = imperialUnits,
                        onToggleImperialUnits = onToggleImperialUnits,
                    )
                }
            }
            composable(Routes.LICENSES) {
                LicensesScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToLicense = { licenseId ->
                        navController.navigate("${Routes.LICENSE_DETAIL}/$licenseId")
                    },
                )
            }
            // El id de licencia es SPDX (`Apache-2.0`, `MIT`) o el hash del generador: seguro en una
            // ruta, sin dos puntos ni barras que haya que escapar — por eso el detalle se abre por
            // licencia y no por coordenadas Maven de la librería.
            composable(
                route = "${Routes.LICENSE_DETAIL}/{licenseId}",
                arguments = listOf(navArgument("licenseId") { type = NavType.StringType }),
            ) { backStack ->
                val licenseId = backStack.arguments?.read { getStringOrNull("licenseId") }
                    ?: return@composable
                LicenseDetailScreen(
                    licenseId = licenseId,
                    onNavigateBack = { navController.popBackStack() },
                )
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
            composable(
                route = "${Routes.PAYWALL}?reason={reason}",
                arguments = listOf(navArgument("reason") { type = NavType.StringType; defaultValue = "" }),
            ) { backStack ->
                // [FREE-LIMITS-001] WHICH limit asked — feeds paywall_shown and the storeless
                // motive line. Unknown/absent parses to GENERAL, never a crash.
                val reason = PaywallReason.ofName(backStack.arguments?.read { getStringOrNull("reason") })
                PaywallScreen(
                    reason = reason,
                    onClose = { navController.popBackStack() },
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

/**
 * Abrir la pestana Vehiculos EN un coche. [UI-A-PARKED-CARD-OFFERS-THE-WAY-ACROSS-001]
 *
 * ⛔ Sin `restoreState`, a diferencia de [navigateToTab]. Navigation Compose, cuando hay estado
 * guardado para el destino, RESTAURA la entrada vieja con sus argumentos viejos en lugar de crear
 * una nueva con el `vehicleId` que acabamos de pasar: el salto habria aterrizado en el coche
 * anterior justo cuando el sentido del boton es aterrizar en este. Una entrada nueva cuesta el
 * scroll guardado de la pestana, que es exactamente lo que este gesto quiere reemplazar.
 */
/** Donde la ficha deja "abre este aparcamiento" para Home. [UI-A-PARKED-CARD-OFFERS-THE-WAY-ACROSS-001] */
private const val HOME_FOCUS_PARKING_KEY = "focusParkingId"

private fun NavController.navigateToVehicle(vehicleId: String) {
    navigate("${Routes.VEHICLES}?vehicleId=$vehicleId") {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
    }
}

/** La ruta de pestana a la que pertenece un destino: su ruta base, sin argumentos. */
private fun NavDestination.tabRoute(): String? = route?.substringBefore('?')

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

/** El aviso in-app se apoya por encima de la barra de navegación, no sobre ella. */
private const val SPOT_ALERT_BOTTOM_PADDING_DP = 96

/** Por encima del contenido y por debajo de cualquier modal: es un aviso, no una interrupción. */
private const val SPOT_ALERT_Z = 1f
