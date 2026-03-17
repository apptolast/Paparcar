package io.apptolast.paparcar.presentation.util

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared circular map FAB used by both HomeMapFabColumn and MapControlButtons.
 */
@Composable
fun MapCircleFab(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    iconTint: Color = Color.Unspecified,
    containerColor: Color = Color.Unspecified,
    size: Dp = 44.dp,
    iconSize: Dp = 20.dp,
    shadowElevation: Dp = 6.dp,
) {
    val resolvedContainer = if (containerColor == Color.Unspecified)
        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    else
        containerColor
    val resolvedTint = if (iconTint == Color.Unspecified)
        MaterialTheme.colorScheme.onSurface
    else
        iconTint

    Surface(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = CircleShape,
        color = resolvedContainer,
        shadowElevation = shadowElevation,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = resolvedTint,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
