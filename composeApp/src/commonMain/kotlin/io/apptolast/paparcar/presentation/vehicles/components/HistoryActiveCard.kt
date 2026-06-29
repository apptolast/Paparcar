package io.apptolast.paparcar.presentation.vehicles.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.ui.theme.PapGreen
import io.apptolast.paparcar.ui.theme.PapMotion

private const val PULSE_EXPAND_DURATION = PapMotion.PulseExpand
private const val PULSE_COLLAPSE_DURATION = PapMotion.PulseCollapse

@Composable
internal fun PulsingDot(color: Color = PapGreen, modifier: Modifier = Modifier) {
    val ring = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            ring.animateTo(1f, tween(PULSE_EXPAND_DURATION, easing = PapMotion.EaseInOut))
            ring.animateTo(0f, tween(PULSE_COLLAPSE_DURATION, easing = PapMotion.EaseInOut))
        }
    }
    Box(modifier = modifier.size(14.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size((8 + 6 * ring.value).dp)
                .background(color.copy(alpha = (1f - ring.value) * 0.35f), CircleShape)
        )
        Box(Modifier.size(8.dp).background(color, CircleShape))
    }
}
