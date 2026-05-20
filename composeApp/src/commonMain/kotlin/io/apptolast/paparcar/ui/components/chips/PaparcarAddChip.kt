package io.apptolast.paparcar.ui.components.chips

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val PILL_SHAPE = RoundedCornerShape(999.dp)
private const val BORDER_ALPHA = 0.5f

/**
 * "Add" action chip — primary-bordered, icon-only.
 *
 * Supports two sizing modes via [modifier]:
 * - Fixed size (e.g. vehicle tab row): pass `Modifier.size(32.dp)` + `contentPadding = PaddingValues(0.dp)`
 * - Padding-based (e.g. zone chips row): omit size, use default contentPadding
 */
@Composable
fun PaparcarAddChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    shape: Shape = PILL_SHAPE,
    iconSize: Dp = 16.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(
            width = 1.5.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = BORDER_ALPHA),
        ),
    ) {
        Box(
            modifier = Modifier.padding(contentPadding),
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
