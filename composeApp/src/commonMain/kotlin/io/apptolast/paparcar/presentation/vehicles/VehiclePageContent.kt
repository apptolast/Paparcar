package io.apptolast.paparcar.presentation.vehicles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.model.VehicleWithStats
import io.apptolast.paparcar.presentation.history.HistoryContent
import io.apptolast.paparcar.presentation.history.HistoryEffect
import io.apptolast.paparcar.presentation.history.HistoryIntent
import io.apptolast.paparcar.presentation.history.HistoryViewModel
import io.apptolast.paparcar.ui.components.PapSecondaryButton
import io.apptolast.paparcar.ui.components.PapStatusBadge
import io.apptolast.paparcar.ui.components.VehicleDetectionStatus
import io.apptolast.paparcar.ui.theme.PapBlue
import io.apptolast.paparcar.ui.theme.PapBlueMuted
import io.apptolast.paparcar.ui.theme.PapGreen
import io.apptolast.paparcar.ui.theme.PapGreenMuted
import io.apptolast.paparcar.ui.theme.PaparcarSpacing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.my_car_active_vehicle
import paparcar.composeapp.generated.resources.my_car_bt_configure
import paparcar.composeapp.generated.resources.my_car_delete_vehicle
import paparcar.composeapp.generated.resources.my_car_edit_vehicle
import paparcar.composeapp.generated.resources.my_car_set_active
import paparcar.composeapp.generated.resources.vehicle_card_detection_ar
import paparcar.composeapp.generated.resources.vehicle_card_detection_bt
import paparcar.composeapp.generated.resources.vehicle_size_large
import paparcar.composeapp.generated.resources.vehicle_size_medium
import paparcar.composeapp.generated.resources.vehicle_size_moto
import paparcar.composeapp.generated.resources.vehicle_size_small
import paparcar.composeapp.generated.resources.vehicle_size_van
import androidx.compose.foundation.layout.PaddingValues

/**
 * A single page inside the vehicles HorizontalPager.
 *
 * Contains:
 * 1. A flat vehicle details header (minimal elevation, M3 tonal surface).
 * 2. The parking history for this vehicle, filling the remaining space.
 *
 * The [HistoryViewModel] is scoped to this composable via a unique key so each
 * vehicle tab has its own independent observer. [HIST-001]
 */
@Composable
internal fun VehiclePageContent(
    vehicleWithStats: VehicleWithStats,
    onIntent: (VehiclesIntent) -> Unit,
    onConfigureBluetooth: (vehicleId: String) -> Unit,
    onNavigateToMap: (lat: Double, lon: Double) -> Unit,
    canDelete: Boolean = true,
) {
    val vehicleId = vehicleWithStats.vehicle.id

    val historyVm: HistoryViewModel = koinViewModel(
        key = "history_$vehicleId",
        parameters = { parametersOf(vehicleId) },
    )
    val historyState by historyVm.state.collectAsState()

    LaunchedEffect(vehicleId) {
        historyVm.effect.collect { effect ->
            when (effect) {
                is HistoryEffect.NavigateToMap -> onNavigateToMap(effect.lat, effect.lon)
                is HistoryEffect.ShowError -> { /* shown inline via state */ }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        VehicleDetailsHeader(
            vehicleWithStats = vehicleWithStats,
            onSetActive = { onIntent(VehiclesIntent.SetActiveVehicle(vehicleId)) },
            onEdit = { onIntent(VehiclesIntent.EditVehicle(vehicleId)) },
            onDelete = { onIntent(VehiclesIntent.RequestDeleteVehicle(vehicleId)) },
            onConfigureBluetooth = { onConfigureBluetooth(vehicleId) },
            canDelete = canDelete,
        )
        HorizontalDivider()
        HistoryContent(
            state = historyState,
            contentPadding = PaddingValues(0.dp),
            onViewOnMap = { lat, lon ->
                historyVm.handleIntent(HistoryIntent.ViewOnMap(lat, lon))
            },
            onFilterSelected = { filter ->
                historyVm.handleIntent(HistoryIntent.SetFilter(filter))
            },
            modifier = Modifier.weight(1f),
        )
    }
}

// ─── Vehicle details header ────────────────────────────────────────────────────

@Composable
private fun VehicleDetailsHeader(
    vehicleWithStats: VehicleWithStats,
    onSetActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onConfigureBluetooth: () -> Unit,
    canDelete: Boolean,
) {
    val vehicle = vehicleWithStats.vehicle
    val displayName = listOfNotNull(vehicle.brand, vehicle.model)
        .joinToString(" ")
        .ifBlank { vehicleSizeName(vehicle.sizeCategory) }

    val detectionStatus = if (vehicle.bluetoothDeviceId != null)
        VehicleDetectionStatus.Bluetooth(vehicle.bluetoothDeviceId.takeLast(BT_LABEL_LENGTH))
    else
        VehicleDetectionStatus.ActivityRecognition

    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = HEADER_TONAL_ELEVATION,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = PaparcarSpacing.lg, vertical = PaparcarSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PaparcarSpacing.xs),
        ) {
            // Name + active badge + action icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.DirectionsCar,
                    contentDescription = null,
                    modifier = Modifier.size(ICON_SIZE),
                    tint = if (vehicle.isDefault) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(PaparcarSpacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = vehicleSizeName(vehicle.sizeCategory),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (vehicle.isDefault) {
                    PapStatusBadge(label = stringResource(Res.string.my_car_active_vehicle))
                    Spacer(Modifier.width(PaparcarSpacing.xs))
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = stringResource(Res.string.my_car_edit_vehicle),
                        modifier = Modifier.size(ICON_ACTION_SIZE),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Button stays clickable even when canDelete=false so the VM can surface
                // the "must keep at least one vehicle" snackbar — translucent alpha hints
                // at the disabled state.
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(Res.string.my_car_delete_vehicle),
                        modifier = Modifier.size(ICON_ACTION_SIZE),
                        tint = MaterialTheme.colorScheme.error.copy(
                            alpha = if (canDelete) DELETE_ICON_ALPHA else DELETE_ICON_DISABLED_ALPHA,
                        ),
                    )
                }
            }

            // Detection badge
            DetectionChip(status = detectionStatus)

            // Action chips: set active + BT config
            Row(horizontalArrangement = Arrangement.spacedBy(PaparcarSpacing.sm)) {
                if (!vehicle.isDefault) {
                    PapSecondaryButton(
                        label = stringResource(Res.string.my_car_set_active),
                        onClick = onSetActive,
                        modifier = Modifier.weight(1f),
                    )
                }
                PapSecondaryButton(
                    label = stringResource(Res.string.my_car_bt_configure),
                    onClick = onConfigureBluetooth,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(PaparcarSpacing.xs))
        }
    }
}

