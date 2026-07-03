package io.apptolast.paparcar.presentation.home.sections.sheet.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.presentation.util.MAP_FLOATING_SHADOW_DP
import io.apptolast.paparcar.presentation.util.zoneIconFor
import io.apptolast.paparcar.ui.components.GlassSurface
import io.apptolast.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_zone_action_delete

/**
 * Habitual-zone chip — a **glass stadium pill** that floats over the map in the
 * header, sharing the glass language of the search bar / map-type picker and the
 * fully-rounded pill shape of the vehicle tabs. Opaque at rest, translucent while
 * the map camera is dragged (via [GlassSurface]).
 *
 * Tap selects the zone, long-press edits it, the trailing × deletes it.
 * [ZONE-CHIPS-GLASS-001]
 */
@Composable
internal fun ZoneChip(
    label: String,
    iconKey: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    GlassSurface(
        onClick = onClick,
        shape = RoundedCornerShape(ZONE_CHIP_RADIUS_DP.dp),
        // Same container colour, FAB shadow and (no resting) border as the map
        // FABs — every floating-over-map control shares one contract. [MAP-GLASS-001]
        shadowElevation = MAP_FLOATING_SHADOW_DP.dp,
        // detectTapGestures with only onLongPress doesn't consume normal taps, so
        // the GlassSurface onClick (select) still fires. Same pattern the chip used
        // before the glass restyle.
        modifier = modifier.pointerInput(onLongPress) {
            detectTapGestures(onLongPress = { onLongPress() })
        },
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = zoneIconFor(iconKey),
                contentDescription = null,
                tint = cs.primary,
                modifier = Modifier.size(ZONE_CHIP_ICON_DP.dp),
            )
            Text(
                text = label,
                style = PaparcarType.current.label,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
                maxLines = 1,
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onDelete)
                    .padding(2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(Res.string.home_zone_action_delete),
                    tint = cs.onSurface.copy(alpha = ZONE_CHIP_TRAILING_ALPHA),
                    modifier = Modifier.size(ZONE_CHIP_CLOSE_DP.dp),
                )
            }
        }
    }
}

private const val ZONE_CHIP_RADIUS_DP = 999
private const val ZONE_CHIP_ICON_DP = 18
private const val ZONE_CHIP_CLOSE_DP = 14
private const val ZONE_CHIP_TRAILING_ALPHA = 0.5f
