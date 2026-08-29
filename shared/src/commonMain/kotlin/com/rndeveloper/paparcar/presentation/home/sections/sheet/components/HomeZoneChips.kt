package com.rndeveloper.paparcar.presentation.home.sections.sheet.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.presentation.util.MAP_FLOATING_SHADOW_DP
import com.rndeveloper.paparcar.presentation.util.zoneIconFor
import com.rndeveloper.paparcar.ui.components.GlassSurface
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_zone_action_edit
import com.rndeveloper.paparcar.ui.theme.PapAlpha

/**
 * Habitual-zone chip — a **glass stadium pill** that floats over the map in the
 * header, sharing the glass language of the search bar / map-type picker and the
 * fully-rounded pill shape of the vehicle tabs. Opaque at rest, translucent while
 * the map camera is dragged (via [GlassSurface]).
 *
 * Two gestures, neither destructive: **tap flies the camera to the zone**, the
 * trailing **pencil opens its modal**, where the zone is managed (icon, radius,
 * privacy, name — and deleting it, behind a confirm). Deleting was never worth a
 * 14dp bullseye floating over the map, and the edit long-press it replaces was
 * undiscoverable. [ZONE-CHIPS-GLASS-001] [UI-ZONE-MANAGE-001]
 */
@Composable
internal fun ZoneChip(
    label: String,
    iconKey: String,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    GlassSurface(
        onClick = onClick,
        shape = RoundedCornerShape(ZONE_CHIP_RADIUS_DP.dp),
        // Same container colour, FAB shadow and (no resting) border as the map
        // FABs — every floating-over-map control shares one contract. [MAP-GLASS-001]
        shadowElevation = MAP_FLOATING_SHADOW_DP.dp,
        modifier = modifier,
    ) {
        Row(
            // Tighter end inset than start: the pencil's 28dp touch box already carries
            // its own optical margin around the 16dp glyph.
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
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
                color = cs.onSurface,
                maxLines = 1,
            )
            // The pencil is now the way INTO zone management, so it gets a real
            // touch target instead of the old ×'s 14dp sliver.
            Box(
                modifier = Modifier
                    .size(ZONE_CHIP_EDIT_TAP_DP.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onEdit),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = stringResource(Res.string.home_zone_action_edit),
                    tint = cs.onSurface.copy(alpha = ZONE_CHIP_TRAILING_ALPHA),
                    modifier = Modifier.size(ZONE_CHIP_EDIT_DP.dp),
                )
            }
        }
    }
}

private const val ZONE_CHIP_RADIUS_DP = 999
private const val ZONE_CHIP_ICON_DP = 18
private const val ZONE_CHIP_EDIT_DP = 16
private const val ZONE_CHIP_EDIT_TAP_DP = 28
private val ZONE_CHIP_TRAILING_ALPHA = PapAlpha.muted
