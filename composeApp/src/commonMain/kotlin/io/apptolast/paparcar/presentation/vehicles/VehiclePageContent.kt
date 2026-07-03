@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.vehicles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.ui.components.PapDivider
import io.apptolast.paparcar.ui.components.PapVerticalDivider
import io.apptolast.paparcar.domain.model.VehicleMonitoringStatus
import io.apptolast.paparcar.domain.model.VehicleWithStats
import io.apptolast.paparcar.domain.model.monitoringStatus
import io.apptolast.paparcar.presentation.util.compactRelativeTimeText
import io.apptolast.paparcar.ui.components.VehicleIdentityHeader
import io.apptolast.paparcar.ui.components.vehicleStatusBorderColor
import io.apptolast.paparcar.ui.theme.PapBorders
import io.apptolast.paparcar.ui.theme.PaparcarType
import io.apptolast.paparcar.ui.theme.PapShapes
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.my_car_edit_vehicle
import paparcar.composeapp.generated.resources.vehicle_set_active_action
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
        header = {
            VehicleHeroCard(
                vehicleWithStats = vehicleWithStats,
                reliabilityPct = historyState.statsData?.avgReliabilityPct,
                isSettingActive = isSettingActive,
                onSetActive = { onIntent(VehiclesIntent.SetActiveVehicle(vehicleId)) },
                onEdit = { onIntent(VehiclesIntent.EditVehicle(vehicleId)) },
            )
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Hero card — glyph + name + status pin + size chip · stats row · set-active row
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
    val monitoring = vehicle.monitoringStatus()
    val isInactive = monitoring == VehicleMonitoringStatus.Inactive
    val hasStats = vehicleWithStats.sessionCount > 0
    val cs = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        shape = PapShapes.cardLarge,
        color = cs.surfaceContainerHigh,
        // The frame reads the state — muted status accent (green/blue) or neutral when inactive,
        // same border language as the Home chips. [HOME-VEH-REFINE-001]
        border = BorderStroke(PapBorders.thin, vehicleStatusBorderColor(monitoring)),
    ) {
        Column {
            // Shared identity anatomy (tile glyph + name + size chip + status pin) — single source
            // of truth with the Home single-vehicle card. Edit rides as the trailing action, in a
            // compact box so it doesn't push the meta row with a 48dp dead zone.
            VehicleIdentityHeader(
                vehicle = vehicle,
                modifier = Modifier.padding(
                    start = CARD_PADDING.dp, end = CARD_PADDING.dp,
                    top = IDENTITY_TOP_PAD.dp, bottom = IDENTITY_BOTTOM_PAD.dp,
                ),
                trailing = {
                    IconButton(onClick = onEdit, modifier = Modifier.size(EDIT_BOX_DP.dp)) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = stringResource(Res.string.my_car_edit_vehicle),
                            tint = cs.onSurface.copy(alpha = EDIT_ICON_ALPHA),
                        )
                    }
                },
            )

            // Stats — a divided bottom row (Sessions / Last / Reliability). Muted for an inactive
            // vehicle that still has history. Hidden entirely with no sessions. [HOME-VEH-REFINE-001]
            if (hasStats) {
                PapDivider()
                VehicleStatsRow(
                    sessionCount = vehicleWithStats.sessionCount,
                    lastSessionMs = vehicleWithStats.lastSession?.location?.timestamp,
                    reliabilityPct = reliabilityPct,
                    muted = isInactive,
                )
            }

            // "Set as active" is its own row — and only for an inactive vehicle. Active / Bluetooth
            // vehicles are already the tracked one, so the action never appears for them.
            if (isInactive) {
                PapDivider()
                SetActiveRow(isLoading = isSettingActive, onClick = onSetActive)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Stats row — three divided cells at the card foot
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VehicleStatsRow(
    sessionCount: Int,
    lastSessionMs: Long?,
    reliabilityPct: Int?,
    muted: Boolean,
) {
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        StatCell(
            icon = Icons.AutoMirrored.Rounded.TrendingUp,
            value = sessionCount.toString(),
            label = stringResource(Res.string.vehicle_stats_sessions),
            muted = muted,
            modifier = Modifier.weight(1f),
        )
        StatDivider()
        StatCell(
            icon = Icons.Rounded.Schedule,
            value = lastSessionMs?.let { compactRelativeTimeText(it) } ?: "—",
            label = stringResource(Res.string.vehicle_stats_last_session),
            muted = muted,
            modifier = Modifier.weight(1f),
        )
        StatDivider()
        StatCell(
            icon = Icons.Rounded.Speed,
            value = reliabilityPct?.let { "$it%" } ?: "—",
            label = stringResource(Res.string.vehicle_stats_reliability),
            muted = muted,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatDivider() {
    PapVerticalDivider(modifier = Modifier.padding(vertical = STAT_DIVIDER_V_PAD.dp))
}

@Composable
private fun StatCell(
    icon: ImageVector,
    value: String,
    label: String,
    muted: Boolean,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val accent = if (muted) cs.onSurfaceVariant else cs.primary
    val valueColor = if (muted) cs.onSurfaceVariant else cs.onSurface
    Column(
        modifier = modifier.padding(vertical = STAT_CELL_V_PAD.dp, horizontal = STAT_CELL_H_PAD.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(STAT_ICON_GAP.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(STAT_ICON_DP.dp),
            )
            Text(
                text = value,
                // Condensed numerals straight from the token (25sp per the design spec) — no
                // per-call size overrides. [HOME-VEH-REFINE-001]
                style = PaparcarType.current.statNumber,
                color = valueColor,
                maxLines = 1,
            )
        }
        Spacer(Modifier.size(STAT_LABEL_GAP.dp))
        Text(
            // Condensed, matching its number above — icon + number + label read as one data unit.
            text = label.uppercase(),
            style = PaparcarType.current.badge,
            color = cs.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Set-active row — full-width action, inactive vehicles only
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SetActiveRow(isLoading: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth(),
        color = cs.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(SET_ACTIVE_PAD.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SET_ACTIVE_GAP.dp, Alignment.CenterHorizontally),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(SET_ACTIVE_ICON_DP.dp),
                    strokeWidth = SET_ACTIVE_SPINNER_STROKE.dp,
                    color = cs.primary,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(SET_ACTIVE_ICON_DP.dp),
                )
            }
            Text(
                text = stringResource(Res.string.vehicle_set_active_action),
                // An action row is a button → cta (Inter), the app's button convention (PapButton);
                // not rowTitle (Outfit), which is for identity titles. [CARD-ONE-BADGE-001]
                style = PaparcarType.current.cta,
                color = cs.primary,
            )
        }
    }
}

private const val CARD_PADDING = 16
// Identity block paddings per the design spec (15 top / 13 bottom).
private const val IDENTITY_TOP_PAD = 15
private const val IDENTITY_BOTTOM_PAD = 13
// Compact edit box — kills the invisible 48dp dead zone that misaligned the card's right edge.
private const val EDIT_BOX_DP = 40
private const val EDIT_ICON_ALPHA = 0.7f
private const val STAT_CELL_V_PAD = 13
private const val STAT_CELL_H_PAD = 8
private const val STAT_DIVIDER_V_PAD = 13
private const val STAT_ICON_DP = 17
private const val STAT_ICON_GAP = 5
private const val STAT_LABEL_GAP = 5
private const val SET_ACTIVE_PAD = 13
private const val SET_ACTIVE_GAP = 9
private const val SET_ACTIVE_ICON_DP = 18
private const val SET_ACTIVE_SPINNER_STROKE = 2
