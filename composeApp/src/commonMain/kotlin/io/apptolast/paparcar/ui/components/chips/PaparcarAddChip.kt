package io.apptolast.paparcar.ui.components.chips

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.ui.theme.PapBorders

private const val ADD_BORDER_ALPHA = 0.6f

/**
 * "Add" action chip — icon-only, always a circle that adapts to the icon size
 * + padding (no fixed-size mode). Primary-tinted border keeps it readable as
 * an action affordance vs the neutral outline used by [PaparcarFilterChip].
 *
 * Sizing model: the circle's diameter is driven by [iconSize] + [contentPad]
 * on each side, so callers control the visual weight just by changing
 * [iconSize]. Callers that need a specific size match other chips in a row
 * (e.g. the vehicle tab pill's 32dp height) should pass an [iconSize] /
 * [contentPad] pair whose sum equals that height.
 */
@Composable
fun PaparcarAddChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    iconSize: Dp = 16.dp,
    contentPad: Dp = 8.dp,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            width = PapBorders.thin,
            color = MaterialTheme.colorScheme.primary.copy(alpha = ADD_BORDER_ALPHA),
        ),
    ) {
        Box(
            modifier = Modifier.padding(contentPad),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
