@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.presentation.util.SpotReliabilityUiState
import io.apptolast.paparcar.presentation.util.distanceString
import io.apptolast.paparcar.presentation.util.driveTimeString
import kotlinx.coroutines.delay
import kotlin.time.Clock
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_cd_spot
import paparcar.composeapp.generated.resources.home_spot_freshness_hours
import paparcar.composeapp.generated.resources.home_spot_freshness_minutes
import paparcar.composeapp.generated.resources.home_spot_freshness_under_min
import paparcar.composeapp.generated.resources.spot_item_type_auto
import paparcar.composeapp.generated.resources.spot_item_type_manual

// ── Dimensions ────────────────────────────────────────────────────────────────
private val ITEM_ICON_SIZE   = 42.dp
private val ITEM_ICON_RADIUS = 12.dp

// ── Freshness timer ───────────────────────────────────────────────────────────
private const val FRESHNESS_TICK_MS = 60_000L

/**
 * Compact community parking spot item for list views.
 *
 * Layout (two text lines inside a single row, max ~72 dp tall):
 * ```
 * ┌──────────────────────────────────────────────────────┐
 * │ [P]  ⛽ Repsol · Av. Castellana 110        < 1 min  │
 * │      203 m · 2 min drive · Auto                      │
 * └──────────────────────────────────────────────────────┘
 * ```
 *
 * Design rules:
 * - No inline action buttons. Navigation is handled by the bottom nav bar shown
 *   when an item is selected; community signals are out of scope for the list.
 * - The entire row is clickable via [onClick].
 * - [SpotCardData] is reused as the input model for compatibility with existing
 *   mapping code in ViewModels.
 *
 * @param data       Pre-computed presentation model (same as [SpotCard]).
 * @param onClick    Called when the user taps anywhere on the row.
 * @param isSelected Shows a subtle primary tint to match the selected marker on map.
 */
@Composable
fun ParkingSpotItem(
    data: SpotCardData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
) {
    val cd = stringResource(Res.string.home_cd_spot, data.displayLocation)
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Button
                contentDescription = cd
            },
        color = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
        else
            MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── "P" icon ─────────────────────────────────────────────────────
            SpotItemIcon(isSelected = isSelected)

            // ── Text block ────────────────────────────────────────────────────
            Column(modifier = Modifier.weight(1f)) {
                // Line 1 — location (where)
                Text(
                    text = data.displayLocation,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Line 2 — distance · drive time · detection type
                if (data.distanceMeters != null) {
                    val typeLabel = data.reliability.shortLabel()
                    Text(
                        text = buildString {
                            append(distanceString(data.distanceMeters))
                            append("  ·  ")
                            append(driveTimeString(data.distanceMeters))
                            append("  ·  ")
                            append(typeLabel)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // ── Right: live TTL or freshness chip ─────────────────────────────
            if (data.expiresAt > 0L) {
                TTLIndicator(expiresAtMs = data.expiresAt)
            } else {
                SpotItemFreshnessChip(reportedAtMs = data.reportedAtMs)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SpotItemIcon(isSelected: Boolean) {
    Box(
        modifier = Modifier
            .size(ITEM_ICON_SIZE)
            .clip(RoundedCornerShape(ITEM_ICON_RADIUS))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "P",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Live freshness chip that recomputes itself every 60 s.
 * Shows "< 1 min", "N min", or "N h" depending on age.
 */
@Composable
private fun SpotItemFreshnessChip(reportedAtMs: Long) {
    var nowMs by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(reportedAtMs) {
        while (true) {
            delay(FRESHNESS_TICK_MS)
            nowMs = Clock.System.now().toEpochMilliseconds()
        }
    }
    val ageMinutes = (nowMs - reportedAtMs) / MS_PER_MINUTE

    val label = when {
        ageMinutes < 1L  -> stringResource(Res.string.home_spot_freshness_under_min)
        ageMinutes < 60L -> stringResource(Res.string.home_spot_freshness_minutes, ageMinutes.toInt())
        else             -> stringResource(Res.string.home_spot_freshness_hours, (ageMinutes / 60L).toInt())
    }
    val containerColor = when {
        ageMinutes < 5L  -> MaterialTheme.colorScheme.primaryContainer
        ageMinutes < 15L -> MaterialTheme.colorScheme.secondaryContainer
        else             -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        ageMinutes < 5L  -> MaterialTheme.colorScheme.primary
        ageMinutes < 15L -> MaterialTheme.colorScheme.secondary
        else             -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    }
    PapBadge(label = label, containerColor = containerColor, contentColor = contentColor)
}

// ─────────────────────────────────────────────────────────────────────────────
// SpotReliabilityUiState → short label
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One-word detection-type label shown inline in the subtitle row.
 * Returns the localized "Auto" for all auto-detected reliability levels,
 * and "Manual" for user-reported spots.
 */
@Composable
internal fun SpotReliabilityUiState.shortLabel(): String = when (this) {
    SpotReliabilityUiState.MANUAL -> stringResource(Res.string.spot_item_type_manual)
    else                          -> stringResource(Res.string.spot_item_type_auto)
}
