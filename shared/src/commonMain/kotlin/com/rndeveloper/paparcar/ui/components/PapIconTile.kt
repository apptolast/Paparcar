package com.rndeveloper.paparcar.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    iconSize: Dp = size * GLYPH_RATIO,
    border: BorderStroke? = null,
) {
    val outline = if (border == null) Modifier else Modifier.border(border, shape)
    Box(
        modifier = modifier.size(size).then(outline).clip(shape).background(container),
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

/**
 * The glyph takes HALF the tile's edge, and it is a RATIO because a fixed default is a trap.
 * [UI-A-TILE-THAT-GROWS-MUST-GROW-ITS-GLYPH-001]
 *
 * This used to be a flat `20.dp`, which is the right number at the default 40 dp edge and silently
 * wrong at every other: a caller that scaled the box got the same small glyph in a bigger disc.
 * The explainer hero was exactly that — a 64 dp disc around a 20 dp glyph, 31% where the house
 * proportion is 50%, and it read as a pea in a bowl on device (Oppo, 25-09).
 *
 * ⛔ At the default size this computes to the same 20 dp it always was, so nothing that relies on
 * the default moves. The two callers that size the box themselves (`HomeSpotRows`,
 * `CarbodyInfoCard`) pass their own `iconSize` and are untouched — they were never the bug.
 */
private const val GLYPH_RATIO = 0.5f
