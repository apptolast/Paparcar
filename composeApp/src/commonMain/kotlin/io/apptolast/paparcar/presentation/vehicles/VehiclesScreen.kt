package io.apptolast.paparcar.presentation.vehicles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.ui.components.PapPrimaryButton
import io.apptolast.paparcar.ui.theme.PaparcarSpacing
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
    val state by viewModel.state.collectAsState()
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
    if (state.pendingDeleteVehicleId != null) {
        DeleteVehicleConfirmDialog(
            onConfirm = {
                onIntent(VehiclesIntent.ConfirmDeleteVehicle(state.pendingDeleteVehicleId!!))
            },
            onDismiss = { onIntent(VehiclesIntent.DismissDeleteConfirmation) },
        )
    }

    Scaffold(
        topBar = {
            // The "add vehicle" action lives in the TopAppBar (not as a FAB) because
            // the page below has its own pager + sticky vehicle header + history list;
            // a floating FAB would overlap the active vehicle's CTAs. The empty state
            // already has a prominent "Add vehicle" button — when there are no
            // vehicles yet, the TopAppBar action is hidden as redundant. [UI-002]
            TopAppBar(
                title = { Text(stringResource(Res.string.my_car_title)) },
                actions = {
                    if (!state.isLoading && state.vehicles.isNotEmpty()) {
                        IconButton(onClick = { onIntent(VehiclesIntent.AddVehicle) }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(Res.string.my_car_add_vehicle),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                state.vehicles.isEmpty() -> EmptyVehicleState(
                    modifier = Modifier.fillMaxSize(),
                    onAddVehicle = { onIntent(VehiclesIntent.AddVehicle) },
                    onShowExplainer = onShowExplainer,
                )
                state.vehicles.size == 1 -> VehiclePageContent(
                    vehicleWithStats = state.vehicles.first(),
                    onIntent = onIntent,
                    onConfigureBluetooth = onConfigureBluetooth,
                    onNavigateToMap = onNavigateToMap,
                    canDelete = false,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehiclesPager(
    state: VehiclesState,
    onIntent: (VehiclesIntent) -> Unit,
    onConfigureBluetooth: (vehicleId: String) -> Unit,
    onNavigateToMap: (lat: Double, lon: Double) -> Unit,
) {
    val vehicles = state.vehicles
    val pagerState = rememberPagerState(pageCount = { vehicles.size })
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(selectedTabIndex = pagerState.currentPage) {
            vehicles.forEachIndexed { index, vehicleWithStats ->
                val vehicle = vehicleWithStats.vehicle
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { VehicleTabLabel(vehicle = vehicle) },
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            VehiclePageContent(
                vehicleWithStats = vehicles[page],
                onIntent = onIntent,
                onConfigureBluetooth = onConfigureBluetooth,
                onNavigateToMap = onNavigateToMap,
            )
        }
    }
}

@Composable
private fun VehicleTabLabel(vehicle: Vehicle) {
    val tabName = listOfNotNull(vehicle.brand, vehicle.model)
        .joinToString(" ")
        .ifBlank { vehicle.id.take(TAB_LABEL_MAX_LENGTH) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = vehicleSizeEmoji(vehicle.sizeCategory), fontSize = TAB_EMOJI_SIZE)
        Text(text = tabName, maxLines = 1)
        if (vehicle.isDefault) {
            Box(
                modifier = Modifier
                    .size(ACTIVE_DOT_SIZE)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

// ─── Delete confirmation dialog ───────────────────────────────────────────────

@Composable
private fun DeleteVehicleConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.my_car_delete_confirm_title)) },
        text = { Text(stringResource(Res.string.my_car_delete_confirm_message)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(Res.string.my_car_delete_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.my_car_delete_cancel))
            }
        },
    )
}

// ─── Empty state ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyVehicleState(
    modifier: Modifier = Modifier,
    onAddVehicle: () -> Unit,
    onShowExplainer: () -> Unit,
) {
    Column(
        modifier = modifier.padding(horizontal = PaparcarSpacing.xxxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "🚗", fontSize = EMPTY_STATE_EMOJI_SIZE)
        Spacer(modifier = Modifier.height(PaparcarSpacing.lg))
        Text(
            text = stringResource(Res.string.my_car_no_vehicle),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(PaparcarSpacing.sm))
        Text(
            text = stringResource(Res.string.my_car_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(PaparcarSpacing.xxl))
        PapPrimaryButton(
            label = stringResource(Res.string.my_car_add_vehicle),
            onClick = onAddVehicle,
        )
        Spacer(modifier = Modifier.height(PaparcarSpacing.lg))
        TextButton(onClick = onShowExplainer) {
            Text(
                text = stringResource(Res.string.my_car_empty_why_link),
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = TextDecoration.Underline,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ─── Constants ────────────────────────────────────────────────────────────────

private val EMPTY_STATE_EMOJI_SIZE = 72.sp
private val TAB_EMOJI_SIZE = 16.sp
private val ACTIVE_DOT_SIZE = 6.dp
private const val TAB_LABEL_MAX_LENGTH = 8
