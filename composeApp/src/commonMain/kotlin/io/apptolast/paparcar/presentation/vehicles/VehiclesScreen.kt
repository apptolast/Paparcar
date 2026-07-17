package io.apptolast.paparcar.presentation.vehicles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import io.apptolast.paparcar.presentation.util.collectAsStateLifecycleAware
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.absoluteValue
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleMonitoringStatus
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.model.displayName
import io.apptolast.paparcar.domain.model.monitoringStatus
import io.apptolast.paparcar.ui.components.PapAlertDialog
import io.apptolast.paparcar.ui.components.chips.PaparcarAddChip
import io.apptolast.paparcar.ui.theme.PapBorders
import io.apptolast.paparcar.ui.theme.PaparcarType
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.error_unknown
import paparcar.composeapp.generated.resources.my_car_add_vehicle
import paparcar.composeapp.generated.resources.my_car_empty_subtitle
import paparcar.composeapp.generated.resources.my_car_empty_why_link
import paparcar.composeapp.generated.resources.my_car_no_vehicle
import paparcar.composeapp.generated.resources.my_car_title
import paparcar.composeapp.generated.resources.my_car_unnamed_vehicle
import paparcar.composeapp.generated.resources.vehicle_set_active_confirm_body
import paparcar.composeapp.generated.resources.vehicle_set_active_confirm_cancel
import paparcar.composeapp.generated.resources.vehicle_set_active_confirm_cta
import paparcar.composeapp.generated.resources.vehicle_set_active_confirm_title
import paparcar.composeapp.generated.resources.vehicle_status_active_cd

/**
 * VehiclesScreen (v1 redesign) — Vehicles + History fusionado.
 *
 *  - TopAppBar con tipografía appBarTitle (Outfit ExtraBold, headlineSmall + (-0.5)sp).
 *  - Tabs rediseñadas: pills custom con icono + nombre + dot si activo.
 *  - "+" trailing chip para añadir vehículo además del icon action en top bar.
 *  - Empty state con icono circular 120dp + display title + CTA grande.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehiclesScreen(
    onAddVehicle: () -> Unit = {},
    onEditVehicle: (vehicleId: String) -> Unit = {},
    onNavigateToMap: (lat: Double, lon: Double, sessionId: String) -> Unit = { _, _, _ -> },
    onShowExplainer: () -> Unit = {},
    viewModel: VehiclesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateLifecycleAware()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorFallback = stringResource(Res.string.error_unknown)

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is VehiclesEffect.NavigateToAddVehicle -> onAddVehicle()
                is VehiclesEffect.NavigateToEditVehicle -> onEditVehicle(effect.vehicleId)
                is VehiclesEffect.NavigateToMap -> onNavigateToMap(effect.lat, effect.lon, effect.sessionId)
                is VehiclesEffect.ShowError -> snackbarHostState.showSnackbar(errorFallback)
            }
        }
    }

    VehiclesContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onIntent = viewModel::handleIntent,
        onShowExplainer = onShowExplainer,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VehiclesContent(
    state: VehiclesState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onIntent: (VehiclesIntent) -> Unit = {},
    onShowExplainer: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.my_car_title),
                        style = PaparcarType.current.screenTitle,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
        // Match Home's bottom-sheet tone so the page doesn't feel near-black.
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }

                state.vehicles.isEmpty() -> EmptyVehicleState(
                    modifier = Modifier.fillMaxSize(),
                    onAddVehicle = { onIntent(VehiclesIntent.AddVehicle) },
                    onShowExplainer = onShowExplainer,
                )

                else -> VehiclesPager(
                    state = state,
                    onIntent = onIntent,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Multi-vehicle pager with custom pill tab row
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehiclesPager(
    state: VehiclesState,
    onIntent: (VehiclesIntent) -> Unit,
) {
    val vehicles = state.vehicles
    val pagerState = rememberPagerState(
        initialPage = state.selectedVehicleIndex,
        pageCount = { vehicles.size },
    )
    val scope = rememberCoroutineScope()
    // The vehicle whose set-active is awaiting confirmation. Non-null shows the consequence dialog;
    // making a car active is a declaration ("I drive this"), never a silent switch. [VEH-ACTIVE-FENCE-001]
    var pendingSetActive by remember { mutableStateOf<Vehicle?>(null) }

    // Scroll pager when ViewModel changes the selected index (e.g. restore on back-nav).
    // Skip when the pager is already scrolling — the in-progress animateScrollToPage
    // (started by a tab click) would be cancelled and replaced with an instant jump.
    LaunchedEffect(state.selectedVehicleIndex) {
        if (pagerState.settledPage != state.selectedVehicleIndex && !pagerState.isScrollInProgress) {
            pagerState.scrollToPage(state.selectedVehicleIndex)
        }
    }

    // Sync swipe gestures back to ViewModel so the selection survives navigation.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page -> onIntent(VehiclesIntent.SelectVehicle(page)) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        VehicleTabRow(
            vehicles = vehicles.map { it.vehicle },
            selectedIndex = pagerState.currentPage,
            onTabClick = { index ->
                onIntent(VehiclesIntent.SelectVehicle(index))
                scope.launch { pagerState.animateScrollToPage(index) }
            },
            onAddVehicle = { onIntent(VehiclesIntent.AddVehicle) },
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            key = { index -> vehicles.getOrElse(index) { vehicles.last() }.vehicle.id },
        ) { page ->
            // Defensive guard: during the recomposition where vehicles
            // shrinks and the clamp above hasn't run yet, `page` may
            // briefly point at an out-of-bounds index. Use the clamped
            // page to read the slot so we never crash with IOOBE.
            val safePage = page.coerceAtMost(vehicles.lastIndex)
            val vehicleWithStats = vehicles[safePage]
            // Each page reads its own vehicle's history from the cache directly.
            // Using the shared state.historyState (derived from selectedVehicleIndex)
            // would show the SELECTED vehicle's history on ALL visible pages during a
            // pager slide animation, making the incoming page appear to have the wrong content.
            val pageHistoryState = state.historyCache[vehicleWithStats.vehicle.id]
                ?: HistoryState(isLoading = false)
            // Carousel polish: the off-centre page eases out (alpha + scale) as you
            // swipe, so the incoming vehicle "settles" into focus instead of a flat slide.
            val pageOffset =
                ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                    .absoluteValue.coerceIn(0f, 1f)
            Box(
                modifier = Modifier.graphicsLayer {
                    alpha = PAGER_MIN_ALPHA + (1f - pageOffset) * (1f - PAGER_MIN_ALPHA)
                    val scale = PAGER_MIN_SCALE + (1f - pageOffset) * (1f - PAGER_MIN_SCALE)
                    scaleX = scale
                    scaleY = scale
                },
            ) {
                VehiclePageContent(
                    vehicleWithStats = vehicleWithStats,
                    historyState = pageHistoryState,
                    isSettingActive = state.settingActiveVehicleId == vehicleWithStats.vehicle.id,
                    onRequestSetActive = { pendingSetActive = vehicleWithStats.vehicle },
                    onIntent = onIntent,
                )
            }
        }
    }

    pendingSetActive?.let { veh ->
        SetActiveConfirmDialog(
            vehicleName = veh.displayName(fallback = stringResource(Res.string.my_car_unnamed_vehicle)),
            onConfirm = {
                onIntent(VehiclesIntent.SetActiveVehicle(veh.id))
                pendingSetActive = null
            },
            onDismiss = { pendingSetActive = null },
        )
    }
}

/**
 * Consequence confirmation before a vehicle becomes the active one: the active vehicle IS the
 * user's declaration of what they drive, so we spell out what activating means and never switch
 * silently. [VEH-ACTIVE-FENCE-001]
 */
