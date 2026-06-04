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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.model.VehicleWithStats
import io.apptolast.paparcar.presentation.util.compactRelativeTimeText
import io.apptolast.paparcar.ui.icons.icon
import io.apptolast.paparcar.ui.theme.PapBorders
import io.apptolast.paparcar.ui.theme.PapShapes
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.my_car_active_vehicle
import paparcar.composeapp.generated.resources.my_car_edit_vehicle
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
) {
    val vehicleId = vehicleWithStats.vehicle.id

    HistoryContent(
        state = historyState,
        contentPadding = PaddingValues(0.dp),
        onViewOnMap = { lat, lon, sessionId -> onIntent(VehiclesIntent.ViewOnMap(lat, lon, sessionId)) },
        onFilterSelected = { filter -> onIntent(VehiclesIntent.SetHistoryFilter(filter)) },
        onLoadMore = { onIntent(VehiclesIntent.LoadNextHistoryPage) },
        modifier = Modifier.fillMaxSize(),
        showInternalStats = false,
        headerSlot = {
            item(key = "vehicle_hero") {
                VehicleHeroCard(
                    vehicleWithStats = vehicleWithStats,
                    reliabilityPct = historyState.statsData?.avgReliabilityPct,
                    onSetActive = { onIntent(VehiclesIntent.SetActiveVehicle(vehicleId)) },
                    onEdit = { onIntent(VehiclesIntent.EditVehicle(vehicleId)) },
                )
            }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Hero card — icon | name+subtitle+status | overflow · inline stats inside
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VehicleHeroCard(
    vehicleWithStats: VehicleWithStats,
    reliabilityPct: Int?,
    onSetActive: () -> Unit,
    onEdit: () -> Unit,
) {
    val vehicle = vehicleWithStats.vehicle
    val displayName = listOfNotNull(vehicle.brand, vehicle.model)
        .joinToString(" ")
        .ifBlank { stringResource(Res.string.my_car_unnamed_vehicle) }
    val sizeLabel = vehicleSizeLabel(vehicle.sizeCategory)
    val isActive = vehicle.isActive
    val cs = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        shape = PapShapes.cardLarge,
        color = cs.surfaceContainerHigh,
        border = BorderStroke(PapBorders.medium, cs.outline.copy(alpha = HERO_BORDER_ALPHA)),
    ) {
        Column(modifier = Modifier.padding(CARD_PADDING.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // START — circle icon + name/subtitle column
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(HERO_ICON_BOX_DP.dp)
                            .clip(CircleShape)
                            .background(if (isActive) cs.primary else cs.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = vehicle.sizeCategory.icon,
                            contentDescription = null,
                            tint = if (isActive) cs.onPrimary else cs.primary,
                            modifier = Modifier.size(HERO_ICON_SIZE_DP.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = cs.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = sizeLabel.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.onSurface.copy(alpha = SUBTITLE_ALPHA),
                        )
                    }
                }
                // END — edit icon + status badge, centered vertically
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center,
                ) {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = stringResource(Res.string.my_car_edit_vehicle),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = EDIT_ICON_ALPHA),
                        )
                    }
                    VehicleStatusBadge(
                        isActive = isActive,
                        hasBluetooth = vehicle.bluetoothDeviceId != null,
                        onSetActive = onSetActive,
                    )
                }
            }

            // Stat mini-cards — shown inside the card when sessions exist
            if (vehicleWithStats.sessionCount > 0) {
                Spacer(Modifier.height(STATS_DIVIDER_PAD.dp))
                InlineStatsRow(
                    sessionCount = vehicleWithStats.sessionCount,
                    lastSessionMs = vehicleWithStats.lastSession?.location?.timestamp,
                    reliabilityPct = reliabilityPct,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Vehicle status badge — three states
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VehicleStatusBadge(isActive: Boolean, hasBluetooth: Boolean, onSetActive: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    when {
        isActive && hasBluetooth -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Bluetooth,
                contentDescription = null,
                tint = cs.tertiary,
                modifier = Modifier.size(BADGE_ICON_DP.dp),
            )
            Text(
                text = stringResource(Res.string.vehicle_card_detection_bt),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurfaceVariant,
            )
        }
        isActive -> ActiveStatusInline()
        else -> SetActiveButton(onClick = onSetActive)
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
// Stat mini-cards — bordered surfaces inside the hero card, no divider
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InlineStatsRow(
    sessionCount: Int,
    lastSessionMs: Long?,
    reliabilityPct: Int?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(STAT_CARD_GAP.dp),
    ) {
        StatMiniCard(
            icon = Icons.Outlined.TrendingUp,
            value = sessionCount.toString(),
            label = stringResource(Res.string.vehicle_stats_sessions),
            modifier = Modifier.weight(1f),
        )
        StatMiniCard(
            icon = Icons.Outlined.Schedule,
            value = lastSessionMs?.let { compactRelativeTimeText(it) } ?: "—",
            label = stringResource(Res.string.vehicle_stats_last_session),
            modifier = Modifier.weight(1f),
        )
        StatMiniCard(
            icon = Icons.Outlined.GppGood,
            value = reliabilityPct?.let { "$it%" } ?: "—",
            label = stringResource(Res.string.vehicle_stats_reliability),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatMiniCard(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(STAT_CARD_CORNER_DP.dp),
        color = cs.surfaceContainerHighest,
        border = BorderStroke(PapBorders.thin, cs.outline.copy(alpha = PapBorders.DEFAULT_OUTLINE_ALPHA)),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = STAT_CARD_H_PAD.dp, vertical = STAT_CARD_V_PAD.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(STAT_ICON_GAP.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(STAT_ICON_DP.dp),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface,
                    maxLines = 1,
                )
            }
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Normal,
                color = cs.onSurface.copy(alpha = STAT_LABEL_ALPHA),
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

private const val CARD_PADDING = 16
private const val HERO_BORDER_ALPHA = 0.55f
private const val HERO_ICON_BOX_DP = 56
private const val HERO_ICON_SIZE_DP = 28
private const val PILL_RADIUS_DP = 999
private const val ACTIVE_DOT_DP = 6
private const val BADGE_ICON_DP = 14
private const val STATS_DIVIDER_PAD = 12
private const val STAT_CARD_GAP = 6
private const val STAT_CARD_CORNER_DP = 10
private const val STAT_CARD_H_PAD = 10
private const val STAT_CARD_V_PAD = 8
private const val STAT_ICON_DP = 18
private const val STAT_ICON_GAP = 6
private const val STAT_LABEL_ALPHA = 0.5f
private const val SUBTITLE_ALPHA = 0.55f
private const val EDIT_ICON_ALPHA = 0.7f
