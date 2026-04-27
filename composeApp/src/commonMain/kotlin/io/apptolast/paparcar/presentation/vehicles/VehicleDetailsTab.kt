package io.apptolast.paparcar.presentation.vehicles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.VehicleWithStats
import io.apptolast.paparcar.ui.components.VehicleCard
import io.apptolast.paparcar.ui.components.VehicleCardData
import io.apptolast.paparcar.ui.components.VehicleDetectionStatus
import io.apptolast.paparcar.ui.theme.PaparcarSpacing

@Composable
internal fun VehicleDetailsTab(
    vehicleId: String,
    vehicleWithStats: VehicleWithStats?,
    onEditVehicle: (vehicleId: String) -> Unit,
    onConfigureBluetooth: (vehicleId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = PaparcarSpacing.lg, vertical = PaparcarSpacing.md),
        contentAlignment = Alignment.TopCenter,
    ) {
        when {
            vehicleWithStats == null -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
            )
            else -> Column(
                verticalArrangement = Arrangement.spacedBy(PaparcarSpacing.md),
            ) {
                Spacer(Modifier.height(PaparcarSpacing.xs))
                VehicleCard(
                    data = vehicleWithStats.toCardData(),
                    onSetActive = {},
                    onConfigureBluetooth = { onConfigureBluetooth(vehicleId) },
                    onEdit = { onEditVehicle(vehicleId) },
                )
            }
        }
    }
}

private fun VehicleWithStats.toCardData(): VehicleCardData {
    val displayName = listOfNotNull(vehicle.brand, vehicle.model).joinToString(" ")
        .ifBlank { vehicle.sizeCategory.name.lowercase().replaceFirstChar { it.uppercase() } }
    val detectionStatus = when {
        vehicle.bluetoothDeviceId != null -> VehicleDetectionStatus.Bluetooth(
            deviceLabel = vehicle.bluetoothDeviceId.takeLast(BT_LABEL_LENGTH),
        )
        else -> VehicleDetectionStatus.ActivityRecognition
    }
    return VehicleCardData(
        id = vehicle.id,
        displayName = displayName,
        sizeLabel = vehicle.sizeCategory.name.lowercase(),
        isActive = vehicle.isDefault,
        detectionStatus = detectionStatus,
    )
}

private const val BT_LABEL_LENGTH = 5