@Composable
private fun SetActiveConfirmDialog(
    vehicleName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    PapAlertDialog(
        onDismiss = onDismiss,
        icon = Icons.Rounded.DirectionsCar,
        title = stringResource(Res.string.vehicle_set_active_confirm_title),
        body = stringResource(Res.string.vehicle_set_active_confirm_body, vehicleName),
        primaryLabel = stringResource(Res.string.vehicle_set_active_confirm_cta),
        primaryLeadingIcon = Icons.Rounded.DirectionsCar,
        onPrimary = onConfirm,
        cancelLabel = stringResource(Res.string.vehicle_set_active_confirm_cancel),
    )
}

@Composable
private fun VehicleTabRow(
    vehicles: List<Vehicle>,
    selectedIndex: Int,
    onTabClick: (Int) -> Unit,
    onAddVehicle: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        vehicles.forEachIndexed { index, vehicle ->
            VehicleTabPill(
                vehicle = vehicle,
                selected = index == selectedIndex,
                onClick = { onTabClick(index) },
            )
        }
        PaparcarAddChip(
            onClick = onAddVehicle,
            modifier = Modifier.height(TAB_HEIGHT_DP.dp),
            contentDescription = stringResource(Res.string.my_car_add_vehicle),
        )
    }
}


/**
 * Vehicle pager tab — aligned with [PaparcarFilterChip] visual contract:
 * neutral [PapBorders] outline (no neon-primary border on selected), primary-
 * tinted leading icon, and label colours that follow the selected state.
 * Adds an `isActive` dot suffix that the base chip doesn't need.
 */
