package com.rndeveloper.paparcar.presentation.home.sections.header.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Public
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swmansion.kmpmaps.core.MapType
import com.rndeveloper.paparcar.presentation.util.MapCircleFab
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_cd_map_type

/**
 * Circular FAB that flips the map between its two useful styles.
 *
 *  - [MapType.TERRAIN] — reads as a flat street plan at city zoom: no relief,
 *    no buildings, just roads and names → Map icon.
 *  - [MapType.HYBRID]  — the real world from above (buildings, trees, shadows)
 *    with street labels on top → globe icon.
 *
 * There is no third option and no popup. The button shows the style you are
 * CURRENTLY on — it is a badge of the map's state, not a preview of the next
 * one — and tapping it flips to the other. Switching costs a single tap.
 *
 * Raw satellite (imagery without labels) was dropped — for finding a parking
 * spot it is strictly worse than hybrid. [UI-MAP-TYPE-TOGGLE-001]
 */
@Composable
internal fun MapTypeToggle(
    currentType: MapType,
    onTypeSelected: (MapType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onHybrid = currentType == MapType.HYBRID
    val contentDescription = stringResource(Res.string.home_cd_map_type)

    Crossfade(
        targetState = onHybrid,
        animationSpec = tween(ICON_SWAP_DURATION_MS),
        modifier = modifier,
    ) { animatedOnHybrid ->
        MapCircleFab(
            icon = if (animatedOnHybrid) Icons.Rounded.Public else Icons.Rounded.Map,
            onClick = { onTypeSelected(if (animatedOnHybrid) MapType.TERRAIN else MapType.HYBRID) },
            contentDescription = contentDescription,
            size = FAB_SIZE,
            iconSize = FAB_ICON_SIZE,
        )
    }
}

private val FAB_SIZE      = 56.dp
private val FAB_ICON_SIZE = 24.dp
private const val ICON_SWAP_DURATION_MS = 120
