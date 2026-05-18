package io.apptolast.paparcar.presentation.home.sections.sheet.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.Zone
import io.apptolast.paparcar.ui.theme.PapShapes
import io.apptolast.paparcar.presentation.util.zoneIconFor
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_zones_empty_pill
import paparcar.composeapp.generated.resources.home_zones_empty_subtitle
import paparcar.composeapp.generated.resources.home_zones_empty_title

/**
 * Habitual-zone chips — navigation shortcuts saved by the user.
 *
 * v1 redesign:
 *  - Pill chips with subtle outlineVariant border for cleaner dark-mode contrast.
 *  - Trailing "+" chip uses dashed-style primary border (vs solid) so it reads as
 *    an "add" affordance instead of just another zone. [HOME-ZONES-001]
 */
@Composable
internal fun HomeZoneChips(
    zones: List<Zone>,
    onSelectZone: (String) -> Unit,
    onAddZone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    ) {
        items(zones, key = { it.id }) { zone ->
            ZoneChip(
                label = zone.name,
                iconKey = zone.iconKey,
                onClick = { onSelectZone(zone.id) },
            )
        }
        item("add_zone") {
            AddZoneChip(onClick = onAddZone)
        }
    }
}

@Composable
private fun ZoneChip(
    label: String,
    iconKey: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(CHIP_CORNER_DP.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = CHIP_BORDER_ALPHA),
        ),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = zoneIconFor(iconKey),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(CHIP_ICON_DP.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * "+" trailing chip — mirrors [ZoneChip]'s Surface molde exactly so both rows
 * align to the same height in the LazyRow. Icon-only content with the same
 * 12/8 padding as the zone chips.
 */
@Composable
private fun AddZoneChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(CHIP_CORNER_DP.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.5.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = ADD_CHIP_BORDER_ALPHA),
        ),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(CHIP_ICON_DP.dp),
            )
        }
    }
}

private const val CHIP_CORNER_DP = 18
private const val CHIP_ICON_DP = 18
private const val CHIP_BORDER_ALPHA = 0.6f
private const val ADD_CHIP_BORDER_ALPHA = 0.5f

// ─────────────────────────────────────────────────────────────────────────────
// Empty zones card — same molde as HomeParkingEmptyCard for visual coherence.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun HomeZonesEmptyCard(
    onAddZone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onAddZone,
        modifier = modifier.fillMaxWidth(),
        shape = PapShapes.card,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = EMPTY_CARD_BG_ALPHA),
        border = BorderStroke(
            width = 1.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = EMPTY_CARD_BORDER_ALPHA),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(ICON_BOX_DP.dp)
                    .clip(RoundedCornerShape(ICON_BOX_CORNER_DP.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Bookmark,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.home_zones_empty_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(Res.string.home_zones_empty_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = EMPTY_CARD_SUBTITLE_ALPHA),
                )
            }
            Surface(
                shape = RoundedCornerShape(PILL_RADIUS_DP.dp),
                color = MaterialTheme.colorScheme.primary,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        stringResource(Res.string.home_zones_empty_pill),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

private const val ICON_BOX_DP = 44
private const val ICON_BOX_CORNER_DP = 14
private const val PILL_RADIUS_DP = 999
private const val EMPTY_CARD_BG_ALPHA = 0.4f
private const val EMPTY_CARD_BORDER_ALPHA = 0.4f
private const val EMPTY_CARD_SUBTITLE_ALPHA = 0.55f