@Composable
private fun VehicleTabPill(vehicle: Vehicle, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val tabName = vehicle.displayName(
        fallback = stringResource(Res.string.my_car_unnamed_vehicle),
    )
    val bg = if (selected) cs.primary.copy(alpha = SELECTED_FILL_ALPHA) else cs.surfaceContainerHigh
    val fg = if (selected) cs.primary else cs.onSurface
    // Status by a single coloured dot — green = actively detected, blue = detected via Bluetooth,
    // no dot = inactive. Colour is the whole message; no separate BT glyph, no method text.
    // [HOME-VEH-REFINE-001]
    val monitoring = vehicle.monitoringStatus()

    Surface(
        onClick = onClick,
        modifier = Modifier.height(TAB_HEIGHT_DP.dp),
        shape = RoundedCornerShape(PILL_RADIUS_DP.dp),
        color = bg,
        // Selected = tonal fill + MUTED primary border (the design's green-line), honouring the
        // "no neon-primary border on selected" contract this pill previously violated. [UI-REGRESSION]
        border = if (selected) BorderStroke(PapBorders.strong, cs.primary.copy(alpha = SELECTED_BORDER_ALPHA))
        else BorderStroke(PapBorders.thin, cs.outline.copy(alpha = PapBorders.DEFAULT_OUTLINE_ALPHA)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            io.apptolast.paparcar.ui.components.VehicleIcon(
                carbody = vehicle.carbodyType,
                size = vehicle.sizeCategory,
                tint = Color.Unspecified, // native multi-colour silhouette [BOLT-MARKERS-001]
                color = vehicle.color,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = tabName,
                // The vehicle NAME is identity → Outfit (rowTitle) everywhere, including this selector
                // pill, so the name reads in one family across card/header/selector. [CARD-ONE-BADGE-001]
                style = PaparcarType.current.rowTitle,
                fontWeight = FontWeight.SemiBold,
                color = fg,
                maxLines = 1,
            )
            VehicleStatusDot(status = monitoring)
        }
    }
}

/** Small status dot — green (active) / blue (Bluetooth) / absent (inactive). Mirrors the Home chip
 *  and map-marker status language so the selector reads the same everywhere. [HOME-VEH-REFINE-001] */
@Composable
private fun VehicleStatusDot(status: VehicleMonitoringStatus) {
    val cs = MaterialTheme.colorScheme
    val color = when (status) {
        is VehicleMonitoringStatus.Bluetooth -> cs.tertiary
        VehicleMonitoringStatus.Active       -> cs.primary
        VehicleMonitoringStatus.Inactive     -> return // no dot for inactive
    }
    val cd = stringResource(Res.string.vehicle_status_active_cd)
    Box(
        modifier = Modifier
            .size(ACTIVE_DOT_DP.dp)
            .clip(CircleShape)
            .background(color)
            .semantics { contentDescription = cd },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty state — big circular icon + display title + prominent CTA
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyVehicleState(
    modifier: Modifier = Modifier,
    onAddVehicle: () -> Unit,
    onShowExplainer: () -> Unit,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(EMPTY_ICON_CIRCLE_DP.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            io.apptolast.paparcar.ui.components.VehicleIcon(
                carbody = null,
                size = VehicleSize.MEDIUM_SUV,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(60.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(Res.string.my_car_no_vehicle),
            style = PaparcarType.current.heroTitle,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(Res.string.my_car_empty_subtitle),
            style = PaparcarType.current.body,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = EMPTY_BODY_ALPHA),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Surface(
            onClick = onAddVehicle,
            shape = RoundedCornerShape(EMPTY_CTA_CORNER_DP.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .height(EMPTY_CTA_HEIGHT_DP.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(Res.string.my_car_add_vehicle),
                    // Filled primary button → cta (Inter), the app's button convention. [CARD-ONE-BADGE-001]
                    style = PaparcarType.current.cta,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onShowExplainer) {
            Text(
                text = stringResource(Res.string.my_car_empty_why_link),
                style = PaparcarType.current.label,
                fontWeight = FontWeight.SemiBold,
                textDecoration = TextDecoration.Underline,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private const val PILL_RADIUS_DP = 999
private const val TAB_HEIGHT_DP = 36
// Carousel page transform: off-centre pages dim to this alpha and shrink to this scale.
private const val PAGER_MIN_ALPHA = 0.5f
private const val PAGER_MIN_SCALE = 0.92f
private const val ACTIVE_DOT_DP = 6
private const val SELECTED_FILL_ALPHA = 0.14f
private const val SELECTED_BORDER_ALPHA = 0.45f // muted "green-line", not the neon primary
private const val EMPTY_ICON_CIRCLE_DP = 120
private const val EMPTY_BODY_ALPHA = 0.65f
private const val EMPTY_CTA_HEIGHT_DP = 50
private const val EMPTY_CTA_CORNER_DP = 14
