package io.apptolast.paparcar.presentation.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.SatelliteAlt
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.swmansion.kmpmaps.core.MapType
import io.apptolast.paparcar.presentation.util.MapCircleFab
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_cd_map_type
import paparcar.composeapp.generated.resources.settings_map_type_normal
import paparcar.composeapp.generated.resources.settings_map_type_satellite
import paparcar.composeapp.generated.resources.settings_map_type_terrain

/**
 * Replaces the legacy [androidx.compose.material3.DropdownMenu] map-type picker
 * with a vertical stack of circular FABs that visually echo the trigger.
 *
 * The trigger is a [MapCircleFab] with the Layers icon — same diameter as the
 * picker entries so the stack reads as a coherent column of equally-weighted
 * affordances. Tapping the trigger expands the column below; tapping outside
 * dismisses (the [Popup] is focusable).
 *
 * Currently the entries use the existing Material outlined icons (Map /
 * SatelliteAlt / Terrain) as placeholders. The Design System's circular
 * thumbnails for each map style will land via [ICONS-001] once assets are
 * ready — only the icon swap is needed; the surrounding structure stays.
 */
@Composable
internal fun MapTypePicker(
    currentType: MapType,
    onTypeSelected: (MapType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val triggerContentDescription = stringResource(Res.string.home_cd_map_type)
    val popupYOffsetPx = with(LocalDensity.current) {
        (FAB_SIZE + FAB_GAP).roundToPx()
    }

    Box(modifier = modifier) {
        MapCircleFab(
            icon = Icons.Outlined.Layers,
            onClick = { expanded = !expanded },
            contentDescription = triggerContentDescription,
            size = FAB_SIZE,
            iconSize = FAB_ICON_SIZE,
        )

        if (expanded) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(x = 0, y = popupYOffsetPx),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                // AnimatedVisibility around the column gives an expandVertically + fade
                // entry. The column is always laid out inside the popup; the popup itself
                // appears/disappears with the expanded flag, but the animation happens
                // within it so the user perceives the stack "growing out of" the trigger.
                AnimatedVisibility(
                    visible = true,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(FAB_GAP),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Selections do NOT auto-close the popup so the user can A/B
                        // between styles without having to reopen the picker each time.
                        // The popup dismisses on any outside tap (focusable Popup +
                        // onDismissRequest above).
                        MapTypeStackEntry(
                            icon = Icons.Outlined.Map,
                            contentDescription = stringResource(Res.string.settings_map_type_normal),
                            selected = currentType == MapType.NORMAL,
                            onClick = { onTypeSelected(MapType.NORMAL) },
                        )
                        MapTypeStackEntry(
                            icon = Icons.Outlined.SatelliteAlt,
                            contentDescription = stringResource(Res.string.settings_map_type_satellite),
                            selected = currentType == MapType.SATELLITE,
                            onClick = { onTypeSelected(MapType.SATELLITE) },
                        )
                        MapTypeStackEntry(
                            icon = Icons.Outlined.Terrain,
                            contentDescription = stringResource(Res.string.settings_map_type_terrain),
                            selected = currentType == MapType.TERRAIN,
                            onClick = { onTypeSelected(MapType.TERRAIN) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MapTypeStackEntry(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // The selection ring sits OUTSIDE the FAB so the FAB's own glass surface is
    // not visually pierced. The outer Box is FAB_SIZE + SELECTION_RING_GAP * 2
    // and renders the primary-coloured ring only when selected; the inner
    // MapCircleFab keeps its standard 56dp diameter.
    Box(
        modifier = Modifier
            .size(FAB_SIZE + SELECTION_RING_GAP * 2)
            .then(
                if (selected) Modifier.border(
                    width = SELECTION_RING_WIDTH,
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                ) else Modifier,
            )
            .padding(SELECTION_RING_GAP),
        contentAlignment = Alignment.Center,
    ) {
        MapCircleFab(
            icon = icon,
            onClick = onClick,
            contentDescription = contentDescription,
            size = FAB_SIZE,
            iconSize = FAB_ICON_SIZE,
        )
    }
}

private val FAB_SIZE             = 56.dp
private val FAB_ICON_SIZE        = 24.dp
private val FAB_GAP              = 8.dp
private val SELECTION_RING_GAP   = 3.dp
private val SELECTION_RING_WIDTH = 2.dp
