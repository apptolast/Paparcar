@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.vehicles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.DirectionsCar
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.model.VehicleWithStats
import io.apptolast.paparcar.presentation.history.HistoryContent
import io.apptolast.paparcar.presentation.history.HistoryEffect
import io.apptolast.paparcar.presentation.history.HistoryIntent
import io.apptolast.paparcar.presentation.history.HistoryViewModel
import io.apptolast.paparcar.presentation.util.compactRelativeTimeText
import io.apptolast.paparcar.ui.icons.icon
import io.apptolast.paparcar.ui.theme.PapBorders
import io.apptolast.paparcar.ui.theme.PapShapes
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
import paparcar.composeapp.generated.resources.vehicle_stats_last_session
import paparcar.composeapp.generated.resources.vehicle_stats_reliability
import paparcar.composeapp.generated.resources.vehicle_stats_sessions

/**
 * VehiclePageContent (v1 redesign) — Vehicles + History fusionado.
 *
 * Layout:
 *  1. VehicleHeroCard       — bg coloreado si es activo
 *  2. VehicleStatsCompact   — 3 inline mini cards: sesiones · última · fiabilidad
 *                              (sólo cuando hay sesiones)
 *  3. HistoryContent        — chart + filter + timeline (HOY/AYER…) — embebido
 *                              con `showInternalStats = false` para no duplicar
 *                              el StatsRow / InsightsCard del modo standalone.
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
        VehicleHeroCard(
            vehicleWithStats = vehicleWithStats,
            canDelete = canDelete,
            onSetActive = { onIntent(VehiclesIntent.SetActiveVehicle(vehicleId)) },
            onEdit = { onIntent(VehiclesIntent.EditVehicle(vehicleId)) },
            onDelete = { onIntent(VehiclesIntent.RequestDeleteVehicle(vehicleId)) },
            onConfigureBluetooth = { onConfigureBluetooth(vehicleId) },
        )
        if (vehicleWithStats.sessionCount > 0) {
            VehicleStatsCompact(
                sessionCount = vehicleWithStats.sessionCount,
                lastSessionMs = vehicleWithStats.lastSession?.location?.timestamp,
                reliabilityPct = historyState.statsData?.avgReliabilityPct,
            )
        }
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
            showInternalStats = false,
        )
    }
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
    // Active state used to render with a 1dp neon-green border (cs.primary). That
    // read as "radioactive" against the dark page. The selected affordance now lives
    // in the green tinted background + ActiveBadge; the border stays subtle so the
    // family feels coherent with the parking-card reference. [DS-BORDERS-001]
    val borderColor = cs.outline.copy(alpha = PapBorders.DEFAULT_OUTLINE_ALPHA)
    val iconBg = if (isActive) cs.primary else cs.surfaceVariant
    val iconTint = if (isActive) cs.onPrimary else cs.primary

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = PapShapes.cardLarge,
        color = cardBg,
        border = BorderStroke(PapBorders.thin, borderColor),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Title row owns the full width (minus icon + overflow). The
            // "active" badge used to live here too and ate ~40% of the row,
            // ellipsing brand/model names. It now sits on the metadata row
            // below so the title can breathe.
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
            // Metadata row: size label on the left, status (active dot+label
            // or "set active" CTA) on the right. The status text is small and
            // primary-tinted so it reads as a tag, not a button.
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
            Spacer(Modifier.height(10.dp))
            DetectionChip(bluetoothDeviceId = vehicle.bluetoothDeviceId)
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
// Matches the design's compact stats strip below the hero card. Replaces the
// previous 2-card big-number layout.
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
private fun DetectionChip(bluetoothDeviceId: String?) {
    val cs = MaterialTheme.colorScheme
    val data: DetectionChipData = if (bluetoothDeviceId != null) {
        DetectionChipData(
            label = "${stringResource(Res.string.vehicle_card_detection_bt)} · ${bluetoothDeviceId.takeLast(BT_LABEL_LENGTH)}",
            bg = cs.tertiaryContainer.copy(alpha = CHIP_BG_ALPHA),
            fg = cs.tertiary,
            icon = Icons.Outlined.Bluetooth,
        )
    } else {
        DetectionChipData(
            label = stringResource(Res.string.vehicle_card_detection_ar),
            bg = cs.secondaryContainer.copy(alpha = CHIP_BG_ALPHA),
            fg = cs.secondary,
            icon = Icons.Outlined.DirectionsCar,
        )
    }
    Surface(
        shape = RoundedCornerShape(CHIP_CORNER_DP.dp),
        color = data.bg,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(data.icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = data.fg)
            Text(
                text = data.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = data.fg,
                maxLines = 1,
            )
        }
    }
}

private data class DetectionChipData(
    val label: String,
    val bg: Color,
    val fg: Color,
    val icon: ImageVector,
)

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
            // Delete: disabled (no click, no visual response) when this is
            // the user's only vehicle. The "must keep at least one"
            // explanation lives in the EmptyVehicleState copy / the
            // CannotDeleteLastVehicle snackbar (triggered from the VM via
            // a different code path); here we just refuse the action so
            // the menu item doesn't look broken.
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
