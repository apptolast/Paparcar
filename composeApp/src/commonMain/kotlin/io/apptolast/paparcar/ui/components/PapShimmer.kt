package io.apptolast.paparcar.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.CircleShape
import io.apptolast.paparcar.ui.theme.PapMotion

/**
 * The app's single loading-skeleton primitive: a placeholder box that breathes its alpha
 * (0.15 → 0.40 over [PapMotion.Breathe]) so every skeleton across the app reads as one family.
 *
 * Reuse this instead of hand-rolling an `infiniteTransition` per screen. Use it anywhere a value
 * is still resolving from Room/network and the real content would otherwise flash a wrong default
 * (e.g. a vehicle icon flashing the generic car before its carbody/size arrive).
 *
 * @param alphaScale dims the pulse for secondary/lower-emphasis placeholders (e.g. a subtitle bar).
 */
@Composable
fun PapShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    alphaScale: Float = 1f,
) {
    val transition = rememberInfiniteTransition(label = "pap_shimmer")
    val pulseAlpha by transition.animateFloat(
        initialValue = SHIMMER_ALPHA_MIN,
        targetValue = SHIMMER_ALPHA_MAX,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = PapMotion.Breathe, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pap_shimmer_pulse",
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = pulseAlpha * alphaScale)),
    )
}

private const val SHIMMER_ALPHA_MIN = 0.15f
private const val SHIMMER_ALPHA_MAX = 0.40f
