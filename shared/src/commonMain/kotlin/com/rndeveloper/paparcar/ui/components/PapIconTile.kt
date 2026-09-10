package com.rndeveloper.paparcar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
import com.rndeveloper.paparcar.ui.theme.PapShapes

/**
 * The reusable leading tile for list rows / cards — a rounded (or circular) tonal box with a centred
 * icon. One definition for the "icon in a coloured box" that was hand-rolled all over (Settings,
 * detection surface, carbody cards…). [UI-LIST-ITEM-001]
 *
 * ⛔ **Neutral by default, green only when you ask.** This used to default to
 * `primaryContainer` + `primary`, so every row that reached for the canonical tile came out green —
 * and a screen with six green tiles has no primary action, it has wallpaper. Green is the app's
 * scarcest ink: it belongs to what you can DO now and to the identity of a watched car. A tile that
 * merely labels its row is neither. Pass [container]/[tint] explicitly for the exceptions.
 * [UI-ONE-SKELETON-ONE-EMPHASIS-ONE-GREEN-001] [UI-COLOR-DOCTRINE-001]
 */
@Composable
fun PapIconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = PapIconTileSize,
    shape: Shape = PapShapes.row,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
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

/** The tile's default edge. Public because a caller that has to LINE UP with the tile (the first
 *  steps CTA sitting under its row) must read the size, not re-type it. */
val PapIconTileSize: Dp = 40.dp
private const val ICON_DP = 20
