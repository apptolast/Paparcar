package io.apptolast.paparcar.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothConnected
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import paparcar.composeapp.generated.resources.my_car_set_active
import paparcar.composeapp.generated.resources.vehicle_card_detection_ar
import paparcar.composeapp.generated.resources.vehicle_card_detection_bt
import paparcar.composeapp.generated.resources.vehicle_card_detection_off

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

private val   CarIconSize                 = 20.dp
private const val ACTIVE_CARD_CONTAINER_ALPHA = 0.35f
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
    modifier: Modifier = Modifier,
) {
    PapCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = if (data.isActive)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = ACTIVE_CARD_CONTAINER_ALPHA)
        else
            MaterialTheme.colorScheme.surface,
    ) {
        // ── Header: icon + name + active badge ───────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PaparcarSpacing.md),
        ) {
            Icon(
                imageVector = Icons.Outlined.DirectionsCar,
                contentDescription = null,
                tint = if (data.isActive) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = INACTIVE_ICON_ALPHA),
                modifier = Modifier.size(CarIconSize),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = data.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = data.sizeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (data.isActive) {
                PapStatusBadge(label = stringResource(Res.string.my_car_active_vehicle))
            }
        }

        Spacer(Modifier.height(PaparcarSpacing.sm))

        // ── Detection status row ──────────────────────────────────────────────
        VehicleDetectionBadge(status = data.detectionStatus)

        Spacer(Modifier.height(PaparcarSpacing.md))

        // ── Action buttons ────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PaparcarSpacing.sm),
        ) {
            if (!data.isActive) {
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
