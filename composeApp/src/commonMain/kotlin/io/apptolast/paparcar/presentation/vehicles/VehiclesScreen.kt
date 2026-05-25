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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.displayName
import io.apptolast.paparcar.ui.components.chips.PaparcarAddChip
import io.apptolast.paparcar.ui.components.PapAlertDialog
import io.apptolast.paparcar.ui.components.PapDialogAccent
import io.apptolast.paparcar.ui.icons.PaparcarIcons
import io.apptolast.paparcar.ui.theme.PapBorders
import io.apptolast.paparcar.ui.theme.appBarTitle
import io.apptolast.paparcar.ui.icons.icon
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.error_unknown
import paparcar.composeapp.generated.resources.my_car_add_vehicle
import paparcar.composeapp.generated.resources.my_car_cannot_delete_last_vehicle
import paparcar.composeapp.generated.resources.my_car_delete_cancel
import paparcar.composeapp.generated.resources.my_car_delete_confirm_action
import paparcar.composeapp.generated.resources.my_car_delete_confirm_message
import paparcar.composeapp.generated.resources.my_car_delete_confirm_title
import paparcar.composeapp.generated.resources.my_car_empty_subtitle
import paparcar.composeapp.generated.resources.my_car_empty_why_link
import paparcar.composeapp.generated.resources.my_car_no_vehicle
import paparcar.composeapp.generated.resources.my_car_title
import paparcar.composeapp.generated.resources.my_car_unnamed_vehicle

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
    onConfigureBluetooth: (vehicleId: String) -> Unit = {},
    onNavigateToMap: (lat: Double, lon: Double) -> Unit = { _, _ -> },
    onShowExplainer: () -> Unit = {},
    viewModel: VehiclesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateLifecycleAware()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorFallback = stringResource(Res.string.error_unknown)
    val cannotDeleteLastMessage = stringResource(Res.string.my_car_cannot_delete_last_vehicle)

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is VehiclesEffect.NavigateToAddVehicle -> onAddVehicle()
                is VehiclesEffect.NavigateToEditVehicle -> onEditVehicle(effect.vehicleId)
                is VehiclesEffect.ShowError -> snackbarHostState.showSnackbar(errorFallback)
                is VehiclesEffect.ShowCannotDeleteLastVehicle ->
                    snackbarHostState.showSnackbar(cannotDeleteLastMessage)
            }
        }
    }

    VehiclesContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onIntent = viewModel::handleIntent,
        onConfigureBluetooth = onConfigureBluetooth,
        onNavigateToMap = onNavigateToMap,
        onShowExplainer = onShowExplainer,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VehiclesContent(
    state: VehiclesState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onIntent: (VehiclesIntent) -> Unit = {},
    onConfigureBluetooth: (vehicleId: String) -> Unit = {},
    onNavigateToMap: (lat: Double, lon: Double) -> Unit = { _, _ -> },
    onShowExplainer: () -> Unit = {},
) {
    state.pendingDeleteVehicleId?.let { pendingId ->
        DeleteVehicleConfirmDialog(
            onConfirm = { onIntent(VehiclesIntent.ConfirmDeleteVehicle(pendingId)) },
            onDismiss = { onIntent(VehiclesIntent.DismissDeleteConfirmation) },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.my_car_title),
                        style = MaterialTheme.typography.appBarTitle,
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
                    onConfigureBluetooth = onConfigureBluetooth,
                    onNavigateToMap = onNavigateToMap,
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
    onConfigureBluetooth: (vehicleId: String) -> Unit,
    onNavigateToMap: (lat: Double, lon: Double) -> Unit,
) {
    val vehicles = state.vehicles
    val pagerState = rememberPagerState(
        initialPage = state.selectedVehicleIndex,
        pageCount = { vehicles.size },
    )
    val scope = rememberCoroutineScope()

    // Scroll pager when ViewModel changes the selected index (e.g. restore on back-nav).
    LaunchedEffect(state.selectedVehicleIndex) {
        if (pagerState.currentPage != state.selectedVehicleIndex) {
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
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            // Defensive guard: during the recomposition where vehicles
            // shrinks and the clamp above hasn't run yet, `page` may
            // briefly point at an out-of-bounds index. Use the clamped
            // page to read the slot so we never crash with IOOBE.
            val safePage = page.coerceAtMost(vehicles.lastIndex)
            VehiclePageContent(
                vehicleWithStats = vehicles[safePage],
                onIntent = onIntent,
                onConfigureBluetooth = onConfigureBluetooth,
                onNavigateToMap = onNavigateToMap,
                canDelete = vehicles.size > 1,
            )
        }
    }
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
        // 32dp diameter: iconSize 16 + contentPad 8 per side = 32dp total to
        // align vertically with adjacent VehicleTabPills.
        PaparcarAddChip(
            onClick = onAddVehicle,
            iconSize = 16.dp,
            contentPad = 8.dp,
            contentDescription = stringResource(Res.string.my_car_add_vehicle),
        )
    }
}


