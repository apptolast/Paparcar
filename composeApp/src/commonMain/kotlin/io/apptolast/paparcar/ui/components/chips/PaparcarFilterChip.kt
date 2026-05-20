package io.apptolast.paparcar.ui.components.chips

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val CHIP_SHAPE = RoundedCornerShape(18.dp)
private const val INACTIVE_BORDER_ALPHA = 0.6f

@Composable
fun PaparcarFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    val bg = when {
        !enabled -> cs.surfaceContainerHigh.copy(alpha = 0.5f)
        selected -> cs.primaryContainer
        else -> cs.surfaceContainerHigh
    }
    val borderColor = when {
        !enabled -> cs.outlineVariant.copy(alpha = 0.3f)
        selected -> cs.primary
        else -> cs.outlineVariant.copy(alpha = INACTIVE_BORDER_ALPHA)
    }
    val contentColor = when {
        !enabled -> cs.onSurface.copy(alpha = 0.38f)
        selected -> cs.onPrimaryContainer
        else -> cs.onSurface
    }

    Surface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = CHIP_SHAPE,
        color = bg,
        border = BorderStroke(width = 1.dp, color = borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (selected) cs.primary else contentColor,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
            )
        }
    }
}
