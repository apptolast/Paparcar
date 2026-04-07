package io.apptolast.paparcar.presentation.mycar

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import paparcar.composeapp.generated.resources.my_car_no_vehicle
import paparcar.composeapp.generated.resources.my_car_title
import paparcar.composeapp.generated.resources.vehicle_size_large
import paparcar.composeapp.generated.resources.vehicle_size_medium
import paparcar.composeapp.generated.resources.vehicle_size_moto
import paparcar.composeapp.generated.resources.vehicle_size_small
import paparcar.composeapp.generated.resources.vehicle_size_van

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyCarScreen(
    onAddVehicle: () -> Unit = {},
    onConfigureBluetooth: (vehicleId: String) -> Unit = {},
    viewModel: MyCarViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorFallback = stringResource(Res.string.error_unknown)

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is MyCarEffect.NavigateToAddVehicle -> onAddVehicle()
                is MyCarEffect.ShowError -> snackbarHostState.showSnackbar(errorFallback)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(Res.string.my_car_title)) })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.handleIntent(MyCarIntent.AddVehicle) },
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
                onAddVehicle = { viewModel.handleIntent(MyCarIntent.AddVehicle) },
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Spacer(Modifier.height(4.dp)) }
                items(items = state.vehicles, key = { it.id }) { vehicle ->
                    VehicleCard(
                        data = vehicle.toCardData(sizeLabel = vehicleSizeName(vehicle.sizeCategory)),
                        onSetActive = { viewModel.handleIntent(MyCarIntent.SetActiveVehicle(vehicle.id)) },
                        onConfigureBluetooth = { onConfigureBluetooth(vehicle.id) },
                    )
                }
                item { Spacer(Modifier.height(80.dp)) } // FAB clearance
            }
        }
    }
}

// ─── Empty state ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyVehicleState(
    modifier: Modifier = Modifier,
    onAddVehicle: () -> Unit,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.DirectionsCar,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.my_car_no_vehicle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
        Spacer(modifier = Modifier.height(24.dp))
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
            deviceLabel = bluetoothDeviceId.takeLast(5),
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

// ─── Helpers ─────────────────────────────────────────────────────────────────

@Composable
private fun vehicleSizeName(size: VehicleSize): String = when (size) {
    VehicleSize.MOTO -> stringResource(Res.string.vehicle_size_moto)
    VehicleSize.SMALL -> stringResource(Res.string.vehicle_size_small)
    VehicleSize.MEDIUM -> stringResource(Res.string.vehicle_size_medium)
    VehicleSize.LARGE -> stringResource(Res.string.vehicle_size_large)
    VehicleSize.VAN -> stringResource(Res.string.vehicle_size_van)
}