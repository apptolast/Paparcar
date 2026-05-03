package io.apptolast.paparcar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothConnected
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.ui.theme.PapBlue
import io.apptolast.paparcar.ui.theme.PapBlueMuted
import io.apptolast.paparcar.ui.theme.PapGreen
import io.apptolast.paparcar.ui.theme.PapGreenMuted
import io.apptolast.paparcar.ui.theme.PaparcarSpacing
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.my_car_active_vehicle
import paparcar.composeapp.generated.resources.my_car_bt_configure
import paparcar.composeapp.generated.resources.my_car_delete_vehicle
import paparcar.composeapp.generated.resources.my_car_edit_vehicle
import paparcar.composeapp.generated.resources.my_car_set_active
import paparcar.composeapp.generated.resources.vehicle_card_detection_ar
import paparcar.composeapp.generated.resources.vehicle_card_detection_bt
import paparcar.composeapp.generated.resources.vehicle_card_detection_off
import paparcar.composeapp.generated.resources.vehicles_view_history

// ─────────────────────────────────────────────────────────────────────────────
// Detection status model
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Detection strategy currently associated with a vehicle.
 *
 *  - [Bluetooth]: paired BT device is set; [deviceLabel] = last-5 of MAC or device name.
 *  - [ActivityRecognition]: no BT configured; using the AR coordinator strategy.
 *  - [Disabled]: auto-detection is turned off in Settings.
 */
sealed class VehicleDetectionStatus {
    data class Bluetooth(val deviceLabel: String) : VehicleDetectionStatus()
    data object ActivityRecognition : VehicleDetectionStatus()
    data object Disabled : VehicleDetectionStatus()
}

// ─────────────────────────────────────────────────────────────────────────────
// Presentation model
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Presentation model for [VehicleCard].
 *
 * @param id              Vehicle ID (used as LazyColumn key).
 * @param displayName     Brand + model, or size label as fallback.
 * @param sizeLabel       Human-readable vehicle size (Moto / Small / Medium …).
 * @param isActive        Whether this is the default / currently active vehicle.
 * @param detectionStatus Active detection strategy for this vehicle.
 */
data class VehicleCardData(
    val id: String,
    val displayName: String,
    val sizeLabel: String,
    val isActive: Boolean,
    val detectionStatus: VehicleDetectionStatus,
)

// ─────────────────────────────────────────────────────────────────────────────
// VehicleCard
// ─────────────────────────────────────────────────────────────────────────────

private const val INACTIVE_ICON_ALPHA         = 0.5f
private const val DISABLED_DETECTION_ALPHA    = 0.45f

/**
 * Vehicle card with detection status indicator.
 *
 * Shows:
 *  - Car icon + name + size label + "Active" badge when [VehicleCardData.isActive]
 *  - Detection status badge (BT / AR / Off)
 *  - "Set as active" button when not active
 *  - "Configure Bluetooth" button always visible
 */
@Composable
fun VehicleCard(
    data: VehicleCardData,
    onSetActive: () -> Unit,
    onConfigureBluetooth: () -> Unit,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onViewHistory: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isRedundant = data.displayName.trim().equals(data.sizeLabel.trim(), ignoreCase = true)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = if (data.isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium
            )
            .padding(PaparcarSpacing.md)
    ) {
        Column {
            // ── Header: icon + name + Edit/Delete ───────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(PaparcarSpacing.md),
            ) {
                Icon(
                    imageVector = Icons.Outlined.DirectionsCar,
                    contentDescription = null,
                    tint = if (data.isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = INACTIVE_ICON_ALPHA),
                    modifier = Modifier.size(24.dp).padding(top = 2.dp),
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = data.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (!isRedundant) {
                        Text(
                            text = data.sizeLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(PaparcarSpacing.xs))
                    VehicleDetectionBadge(status = data.detectionStatus)
                }

                // Botones de acción rápida (Edit/Delete)
                Row {
                    IconButton(
                        onClick = onEdit,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(Res.string.my_car_edit_vehicle), modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onDelete,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)),
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(Res.string.my_car_delete_vehicle), modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (data.isActive) {
                Spacer(Modifier.height(PaparcarSpacing.sm))
                PapStatusBadge(
                    label = stringResource(Res.string.my_car_active_vehicle),
                )
            }

            Spacer(Modifier.height(PaparcarSpacing.md))
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )
            Spacer(Modifier.height(PaparcarSpacing.md))

            // ── Bottom Action Row ──────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!data.isActive) {
                    PapSecondaryButton(
                        label = stringResource(Res.string.my_car_set_active),
                        onClick = onSetActive,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(PaparcarSpacing.sm))
                }

                PapSecondaryButton(
                    label = stringResource(Res.string.my_car_bt_configure),
                    onClick = onConfigureBluetooth,
                    modifier = if (data.isActive) Modifier.fillMaxWidth() else Modifier.weight(1f),
                )
            }

            if (onViewHistory != null) {
                Spacer(Modifier.height(PaparcarSpacing.sm))
                PapSecondaryButton(
                    label = stringResource(Res.string.vehicles_view_history),
                    onClick = onViewHistory,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Detection badge
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VehicleDetectionBadge(status: VehicleDetectionStatus) {
    when (status) {
        is VehicleDetectionStatus.Bluetooth -> PapBadge(
            label = "${stringResource(Res.string.vehicle_card_detection_bt)} · ${status.deviceLabel}",
            containerColor = PapBlueMuted,
            contentColor = PapBlue,
            icon = Icons.Outlined.BluetoothConnected,
        )
        VehicleDetectionStatus.ActivityRecognition -> PapBadge(
            label = stringResource(Res.string.vehicle_card_detection_ar),
            containerColor = PapGreenMuted,
            contentColor = PapGreen,
            icon = Icons.Outlined.DirectionsCar,
        )
        VehicleDetectionStatus.Disabled -> PapBadge(
            label = stringResource(Res.string.vehicle_card_detection_off),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_DETECTION_ALPHA),
        )
    }
}
