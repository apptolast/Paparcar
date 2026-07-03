package io.apptolast.paparcar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.ui.theme.PaparcarSpacing
import io.apptolast.paparcar.ui.theme.PaparcarType

private val BadgeIconSize       = 12.dp
private val BadgeHorizontalPad  = 8.dp
private val BadgeVerticalPad    = 4.dp

/**
 * Generic pill-shaped status badge.
 *
 * @param label Text displayed inside the badge.
 * @param containerColor Background fill of the badge.
 * @param contentColor Text and icon tint color.
 * @param icon Optional leading icon.
 * @param textStyle Label style — data-token callers (TTL, counts) pass a condensed
 *   [io.apptolast.paparcar.ui.theme.PaparcarType] role; prose callers keep the default.
 */
@Composable
fun PapBadge(
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    textStyle: TextStyle = PaparcarType.current.label,
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(containerColor)
            .padding(horizontal = BadgeHorizontalPad, vertical = BadgeVerticalPad),
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
