package com.rndeveloper.paparcar.presentation.util

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
import com.rndeveloper.paparcar.ui.components.GlassDefaults
import com.rndeveloper.paparcar.ui.components.GlassSurface

/**
 * Shadow elevation shared by every floating-over-map control (FABs, the add
 * chip, the zone chips) so they read as one family hovering above the map.
 * [MAP-GLASS-001]
 */
const val MAP_FLOATING_SHADOW_DP = 6

/**
 * Shared circular map FAB. Its live consumers are Home's `HomeMapFabColumn` + `MapTypeToggle` and the
 * history detail map's `MapControlButtons`.
 *
 * ⚠️ This list is only worth writing if it is checked: it used to name `MapControlButtons` while that
 * component was dead code nobody called, so the KDoc asserted a second surface that did not render.
 * [UI-HISTORY-DETAIL-HAS-THE-MAP-CONTROLS-001]
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
