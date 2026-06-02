@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.vehicles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.model.VehicleWithStats
import io.apptolast.paparcar.presentation.util.compactRelativeTimeText
import io.apptolast.paparcar.ui.icons.icon
import io.apptolast.paparcar.ui.theme.PapBorders
import io.apptolast.paparcar.ui.theme.PapShapes
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.my_car_active_vehicle
import paparcar.composeapp.generated.resources.my_car_bt_configure
import paparcar.composeapp.generated.resources.my_car_delete_vehicle
import paparcar.composeapp.generated.resources.my_car_edit_vehicle
import paparcar.composeapp.generated.resources.my_car_more_actions
import paparcar.composeapp.generated.resources.my_car_set_active
import paparcar.composeapp.generated.resources.my_car_unnamed_vehicle
import paparcar.composeapp.generated.resources.vehicle_card_detection_bt
import paparcar.composeapp.generated.resources.vehicle_size_large
import paparcar.composeapp.generated.resources.vehicle_size_medium
import paparcar.composeapp.generated.resources.vehicle_size_moto
import paparcar.composeapp.generated.resources.vehicle_size_small
import paparcar.composeapp.generated.resources.vehicle_size_van
import paparcar.composeapp.generated.resources.vehicle_stats_last_session
import paparcar.composeapp.generated.resources.vehicle_stats_reliability
import paparcar.composeapp.generated.resources.vehicle_stats_sessions

@Composable
internal fun VehiclePageContent(
    vehicleWithStats: VehicleWithStats,
    historyState: HistoryState,
    onIntent: (VehiclesIntent) -> Unit,
    onConfigureBluetooth: (vehicleId: String) -> Unit,
    canDelete: Boolean = true,
) {
    val vehicleId = vehicleWithStats.vehicle.id

    HistoryContent(
        state = historyState,
        contentPadding = PaddingValues(0.dp),
        onViewOnMap = { lat, lon, sessionId -> onIntent(VehiclesIntent.ViewOnMap(lat, lon, sessionId)) },
        onFilterSelected = { filter -> onIntent(VehiclesIntent.SetHistoryFilter(filter)) },
        modifier = Modifier.fillMaxSize(),
        showInternalStats = false,
        headerSlot = {
            item(key = "vehicle_hero") {
                VehicleHeroCard(
                    vehicleWithStats = vehicleWithStats,
                    canDelete = canDelete,
                    onSetActive = { onIntent(VehiclesIntent.SetActiveVehicle(vehicleId)) },
                    onEdit = { onIntent(VehiclesIntent.EditVehicle(vehicleId)) },
                    onDelete = { onIntent(VehiclesIntent.RequestDeleteVehicle(vehicleId)) },
                    onConfigureBluetooth = { onConfigureBluetooth(vehicleId) },
                )
            }
            if (vehicleWithStats.sessionCount > 0) {
                item(key = "vehicle_stats") {
                    VehicleStatsCompact(
                        sessionCount = vehicleWithStats.sessionCount,
                        lastSessionMs = vehicleWithStats.lastSession?.location?.timestamp,
                        reliabilityPct = historyState.statsData?.avgReliabilityPct,
                    )
                }
            }
            item(key = "header_chart_gap") { Spacer(Modifier.height(6.dp)) }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Hero card — identity + active badge + detection chip + (set active CTA)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VehicleHeroCard(
    vehicleWithStats: VehicleWithStats,
    canDelete: Boolean,
    onSetActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onConfigureBluetooth: () -> Unit,
) {
    val vehicle = vehicleWithStats.vehicle
    val displayName = listOfNotNull(vehicle.brand, vehicle.model)
        .joinToString(" ")
        .ifBlank { stringResource(Res.string.my_car_unnamed_vehicle) }
    val sizeLabel = vehicleSizeLabel(vehicle.sizeCategory)
    val isActive = vehicle.isDefault

    val cs = MaterialTheme.colorScheme
    val cardBg = if (isActive) cs.primaryContainer.copy(alpha = ACTIVE_CARD_BG_ALPHA) else cs.surfaceContainerHigh
    // Active state border is intentionally subtle — the green background already signals selection. [DS-BORDERS-001]
    val borderColor = cs.outline.copy(alpha = PapBorders.DEFAULT_OUTLINE_ALPHA)
    val iconBg = if (isActive) cs.primary else cs.surfaceVariant
    val iconTint = if (isActive) cs.onPrimary else cs.primary

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
        shape = PapShapes.cardLarge,
        color = cardBg,
        border = BorderStroke(PapBorders.thin, borderColor),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(HERO_ICON_BOX_DP.dp)
                        .clip(RoundedCornerShape(HERO_ICON_CORNER_DP.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = vehicle.sizeCategory.icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                OverflowMenu(
                    canDelete = canDelete,
                    onEdit = onEdit,
                    onConfigureBluetooth = onConfigureBluetooth,
                    onDelete = onDelete,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = sizeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurface.copy(alpha = SUBTITLE_ALPHA),
                    modifier = Modifier.weight(1f),
                )
                if (isActive) {
                    ActiveStatusInline()
                } else {
                    SetActiveButton(onClick = onSetActive)
                }
            }
            if (vehicle.bluetoothDeviceId != null) {
                Spacer(Modifier.height(10.dp))
                DetectionChip(bluetoothDeviceId = vehicle.bluetoothDeviceId)
            }
        }
    }
}

@Composable
private fun ActiveStatusInline() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(ACTIVE_DOT_DP.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Text(
            text = stringResource(Res.string.my_car_active_vehicle).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SetActiveButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(PILL_RADIUS_DP.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
    ) {
        Text(
            text = stringResource(Res.string.my_car_set_active),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// VehicleStatsCompact — 3 inline mini stat cards (sessions · last · reliability)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VehicleStatsCompact(
    sessionCount: Int,
    lastSessionMs: Long?,
    reliabilityPct: Int?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatCard(
            value = sessionCount.toString(),
            label = stringResource(Res.string.vehicle_stats_sessions),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            value = lastSessionMs?.let { compactRelativeTimeText(it) } ?: "—",
            label = stringResource(Res.string.vehicle_stats_last_session),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            value = reliabilityPct?.let { "$it%" } ?: "—",
            label = stringResource(Res.string.vehicle_stats_reliability),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(STAT_CARD_CORNER_DP.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = STAT_CARD_BORDER_ALPHA),
        ),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = STAT_LABEL_TRACKING_SP.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = STAT_CARD_LABEL_ALPHA),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Detection chip — BT (tertiary) vs AR (secondary)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DetectionChip(bluetoothDeviceId: String) {
    val cs = MaterialTheme.colorScheme
    val label = "${stringResource(Res.string.vehicle_card_detection_bt)} · ${bluetoothDeviceId.takeLast(BT_LABEL_LENGTH)}"
    Surface(
        shape = RoundedCornerShape(CHIP_CORNER_DP.dp),
        color = cs.tertiaryContainer.copy(alpha = CHIP_BG_ALPHA),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Outlined.Bluetooth, contentDescription = null, modifier = Modifier.size(14.dp), tint = cs.tertiary)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = cs.tertiary,
                maxLines = 1,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Overflow menu
// ─────────────────────────────────────────────────────────────────────────────

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
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = OVERFLOW_ICON_ALPHA),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.my_car_edit_vehicle)) },
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                onClick = { expanded = false; onEdit() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.my_car_bt_configure)) },
                leadingIcon = { Icon(Icons.Outlined.Bluetooth, contentDescription = null) },
                onClick = { expanded = false; onConfigureBluetooth() },
            )
            // Disabled (not hidden) so the affordance stays visible; snackbar explains why.
            DropdownMenuItem(
                enabled = canDelete,
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
                onClick = { expanded = false; onDelete() },
            )
        }
    }
}

