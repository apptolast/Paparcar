package io.apptolast.paparcar.presentation.vehicles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.ui.theme.PaparcarSpacing
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.ui.components.PapPrimaryButton
import io.apptolast.paparcar.ui.components.VehicleCard
import io.apptolast.paparcar.ui.components.VehicleCardData
import io.apptolast.paparcar.ui.components.VehicleDetectionStatus
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.error_unknown
import paparcar.composeapp.generated.resources.my_car_add_vehicle
import paparcar.composeapp.generated.resources.my_car_delete_cancel
import paparcar.composeapp.generated.resources.my_car_delete_confirm_action
import paparcar.composeapp.generated.resources.my_car_delete_confirm_message
import paparcar.composeapp.generated.resources.my_car_delete_confirm_title
import paparcar.composeapp.generated.resources.my_car_no_vehicle
import paparcar.composeapp.generated.resources.my_car_title
import paparcar.composeapp.generated.resources.vehicle_size_large
import paparcar.composeapp.generated.resources.vehicle_size_medium
import paparcar.composeapp.generated.resources.vehicle_size_moto
import paparcar.composeapp.generated.resources.vehicle_size_small
import paparcar.composeapp.generated.resources.vehicle_size_van

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehiclesScreen(
    onAddVehicle: () -> Unit = {},
    onEditVehicle: (vehicleId: String) -> Unit = {},
    onConfigureBluetooth: (vehicleId: String) -> Unit = {},
    onViewHistory: (vehicleId: String) -> Unit = {},
    viewModel: VehiclesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorFallback = stringResource(Res.string.error_unknown)

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is VehiclesEffect.NavigateToAddVehicle -> onAddVehicle()
                is VehiclesEffect.NavigateToEditVehicle -> onEditVehicle(effect.vehicleId)
                is VehiclesEffect.NavigateToHistory -> onViewHistory(effect.vehicleId)
                is VehiclesEffect.ShowError -> snackbarHostState.showSnackbar(errorFallback)
            }
        }
    }

    if (state.pendingDeleteVehicleId != null) {
        DeleteVehicleConfirmDialog(
            onConfirm = {
                viewModel.handleIntent(VehiclesIntent.ConfirmDeleteVehicle(state.pendingDeleteVehicleId!!))
            },
            onDismiss = { viewModel.handleIntent(VehiclesIntent.DismissDeleteConfirmation) },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(Res.string.my_car_title)) })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.handleIntent(VehiclesIntent.AddVehicle) },
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.my_car_add_vehicle))
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            state.vehicles.isEmpty() -> EmptyVehicleState(
                modifier = Modifier.fillMaxSize().padding(padding),
                onAddVehicle = { viewModel.handleIntent(VehiclesIntent.AddVehicle) },
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = PaparcarSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(PaparcarSpacing.md),
            ) {
                item { Spacer(Modifier.height(PaparcarSpacing.xs)) }
                items(items = state.vehicles, key = { it.id }) { vehicle ->
                    VehicleCard(
                        data = vehicle.toCardData(sizeLabel = vehicleSizeName(vehicle.sizeCategory)),
                        onSetActive = { viewModel.handleIntent(VehiclesIntent.SetActiveVehicle(vehicle.id)) },
                        onConfigureBluetooth = { onConfigureBluetooth(vehicle.id) },
                        onEdit = { viewModel.handleIntent(VehiclesIntent.EditVehicle(vehicle.id)) },
                        onDelete = { viewModel.handleIntent(VehiclesIntent.RequestDeleteVehicle(vehicle.id)) },
                        onViewHistory = { viewModel.handleIntent(VehiclesIntent.ViewHistory(vehicle.id)) },
                    )
                }
                item { Spacer(Modifier.height(FAB_CLEARANCE_HEIGHT)) }
            }
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
) {
    Column(
        modifier = modifier.padding(horizontal = PaparcarSpacing.xxxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.DirectionsCar,
            contentDescription = null,
            modifier = Modifier.size(EMPTY_STATE_ICON_SIZE),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = EMPTY_STATE_ICON_ALPHA),
        )
        Spacer(modifier = Modifier.height(PaparcarSpacing.lg))
        Text(
            text = stringResource(Res.string.my_car_no_vehicle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
        Spacer(modifier = Modifier.height(PaparcarSpacing.xxl))
        PapPrimaryButton(
            label = stringResource(Res.string.my_car_add_vehicle),
            onClick = onAddVehicle,
        )
    }
}

// ─── Mapper ───────────────────────────────────────────────────────────────────

private fun Vehicle.toCardData(sizeLabel: String): VehicleCardData {
    val displayName = listOfNotNull(brand, model).joinToString(" ").ifBlank { sizeLabel }
    val detectionStatus = when {
        bluetoothDeviceId != null -> VehicleDetectionStatus.Bluetooth(
            deviceLabel = bluetoothDeviceId.takeLast(BT_ADDRESS_LABEL_LENGTH),
        )
        else -> VehicleDetectionStatus.ActivityRecognition
    }
    return VehicleCardData(
        id = id,
        displayName = displayName,
        sizeLabel = sizeLabel,
        isActive = isDefault,
        detectionStatus = detectionStatus,
    )
}

// ─── Constants ───────────────────────────────────────────────────────────────

private val   FAB_CLEARANCE_HEIGHT    = 80.dp
private val   EMPTY_STATE_ICON_SIZE   = 72.dp
private const val EMPTY_STATE_ICON_ALPHA    = 0.4f
private const val BT_ADDRESS_LABEL_LENGTH   = 5

// ─── Helpers ─────────────────────────────────────────────────────────────────

@Composable
private fun vehicleSizeName(size: VehicleSize): String = when (size) {
    VehicleSize.MOTO -> stringResource(Res.string.vehicle_size_moto)
    VehicleSize.SMALL -> stringResource(Res.string.vehicle_size_small)
    VehicleSize.MEDIUM -> stringResource(Res.string.vehicle_size_medium)
    VehicleSize.LARGE -> stringResource(Res.string.vehicle_size_large)
    VehicleSize.VAN -> stringResource(Res.string.vehicle_size_van)
}
