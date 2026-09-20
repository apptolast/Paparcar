package com.rndeveloper.paparcar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.ui.theme.PaparcarSpacing
import com.rndeveloper.paparcar.ui.theme.PaparcarType

private val BadgeIconSize       = 12.dp
private val BadgeHorizontalPad  = 8.dp
private val BadgeVerticalPad    = 4.dp

/** The box of a badge that rides inside somebody else's line. Public so the two or three call sites
 *  that need it share ONE definition of "dense" instead of each inventing its own numbers.
 *
 *  ⛔ La caja es lo ÚNICO que encoge aquí: el tamaño de letra es propiedad del rol
 *  (`PaparcarType.badge`, 12 sp) y no se toca desde un call site.
 *  [UI-TYPE-TWO-VOICES-ONE-ROW-001] */
val PapBadgeDensePadding: PaddingValues = PaddingValues(horizontal = 5.dp, vertical = 0.dp)

/**
 * Generic pill-shaped status badge.
 *
 * @param label Text displayed inside the badge.
 * @param containerColor Background fill of the badge.
 * @param contentColor Text and icon tint color.
 * @param icon Optional leading icon.
 * @param textStyle Label style — data-token callers (TTL, counts) pass a condensed
 *   [com.rndeveloper.paparcar.ui.theme.PaparcarType] role; prose callers keep the default.
 * @param contentPadding The badge's own box. Defaults to the standing size; a badge that rides
 *   INSIDE another line (the fuel standout, sharing the subtitle with the distance) passes
 *   [PapBadgeDensePadding] so it reads as a token in the line and not as a second row.
 * @param shape Defaults to the standing cut; a dense badge takes a pill so that, small, it still
 *   reads as a badge and not as a coloured rectangle.
 */
@Composable
fun PapBadge(
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    textStyle: TextStyle = PaparcarType.current.label,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = BadgeHorizontalPad,
        vertical = BadgeVerticalPad,
    ),
    shape: Shape = MaterialTheme.shapes.extraSmall,
) {
    Row(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PaparcarSpacing.xs),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(BadgeIconSize),
            )
        }
        Text(
            text = label,
            style = textStyle,
            color = contentColor,
        )
    }
}

// ── Pre-built semantic variants ───────────────────────────────────────────────

/** Generic "new" / positive badge — uses primary surface variant. */
@Composable
fun PapStatusBadge(label: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    PapBadge(
        label = label,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier,
        icon = icon,
    )
}
