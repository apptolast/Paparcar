package io.apptolast.paparcar.presentation.home.sections.sheet.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.ui.components.chips.PaparcarAddChip
import io.apptolast.paparcar.ui.components.chips.PaparcarFilterChip
import io.apptolast.paparcar.domain.model.Zone
import io.apptolast.paparcar.ui.theme.PapBorders
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
    onDeleteZone: (String) -> Unit,
    onEditZone: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    ) {
        items(zones, key = { it.id }) { zone ->
            val onChipClick = remember(zone.id, onSelectZone) { { onSelectZone(zone.id) } }
            val onChipDelete = remember(zone.id, onDeleteZone) { { onDeleteZone(zone.id) } }
            val onChipLongPress = remember(zone.id, onEditZone) { { onEditZone(zone.id) } }
            ZoneChip(
                label = zone.name,
                iconKey = zone.iconKey,
                onClick = onChipClick,
                onDelete = onChipDelete,
                onLongPress = onChipLongPress,
            )
        }
        item("add_zone") {
            PaparcarAddChip(
                onClick = onAddZone,
                iconSize = CHIP_ICON_DP.dp,
                horizontalPad = 8.dp,
                verticalPad = 8.dp,
            )
        }
    }
}

@Composable
internal fun ZoneChip(
    label: String,
    iconKey: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PaparcarFilterChip(
        label = label,
        selected = false,
        onClick = onClick,
        leadingIcon = zoneIconFor(iconKey),
        trailingIcon = Icons.Outlined.Close,
        onTrailingClick = onDelete,
        modifier = modifier.pointerInput(onLongPress) {
            detectTapGestures(onLongPress = { onLongPress() })
        },
    )
}

private const val CHIP_ICON_DP = 18

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
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            width = PapBorders.thin,
            color = MaterialTheme.colorScheme.outline.copy(alpha = PapBorders.DEFAULT_OUTLINE_ALPHA),
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
                    .background(MaterialTheme.colorScheme.surfaceContainer),
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
private const val EMPTY_CARD_SUBTITLE_ALPHA = 0.55f