@Composable
private fun DetectionChip(status: VehicleDetectionStatus) {
    val (label, bg, fg, icon) = when (status) {
        is VehicleDetectionStatus.Bluetooth -> DetectionChipData(
            label = "${stringResource(Res.string.vehicle_card_detection_bt)} · ${status.deviceLabel}",
            bg = PapBlueMuted,
            fg = PapBlue,
            icon = Icons.Outlined.Bluetooth,
        )
        VehicleDetectionStatus.ActivityRecognition -> DetectionChipData(
            label = stringResource(Res.string.vehicle_card_detection_ar),
            bg = PapGreenMuted,
            fg = PapGreen,
            icon = Icons.Outlined.DirectionsCar,
        )
        VehicleDetectionStatus.Disabled -> DetectionChipData(
            label = "Off",
            bg = MaterialTheme.colorScheme.surfaceVariant,
            fg = MaterialTheme.colorScheme.onSurface,
            icon = null,
        )
    }
    Surface(
        shape = RoundedCornerShape(CHIP_CORNER_RADIUS),
        color = bg,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PaparcarSpacing.sm, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            icon?.let {
                Icon(it, contentDescription = null, modifier = Modifier.size(12.dp), tint = fg)
            }
            Text(label, style = MaterialTheme.typography.labelSmall, color = fg)
        }
    }
}

private data class DetectionChipData(
    val label: String,
    val bg: androidx.compose.ui.graphics.Color,
    val fg: androidx.compose.ui.graphics.Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector?,
)

@Composable
private fun vehicleSizeName(size: VehicleSize): String = when (size) {
    VehicleSize.MOTO   -> stringResource(Res.string.vehicle_size_moto)
    VehicleSize.SMALL  -> stringResource(Res.string.vehicle_size_small)
    VehicleSize.MEDIUM -> stringResource(Res.string.vehicle_size_medium)
    VehicleSize.LARGE  -> stringResource(Res.string.vehicle_size_large)
    VehicleSize.VAN    -> stringResource(Res.string.vehicle_size_van)
}

private val ICON_SIZE = 22.dp
private val ICON_ACTION_SIZE = 20.dp
private val HEADER_TONAL_ELEVATION = 2.dp
private val CHIP_CORNER_RADIUS = 8.dp
private const val BT_LABEL_LENGTH = 5
private const val DELETE_ICON_ALPHA = 0.75f
private const val DELETE_ICON_DISABLED_ALPHA = 0.30f
