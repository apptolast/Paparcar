package com.rndeveloper.paparcar.presentation.vehicles.components

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
import com.rndeveloper.paparcar.ui.theme.PapMotion

private const val PULSE_EXPAND_DURATION = PapMotion.PulseExpand
private const val PULSE_COLLAPSE_DURATION = PapMotion.PulseCollapse

/**
 * The pulsing dot of a live session. [color] is REQUIRED and must come from
 * `vehicleIdentityColor` — this dot is the one element the doctrine puts the vehicle's identity on
 * (`COLOR-SYSTEM.md` §3), so it can never fall back to the brand green.
 * [UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001]
 */
private val BOX_DP = 14.dp
private val CORE_DP = 8.dp
private val RING_EXPANSION_DP = 6.dp

@Composable
internal fun PulsingDot(color: Color, modifier: Modifier = Modifier) {
    val ring = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            ring.animateTo(1f, tween(PULSE_EXPAND_DURATION, easing = PapMotion.EaseInOut))
            ring.animateTo(0f, tween(PULSE_COLLAPSE_DURATION, easing = PapMotion.EaseInOut))
        }
    }
    // Geometria del propio dibujo, acoplada: la caja (BOX_DP) es justo el maximo que alcanza el
    // halo (CORE_DP + RING_EXPANSION_DP), y el nucleo en reposo mide CORE_DP. No es spacing de
    // interfaz — es la animacion. [UI-THE-SPACING-SWEEP-BY-VOLUME-001]
    Box(modifier = modifier.size(BOX_DP), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(CORE_DP + RING_EXPANSION_DP * ring.value)
                .background(color.copy(alpha = (1f - ring.value) * 0.35f), CircleShape)
        )
        Box(Modifier.size(CORE_DP).background(color, CircleShape))
    }
}
