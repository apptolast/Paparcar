package io.apptolast.paparcar.ui.components.chips

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.ui.theme.PapBorders
import io.apptolast.paparcar.ui.theme.PapShapes
import io.apptolast.paparcar.ui.theme.PaparcarType

private val CHIP_SHAPE = PapShapes.chip
private const val DISABLED_BG_ALPHA = 0.5f
private const val DISABLED_BORDER_ALPHA = 0.3f
private const val DISABLED_FG_ALPHA = 0.38f
private val CHIP_ICON_SIZE = 18.dp
private val TRAILING_ICON_SIZE = 12.dp
private const val TRAILING_ICON_ALPHA = 0.5f

/**
 * Paparcar's canonical chip — the single base used for filter chips, zone
 * chips, and any other label-plus-state pill across the app.
 *
 * Selected state uses the primary container fill + `primary` border and text —
 * "on" reads through tinted CONTAINER + accents, never a solid primary fill
 * (solid green is reserved for the primary CTA). Unselected uses
 * [PapBorders.DEFAULT_OUTLINE_ALPHA] — the same subtle outline used by
 * ordinary cards — so chips feel like part of the card family. [UI-SHEET-001]
 *
 * Optional [trailingIcon] + [onTrailingClick] add a tap-handled icon slot
 * (e.g. `×` for delete on a saved zone chip) without forking the composable.
 */
@Composable
fun PaparcarFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    trailingContentDescription: String? = null,
    onTrailingClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    val bg = when {
        !enabled -> cs.surfaceContainerHigh.copy(alpha = DISABLED_BG_ALPHA)
        selected -> cs.primaryContainer
        else -> cs.surfaceContainerHigh
    }
    val borderColor = when {
        !enabled -> cs.outlineVariant.copy(alpha = DISABLED_BORDER_ALPHA)
        selected -> cs.primary
        else -> cs.outline.copy(alpha = PapBorders.DEFAULT_OUTLINE_ALPHA)
    }
    val contentColor = when {
        !enabled -> cs.onSurface.copy(alpha = DISABLED_FG_ALPHA)
        selected -> cs.primary
        else -> cs.onSurface
    }

    val hasTrailing = trailingIcon != null && onTrailingClick != null
    val endPadding = if (hasTrailing) 6.dp else 12.dp

    Surface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = CHIP_SHAPE,
        color = bg,
        border = BorderStroke(width = PapBorders.thin, color = borderColor),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = endPadding, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    // Leading icons always render in primary (green) so chips read
                    // as branded accents rather than monochrome labels. Disabled
                    // chips fade with the rest of the foreground.
                    tint = if (!enabled) contentColor else cs.primary,
                    modifier = Modifier.size(CHIP_ICON_SIZE),
                )
            }
            Text(
                text = label,
                style = PaparcarType.current.label,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
            )
            if (trailingIcon != null && onTrailingClick != null) {
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .clickable(onClick = onTrailingClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = trailingIcon,
                        contentDescription = trailingContentDescription,
                        tint = contentColor.copy(alpha = TRAILING_ICON_ALPHA),
                        modifier = Modifier.size(TRAILING_ICON_SIZE),
                    )
                }
            }
        }
    }
}

