package io.apptolast.paparcar.presentation.util

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.ui.components.GlassDefaults
import io.apptolast.paparcar.ui.components.GlassSurface

/**
 * Shadow elevation shared by every floating-over-map control (FABs, the add
 * chip, the zone chips) so they read as one family hovering above the map.
 * [MAP-GLASS-001]
 */
const val MAP_FLOATING_SHADOW_DP = 6

/**
 * Shared circular map FAB used by both HomeMapFabColumn and MapControlButtons.
 */
@Composable
fun MapCircleFab(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    iconTint: Color = Color.Unspecified,
    containerColor: Color = Color.Unspecified,
    size: Dp = 48.dp,
    iconSize: Dp = 20.dp,
    shadowElevation: Dp = MAP_FLOATING_SHADOW_DP.dp,
) {
    val resolvedTint = if (iconTint == Color.Unspecified)
        MaterialTheme.colorScheme.onSurface
    else
        iconTint

    val glassColors = if (containerColor == Color.Unspecified)
        GlassDefaults.colors()
    else
        GlassDefaults.colors(container = containerColor)

    GlassSurface(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = CircleShape,
        colors = glassColors,
        shadowElevation = shadowElevation,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = resolvedTint,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