@Composable
private fun vehicleSizeLabel(size: VehicleSize): String = when (size) {
    VehicleSize.MOTO   -> stringResource(Res.string.vehicle_size_moto)
    VehicleSize.SMALL  -> stringResource(Res.string.vehicle_size_small)
    VehicleSize.MEDIUM -> stringResource(Res.string.vehicle_size_medium)
    VehicleSize.LARGE  -> stringResource(Res.string.vehicle_size_large)
    VehicleSize.VAN    -> stringResource(Res.string.vehicle_size_van)
}

private const val HERO_ICON_BOX_DP = 40
private const val HERO_ICON_CORNER_DP = 10
private const val STAT_CARD_CORNER_DP = 12
private const val CHIP_CORNER_DP = 12
private const val PILL_RADIUS_DP = 999
private const val ACTIVE_DOT_DP = 6

private const val ACTIVE_CARD_BG_ALPHA = 0.5f
private const val SUBTITLE_ALPHA = 0.55f
private const val STAT_CARD_BORDER_ALPHA = 0.35f
private const val STAT_CARD_LABEL_ALPHA = 0.55f
private const val STAT_LABEL_TRACKING_SP = 0.6
private const val CHIP_BG_ALPHA = 0.6f
private const val OVERFLOW_ICON_ALPHA = 0.7f
private const val DELETE_ENABLED_ALPHA = 1.0f
private const val DELETE_DISABLED_ALPHA = 0.35f
private const val BT_LABEL_LENGTH = 5
