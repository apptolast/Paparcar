package io.apptolast.paparcar.presentation.home.sections.header.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.SatelliteAlt
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_cd_map_type
import paparcar.composeapp.generated.resources.settings_map_type_hybrid
import paparcar.composeapp.generated.resources.settings_map_type_satellite
import paparcar.composeapp.generated.resources.settings_map_type_terrain

/**
 * Vertical stack of circular FABs for picking the active map style.
 *
 * Tapping the trigger (Layers icon) slides the column down into view;
 * tapping again or outside slides it back up. The exit animation completes
 * before the Popup is removed, so the collapse is always visible.
 *
 * Options (in order):
 *  1. Normal   — standard road map        (Map icon)
 *  2. Satellite — raw aerial imagery       (SatelliteAlt icon)
 *  3. Hybrid   — aerial + road labels      (Public/globe icon)
 */
@Composable
internal fun MapTypePicker(
    currentType: MapType,
    onTypeSelected: (MapType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    // Keep the Popup alive during the exit animation so shrinkVertically plays fully.
    var popupVisible by remember { mutableStateOf(false) }

    LaunchedEffect(expanded) {
        if (expanded) {
            popupVisible = true
            // popupVisible and expanded flip in the same frame → AnimatedVisibility would
            // start already-visible and skip the enter animation. We keep popupVisible
            // separate and let the inner LaunchedEffect(Unit) trigger entry on the next frame.
        } else {
            delay(EXIT_ANIM_DURATION_MS)
            popupVisible = false
        }
    }

    val triggerContentDescription = stringResource(Res.string.home_cd_map_type)
    val popupYOffsetPx = with(LocalDensity.current) {
        (FAB_SIZE + FAB_GAP).roundToPx()
    }

    Box(modifier = modifier) {
        MapCircleFab(
            icon = Icons.Rounded.Layers,
            onClick = { expanded = !expanded },
            contentDescription = triggerContentDescription,
            size = FAB_SIZE,
            iconSize = FAB_ICON_SIZE,
        )

        if (popupVisible) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(x = 0, y = popupYOffsetPx),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                // animVisible starts false so the Popup composes in the hidden state.
                // LaunchedEffect(Unit) fires on the next frame and flips it to true,
                // letting AnimatedVisibility play the full enter animation.
                var animVisible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { animVisible = true }

                AnimatedVisibility(
                    visible = animVisible && expanded,
                    enter = expandVertically(
                        expandFrom = Alignment.Top,
                        animationSpec = tween(ENTER_ANIM_DURATION_MS),
                    ) + fadeIn(animationSpec = tween(ENTER_ANIM_DURATION_MS)),
                    exit = shrinkVertically(
                        shrinkTowards = Alignment.Top,
                        animationSpec = tween(EXIT_ANIM_DURATION_MS.toInt()),
                    ) + fadeOut(animationSpec = tween(EXIT_ANIM_DURATION_MS.toInt())),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(FAB_GAP),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        MapTypeStackEntry(
                            icon = Icons.Rounded.Terrain,
                            contentDescription = stringResource(Res.string.settings_map_type_terrain),
                            selected = currentType == MapType.TERRAIN,
                            onClick = { onTypeSelected(MapType.TERRAIN) },
                        )
                        MapTypeStackEntry(
                            icon = Icons.Rounded.SatelliteAlt,
                            contentDescription = stringResource(Res.string.settings_map_type_satellite),
                            selected = currentType == MapType.SATELLITE,
                            onClick = { onTypeSelected(MapType.SATELLITE) },
                        )
                        MapTypeStackEntry(
                            icon = Icons.Rounded.Public,
                            contentDescription = stringResource(Res.string.settings_map_type_hybrid),
                            selected = currentType == MapType.HYBRID,
                            onClick = { onTypeSelected(MapType.HYBRID) },
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
private val SELECTION_RING_WIDTH = 1.5.dp
private const val ENTER_ANIM_DURATION_MS = 240
private const val EXIT_ANIM_DURATION_MS  = 200L
