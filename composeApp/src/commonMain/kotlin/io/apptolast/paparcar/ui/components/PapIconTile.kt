package io.apptolast.paparcar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The reusable leading tile for list rows / cards — a rounded (or circular) tonal box with a centred
 * icon. One definition for the "icon in a coloured box" that was hand-rolled all over (Settings,
 * detection surface, carbody cards…). Feed it a different [container]/[tint]/[shape] for accent tones
 * or a circular disc. [UI-LIST-ITEM-001]
 */
@Composable
fun PapIconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = TILE_DP.dp,
    shape: Shape = RoundedCornerShape(TILE_CORNER_DP.dp),
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    tint: Color = MaterialTheme.colorScheme.primary,
    iconSize: Dp = ICON_DP.dp,
) {
    Box(
        modifier = modifier.size(size).clip(shape).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

private const val TILE_DP = 40
private const val TILE_CORNER_DP = 12
private const val ICON_DP = 20
