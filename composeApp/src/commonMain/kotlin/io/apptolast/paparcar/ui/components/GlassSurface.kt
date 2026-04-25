package io.apptolast.paparcar.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
 * Idle  : solid [MaterialTheme.colorScheme.surfaceContainer] (shared with NavigationBar)
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

    // Asymmetric easing avoids flicker during a fling: fade-in is quick so the
    // glass appears immediately when the user starts dragging, but fade-out is
    // noticeably slower to ride out any residual onCameraMove ticks emitted
    // while the camera settles from the fling. If both directions used the
    // same duration, a single late event could yo-yo the alpha back to
    // "interacting" mid-fade — visible as a flicker.
    val containerAlpha by animateFloatAsState(
        targetValue = if (isInteracting) GlassDefaults.ALPHA_INTERACTING else GlassDefaults.ALPHA_IDLE,
        animationSpec = tween(
            durationMillis = if (isInteracting) GlassDefaults.FADE_IN_MS else GlassDefaults.FADE_OUT_MS,
            easing = FastOutSlowInEasing,
        ),
        label = "glassContainerAlpha",
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (isInteracting) GlassDefaults.BORDER_ALPHA else 0f,
        animationSpec = tween(
            durationMillis = if (isInteracting) GlassDefaults.FADE_IN_MS else GlassDefaults.FADE_OUT_MS,
            easing = FastOutSlowInEasing,
        ),
        label = "glassBorderAlpha",
    )
    // Drop the elevation when going translucent: a shadow on a semi-transparent
    // surface paints as an inner darker disc/rectangle (most visible on circular
    // FABs), since there's no opaque shape to absorb the shadow. Fading the
    // elevation in/out alongside the alpha keeps the glass effect clean.
    val shadowFactor by animateFloatAsState(
        targetValue = if (isInteracting) 0f else 1f,
        animationSpec = tween(
            durationMillis = if (isInteracting) GlassDefaults.FADE_IN_MS else GlassDefaults.FADE_OUT_MS,
            easing = FastOutSlowInEasing,
        ),
        label = "glassShadowFactor",
    )

    val resolvedContainer = colors.container.copy(alpha = containerAlpha)
    val border = BorderStroke(colors.borderWidth, colors.border.copy(alpha = borderAlpha))
    val resolvedShadow = shadowElevation * shadowFactor

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = resolvedContainer,
            border = border,
            shadowElevation = resolvedShadow,
            content = content,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = resolvedContainer,
            border = border,
            shadowElevation = resolvedShadow,
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
    internal const val FADE_IN_MS = 160
    internal const val FADE_OUT_MS = 320
    internal const val BORDER_ALPHA = 0.18f
    private val BORDER_WIDTH = 0.5.dp

    @Composable
    fun colors(
        // surfaceContainer is the same token NavigationBar uses by default, so
        // the sheet and the nav bar read as one cohesive bottom surface.
        container: Color = MaterialTheme.colorScheme.surfaceContainer,
        border: Color = MaterialTheme.colorScheme.onSurface,
        borderWidth: Dp = BORDER_WIDTH,
    ): GlassColors = GlassColors(
        container = container,
        border = border,
        borderWidth = borderWidth,
    )
}
