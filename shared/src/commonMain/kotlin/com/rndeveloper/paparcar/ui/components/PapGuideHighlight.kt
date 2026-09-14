package com.rndeveloper.paparcar.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * A pulsing brand-green outline that says "this is the control the tutorial is talking about".
 * [FIRST-STEPS-SHOW-WHERE-NOT-OPEN-001]
 *
 * ⛔ Draw-only: no pointer input, no overlay, no scrim. The highlighted control keeps working under
 * the finger — a coach mark that eats the gesture it teaches is the bug
 * [UI-A-COACH-MARK-MUST-NOT-EAT-THE-GESTURE-IT-TEACHES-001] already fixed once.
 */
@Composable
fun Modifier.papGuideHighlight(active: Boolean, shape: Shape): Modifier {
    // The transition runs unconditionally so toggling [active] never changes the composition's shape.
    val pulse by rememberInfiniteTransition(label = "guide_highlight").animateFloat(
        initialValue = GUIDE_MIN_ALPHA,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(GUIDE_PULSE_MS), RepeatMode.Reverse),
        label = "guide_highlight_alpha",
    )
    if (!active) return this
    return this.border(GUIDE_BORDER_DP.dp, MaterialTheme.colorScheme.primary.copy(alpha = pulse), shape)
}

private const val GUIDE_PULSE_MS = 700
private const val GUIDE_MIN_ALPHA = 0.25f
private const val GUIDE_BORDER_DP = 2
