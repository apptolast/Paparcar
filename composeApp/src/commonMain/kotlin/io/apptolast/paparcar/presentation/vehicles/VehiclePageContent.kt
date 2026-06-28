@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.vehicles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.CircularProgressIndicator
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
import io.apptolast.paparcar.domain.model.VehicleMonitoringStatus
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.model.VehicleWithStats
import io.apptolast.paparcar.domain.model.displayName
import io.apptolast.paparcar.domain.model.monitoringStatus
import io.apptolast.paparcar.presentation.util.compactRelativeTimeText
import io.apptolast.paparcar.ui.icons.icon
import io.apptolast.paparcar.ui.theme.PapBorders
import io.apptolast.paparcar.ui.theme.PapShapes
import io.apptolast.paparcar.ui.components.VehicleBadgeTone
import io.apptolast.paparcar.ui.components.VehicleGlyph
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
    isSettingActive: Boolean,
    onIntent: (VehiclesIntent) -> Unit,
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
                    reliabilityPct = historyState.statsData?.avgReliabilityPct,
                    isSettingActive = isSettingActive,
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
    isSettingActive: Boolean,
    onSetActive: () -> Unit,
    onEdit: () -> Unit,
) {
    val vehicle = vehicleWithStats.vehicle
    val displayName = vehicle.displayName(fallback = stringResource(Res.string.my_car_unnamed_vehicle))
    val sizeLabel = vehicleSizeLabel(vehicle.sizeCategory)
    val monitoring = vehicle.monitoringStatus()
    val highlight = monitoring !is VehicleMonitoringStatus.Inactive
    val isBtPaired = vehicle.bluetoothDeviceId != null
    val cs = MaterialTheme.colorScheme
    // Unified semantic badge — same element as the Home chip / map marker. In this screen the
    // *active* (monitored) vehicle is the one that matters → green (vibrant); BT → blue; the rest
    // stay neutral grey. Identity still comes from the silhouette + name. [DET-READY-001k]
    val badgeTone = when {
        isBtPaired -> VehicleBadgeTone.Bluetooth
        highlight  -> VehicleBadgeTone.Parked
        else       -> VehicleBadgeTone.Inactive
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        shape = PapShapes.cardLarge,
        color = cs.surfaceContainerHigh,
        border = BorderStroke(PapBorders.thin, cs.outlineVariant.copy(alpha = CARD_BORDER_ALPHA)),
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
                    VehicleGlyph(
                        carbody = vehicle.carbodyType,
                        size = vehicle.sizeCategory,
                        tone = badgeTone,
                        glyphSize = HERO_ICON_BOX_DP.dp,
                    )
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
                        status = monitoring,
                        isSettingActive = isSettingActive,
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
private fun VehicleStatusBadge(
    status: VehicleMonitoringStatus,
    isSettingActive: Boolean,
    onSetActive: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    when (status) {
        is VehicleMonitoringStatus.Bluetooth -> VehicleStatusPill(
            text = stringResource(Res.string.vehicle_card_detection_bt),
            accent = cs.tertiary,
            leading = PillLeading.BtIcon,
            onClick = null,
            isLoading = false,
        )
        VehicleMonitoringStatus.Active -> VehicleStatusPill(
            text = stringResource(Res.string.my_car_active_vehicle),
            accent = cs.primary,
            leading = PillLeading.Dot,
            onClick = null,
            isLoading = false,
        )
        VehicleMonitoringStatus.Inactive -> VehicleStatusPill(
            text = stringResource(Res.string.my_car_set_active),
            accent = cs.outline,
            leading = PillLeading.Dot,
            onClick = onSetActive,
            isLoading = isSettingActive,
        )
    }
}

private enum class PillLeading { None, Dot, BtIcon }

/**
 * Outlined pill used for the three vehicle-monitoring states. Same height, padding
 * and typography across states so Active / Bluetooth / Set-active align visually
 * in the hero card. Text stays high-contrast ([onSurface]) so the [accent] only
 * paints the border + leading marker. The action variant ([onClick] non-null)
 * drops the marker. When [isLoading] is true the leading marker is replaced by
 * a spinner and the click is suppressed.
 */
@Composable
private fun VehicleStatusPill(
    text: String,
    accent: Color,
    leading: PillLeading,
    onClick: (() -> Unit)?,
    isLoading: Boolean,
) {
    val shape = RoundedCornerShape(PILL_RADIUS_DP.dp)
    val border = BorderStroke(1.dp, accent)
    val content: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            when {
                isLoading -> CircularProgressIndicator(
                    modifier = Modifier.size(BADGE_ICON_DP.dp),
                    strokeWidth = 2.dp,
                    color = accent,
                )
                leading == PillLeading.Dot -> Box(
                    modifier = Modifier
                        .size(ACTIVE_DOT_DP.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
                leading == PillLeading.BtIcon -> Icon(
                    imageVector = Icons.Outlined.Bluetooth,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(BADGE_ICON_DP.dp),
                )
                else -> Unit
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
    val safeOnClick: (() -> Unit)? = onClick?.takeIf { !isLoading }
    if (safeOnClick != null) {
        Surface(onClick = safeOnClick, shape = shape, color = Color.Transparent, border = border) { content() }
    } else {
        Surface(shape = shape, color = Color.Transparent, border = border) { content() }
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
        color = cs.surfaceContainerHigh,
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
    VehicleSize.MOTORCYCLE   -> stringResource(Res.string.vehicle_size_moto)
    VehicleSize.MICRO_SMALL  -> stringResource(Res.string.vehicle_size_small)
    VehicleSize.MEDIUM_SUV -> stringResource(Res.string.vehicle_size_medium)
    VehicleSize.LARGE_SEDAN  -> stringResource(Res.string.vehicle_size_large)
    VehicleSize.VAN_HIGH    -> stringResource(Res.string.vehicle_size_van)
}

private const val CARD_PADDING = 16
private const val CARD_BORDER_ALPHA = 0.5f
private const val HERO_ICON_BOX_DP = 56
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
