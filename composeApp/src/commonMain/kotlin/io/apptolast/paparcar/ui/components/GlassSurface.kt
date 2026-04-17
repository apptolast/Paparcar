package io.apptolast.paparcar.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Provided by HomeScreen when the user is actively dragging the map camera.
 * GlassSurface reads this to animate from solid → glass → solid.
 */
val LocalMapInteracting = compositionLocalOf { false }

/**
 * A Surface with a frosted-glass appearance: semi-transparent tinted background
 * with a subtle luminous border that lets the map show through.
 *
 * When [LocalMapInteracting] is true the surface animates to a more transparent
 * state so the map is clearly visible beneath UI overlays. When false it
 * transitions back to a nearly-opaque solid state.
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
        label = "glassAlpha",
    )

    val resolvedContainer = colors.container.copy(alpha = containerAlpha)
    val border = BorderStroke(colors.borderWidth, colors.border)

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

    internal const val ALPHA_IDLE = 0.92f
    internal const val ALPHA_INTERACTING = 0.48f
    internal const val ANIM_DURATION_MS = 350
    private const val BORDER_ALPHA = 0.12f
    private val BORDER_WIDTH = 0.5.dp

    @Composable
    fun colors(
        container: Color = MaterialTheme.colorScheme.surface,
        border: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = BORDER_ALPHA),
        borderWidth: Dp = BORDER_WIDTH,
    ): GlassColors = GlassColors(
        container = container,
        border = border,
        borderWidth = borderWidth,
    )
}
