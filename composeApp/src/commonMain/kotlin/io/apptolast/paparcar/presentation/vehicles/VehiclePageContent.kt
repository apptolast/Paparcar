package io.apptolast.paparcar.presentation.vehicles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.model.VehicleWithStats
import io.apptolast.paparcar.presentation.history.HistoryContent
import io.apptolast.paparcar.presentation.history.HistoryEffect
import io.apptolast.paparcar.presentation.history.HistoryIntent
import io.apptolast.paparcar.presentation.history.HistoryViewModel
import io.apptolast.paparcar.ui.components.PapSecondaryButton
import io.apptolast.paparcar.ui.components.VehicleDetectionStatus
import io.apptolast.paparcar.ui.icons.icon
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
import paparcar.composeapp.generated.resources.my_car_more_actions
import paparcar.composeapp.generated.resources.my_car_set_active
import paparcar.composeapp.generated.resources.my_car_unnamed_vehicle
import paparcar.composeapp.generated.resources.vehicle_card_detection_ar
import paparcar.composeapp.generated.resources.vehicle_card_detection_bt
import paparcar.composeapp.generated.resources.vehicle_size_large
import paparcar.composeapp.generated.resources.vehicle_size_medium
import paparcar.composeapp.generated.resources.vehicle_size_moto
import paparcar.composeapp.generated.resources.vehicle_size_small
import paparcar.composeapp.generated.resources.vehicle_size_van

/**
 * A single page inside the vehicles HorizontalPager.
 *
 * Contains:
 * 1. An "identity card" header with the size emoji, brand/model, status,
 *    detection chip, and primary actions. Surface stays neutral; the active
 *    vehicle is signalled via the "Activo" tag in [MaterialTheme.colorScheme.primary]
 *    in the subtitle plus the dot in the tab row. A coloured Surface tint was
 *    tried in [UI-002] but the dark-theme primaryContainer was too saturated
 *    against the neutral surface — uniform background reads cleaner.
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
        VehicleIdentityCard(
            vehicleWithStats = vehicleWithStats,
            canDelete = canDelete,
            onSetActive = { onIntent(VehiclesIntent.SetActiveVehicle(vehicleId)) },
            onEdit = { onIntent(VehiclesIntent.EditVehicle(vehicleId)) },
            onDelete = { onIntent(VehiclesIntent.RequestDeleteVehicle(vehicleId)) },
            onConfigureBluetooth = { onConfigureBluetooth(vehicleId) },
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
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

// ─── Identity card ────────────────────────────────────────────────────────────

@Composable
private fun VehicleIdentityCard(
    vehicleWithStats: VehicleWithStats,
    canDelete: Boolean,
    onSetActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onConfigureBluetooth: () -> Unit,
) {
    val vehicle = vehicleWithStats.vehicle
    // When the user didn't provide brand or model, fall back to a dedicated
    // placeholder string so the title doesn't echo the size label that is
    // already shown in the subtitle. The emoji + size pair still gives visual
    // identity. [UI-002]
    val displayName = listOfNotNull(vehicle.brand, vehicle.model)
        .joinToString(" ")
        .ifBlank { stringResource(Res.string.my_car_unnamed_vehicle) }
    val sizeLabel = vehicleSizeLabel(vehicle.sizeCategory)
    val activeLabel = stringResource(Res.string.my_car_active_vehicle)
    val primaryColor = MaterialTheme.colorScheme.primary
    val subtitleText = if (vehicle.isDefault) {
        buildAnnotatedString {
            append("$sizeLabel · ")
            withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Medium)) {
                append(activeLabel)
            }
        }
    } else {
        buildAnnotatedString { append(sizeLabel) }
    }
    val detectionStatus = if (vehicle.bluetoothDeviceId != null) {
        VehicleDetectionStatus.Bluetooth(vehicle.bluetoothDeviceId.takeLast(BT_LABEL_LENGTH))
    } else {
        VehicleDetectionStatus.ActivityRecognition
    }

    Surface(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(
                horizontal = PaparcarSpacing.lg,
                vertical = PaparcarSpacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(PaparcarSpacing.sm),
        ) {
            // ── Row 1: vehicle size icon + name/subtitle + kebab ─────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = vehicle.sizeCategory.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(VEHICLE_ICON_SIZE),
                )
                Spacer(Modifier.width(PaparcarSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OverflowMenu(
                    canDelete = canDelete,
                    onEdit = onEdit,
                    onConfigureBluetooth = onConfigureBluetooth,
                    onDelete = onDelete,
                )
            }

            // ── Row 2: detection chip (BT configure moved to overflow menu) ──
            DetectionChip(status = detectionStatus)

            // ── Row 3 (only when NOT active): set-active CTA ─────────────────
            if (!vehicle.isDefault) {
                PapSecondaryButton(
                    label = stringResource(Res.string.my_car_set_active),
                    onClick = onSetActive,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun OverflowMenu(
    canDelete: Boolean,
    onEdit: () -> Unit,
    onConfigureBluetooth: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Outlined.MoreVert,
                contentDescription = stringResource(Res.string.my_car_more_actions),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.my_car_edit_vehicle)) },
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.my_car_bt_configure)) },
                leadingIcon = { Icon(Icons.Outlined.Bluetooth, contentDescription = null) },
                onClick = {
                    expanded = false
                    onConfigureBluetooth()
                },
            )
            // Delete is rendered with translucent tint when canDelete=false; the click
            // still propagates so the VM can surface the "must keep one" snackbar.
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(Res.string.my_car_delete_vehicle),
                        color = MaterialTheme.colorScheme.error.copy(
                            alpha = if (canDelete) DELETE_ENABLED_ALPHA else DELETE_DISABLED_ALPHA,
                        ),
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error.copy(
                            alpha = if (canDelete) DELETE_ENABLED_ALPHA else DELETE_DISABLED_ALPHA,
                        ),
                    )
                },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

// ─── Detection chip ───────────────────────────────────────────────────────────

@Composable
private fun DetectionChip(
    status: VehicleDetectionStatus,
    modifier: Modifier = Modifier,
) {
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
        modifier = modifier,
        shape = RoundedCornerShape(CHIP_CORNER_RADIUS),
        color = bg,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PaparcarSpacing.sm, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            icon?.let {
                Icon(it, contentDescription = null, modifier = Modifier.size(CHIP_ICON_SIZE), tint = fg)
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = fg,
                maxLines = 1,
            )
        }
    }
}

private data class DetectionChipData(
    val label: String,
    val bg: androidx.compose.ui.graphics.Color,
    val fg: androidx.compose.ui.graphics.Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector?,
)

// ─── Helpers ──────────────────────────────────────────────────────────────────

@Composable
private fun vehicleSizeLabel(size: VehicleSize): String = when (size) {
    VehicleSize.MOTO   -> stringResource(Res.string.vehicle_size_moto)
    VehicleSize.SMALL  -> stringResource(Res.string.vehicle_size_small)
    VehicleSize.MEDIUM -> stringResource(Res.string.vehicle_size_medium)
    VehicleSize.LARGE  -> stringResource(Res.string.vehicle_size_large)
    VehicleSize.VAN    -> stringResource(Res.string.vehicle_size_van)
}

private val VEHICLE_ICON_SIZE = 44.dp
private val CHIP_CORNER_RADIUS = 12.dp
private val CHIP_ICON_SIZE = 14.dp
private const val BT_LABEL_LENGTH = 5
private const val DELETE_ENABLED_ALPHA = 1.0f
private const val DELETE_DISABLED_ALPHA = 0.35f
