package io.apptolast.paparcar.ui.components.chips

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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

/**
 * "Add" action chip — icon-only pill. Matches the visual contract of
 * VehicleTabPill (same border style, same pill shape) with a compact
 * icon-only footprint.
 */
@Composable
fun PaparcarAddChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    iconSize: Dp = 16.dp,
    horizontalPad: Dp = 12.dp,
    verticalPad: Dp = 10.dp,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            width = PapBorders.thin,
            color = MaterialTheme.colorScheme.outline.copy(alpha = PapBorders.DEFAULT_OUTLINE_ALPHA),
        ),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = horizontalPad, vertical = verticalPad),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = ADD_ICON_ALPHA),
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

private const val ADD_ICON_ALPHA = 0.65f
