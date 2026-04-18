package io.apptolast.paparcar.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Surface

/**
 * Provided by HomeScreen when the user is actively dragging the map camera.
 * GlassSurface reads this to animate from solid → glass → solid.
 */
val LocalMapInteracting = compositionLocalOf { false }

/**
 * A Surface that is fully opaque and tonally elevated at rest, and
 * transitions to a translucent frosted-glass style when the map camera
 * is being moved ([LocalMapInteracting] == true).
 *
 * Idle  : solid [MaterialTheme.colorScheme.surfaceColorAtElevation] (tonal tint, no border)
 * Active: semi-transparent + subtle luminous border so the map shows through
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    colors: GlassColors = GlassDefaults.colors(),
    shadowElevation: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val isInteracting = LocalMapInteracting.current

    val containerAlpha by animateFloatAsState(
        targetValue = if (isInteracting) GlassDefaults.ALPHA_INTERACTING else GlassDefaults.ALPHA_IDLE,
        animationSpec = tween(durationMillis = GlassDefaults.ANIM_DURATION_MS),
        label = "glassContainerAlpha",
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (isInteracting) GlassDefaults.BORDER_ALPHA else 0f,
        animationSpec = tween(durationMillis = GlassDefaults.ANIM_DURATION_MS),
        label = "glassBorderAlpha",
    )

    val resolvedContainer = colors.container.copy(alpha = containerAlpha)
    val border = BorderStroke(colors.borderWidth, colors.border.copy(alpha = borderAlpha))

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = resolvedContainer,
            border = border,
            shadowElevation = shadowElevation,
            content = content,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = resolvedContainer,
            border = border,
            shadowElevation = shadowElevation,
            content = content,
        )
    }
}

@Immutable
data class GlassColors(
    val container: Color,
    val border: Color,
    val borderWidth: Dp,
)

object GlassDefaults {

    internal const val ALPHA_IDLE = 1.0f
    internal const val ALPHA_INTERACTING = 0.52f
    internal const val ANIM_DURATION_MS = 350
    internal const val BORDER_ALPHA = 0.18f
    internal val IDLE_ELEVATION = 3.dp
    private val BORDER_WIDTH = 0.5.dp

    @Composable
    fun colors(
        container: Color = MaterialTheme.colorScheme.surfaceColorAtElevation(IDLE_ELEVATION),
        border: Color = MaterialTheme.colorScheme.onSurface,
        borderWidth: Dp = BORDER_WIDTH,
    ): GlassColors = GlassColors(
        container = container,
        border = border,
        borderWidth = borderWidth,
    )
}