/**
 * Vehicle pager tab — aligned with [PaparcarFilterChip] visual contract:
 * neutral [PapBorders] outline (no neon-primary border on selected), primary-
 * tinted leading icon, and label colours that follow the selected state.
 * Adds an `isDefault` dot suffix that the base chip doesn't need.
 */
@Composable
private fun VehicleTabPill(vehicle: Vehicle, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val tabName = vehicle.displayName(
        fallback = stringResource(Res.string.my_car_unnamed_vehicle),
    )
    val bg = if (selected) cs.primaryContainer else cs.surfaceContainerHigh
    val borderColor = if (selected) {
        cs.outline.copy(alpha = PapBorders.DEFAULT_OUTLINE_ALPHA + 0.2f)
    } else {
        cs.outline.copy(alpha = PapBorders.DEFAULT_OUTLINE_ALPHA)
    }
    val fg = if (selected) cs.onPrimaryContainer else cs.onSurface

    Surface(
        onClick = onClick,
        modifier = Modifier.height(TAB_HEIGHT_DP.dp),
        shape = RoundedCornerShape(PILL_RADIUS_DP.dp),
        color = bg,
        border = BorderStroke(PapBorders.thin, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = vehicle.sizeCategory.icon,
                contentDescription = null,
                tint = cs.primary,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = tabName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = fg,
                maxLines = 1,
            )
            if (vehicle.isDefault) {
                Box(
                    modifier = Modifier
                        .size(ACTIVE_DOT_DP.dp)
                        .clip(CircleShape)
                        .background(cs.primary),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Delete confirmation dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DeleteVehicleConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    PapAlertDialog(
        onDismiss = onDismiss,
        icon = Icons.Outlined.Delete,
        title = stringResource(Res.string.my_car_delete_confirm_title),
        body = stringResource(Res.string.my_car_delete_confirm_message),
        primaryLabel = stringResource(Res.string.my_car_delete_confirm_action),
        primaryLeadingIcon = Icons.Outlined.Delete,
        onPrimary = onConfirm,
        cancelLabel = stringResource(Res.string.my_car_delete_cancel),
        accent = PapDialogAccent.Destructive,
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
            Icon(
                imageVector = PaparcarIcons.VehicleMedium,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(60.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(Res.string.my_car_no_vehicle),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(Res.string.my_car_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
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
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(Res.string.my_car_add_vehicle),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onShowExplainer) {
            Text(
                text = stringResource(Res.string.my_car_empty_why_link),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                textDecoration = TextDecoration.Underline,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private const val TITLE_LETTER_SPACING_SP = -0.5
private const val PILL_RADIUS_DP = 999
private const val TAB_HEIGHT_DP = 32
private const val ACTIVE_DOT_DP = 6
private const val EMPTY_ICON_CIRCLE_DP = 120
private const val EMPTY_BODY_ALPHA = 0.65f
private const val EMPTY_CTA_HEIGHT_DP = 50
private const val EMPTY_CTA_CORNER_DP = 14
