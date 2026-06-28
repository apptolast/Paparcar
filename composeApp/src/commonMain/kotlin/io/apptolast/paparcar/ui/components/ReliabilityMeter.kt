package io.apptolast.paparcar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.presentation.util.SpotReliabilityUiState
import io.apptolast.paparcar.ui.theme.stateColors
import kotlin.math.roundToInt

/**
 * Componente ÚNICO de fiabilidad — medidor de [SEGMENT_COUNT] segmentos.
 *
 * Sustituye las 3 representaciones dispersas (texto suelto / barra / escudo %) por una sola
 * escala, con los **mismos colores de estado** ([SpotReliabilityUiState.stateColors]) que usan
 * marcadores y badges, para que mapa, lista y ficha lean igual. Ver regla de iconos en CLAUDE.md.
 *
 * Segmentos rellenos:
 *  - [SpotReliabilityUiState.HIGH]   → 5 (verde)
 *  - [SpotReliabilityUiState.MEDIUM] → 3 (ámbar)
 *  - [SpotReliabilityUiState.LOW]    → 1 (rojo)
 *  - [SpotReliabilityUiState.MANUAL] → 5 (azul, atestiguado por usuario; ignora [pct])
 *
 * @param pct Si se aporta (0..1), afina el nº de segmentos rellenos en vez del default por nivel.
 *            No aplica a MANUAL.
 */
@Composable
fun ReliabilityMeter(
    level: SpotReliabilityUiState,
    modifier: Modifier = Modifier,
    pct: Float? = null,
    barWidth: Dp = DEFAULT_BAR_WIDTH,
    barHeight: Dp = DEFAULT_BAR_HEIGHT,
    fillWidth: Boolean = false,
    contentDescription: String? = null,
) {
    val filled = filledSegments(level, pct)
    val activeColor = level.stateColors().bg
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = TRACK_ALPHA)
    Row(
        modifier = if (contentDescription != null) {
            modifier.semantics { this.contentDescription = contentDescription }
        } else {
            modifier
        },
        horizontalArrangement = Arrangement.spacedBy(BAR_GAP),
    ) {
        repeat(SEGMENT_COUNT) { index ->
            val barModifier = if (fillWidth) {
                Modifier.weight(1f).height(barHeight)
            } else {
                Modifier.size(barWidth, barHeight)
            }
            Box(
                barModifier
                    .clip(CircleShape)
                    .background(if (index < filled) activeColor else trackColor),
            )
        }
    }
}

private fun filledSegments(level: SpotReliabilityUiState, pct: Float?): Int = when (level) {
    SpotReliabilityUiState.MANUAL -> SEGMENT_COUNT
    else -> pct
        ?.let { (it * SEGMENT_COUNT).roundToInt().coerceIn(1, SEGMENT_COUNT) }
        ?: when (level) {
            SpotReliabilityUiState.HIGH   -> HIGH_SEGMENTS
            SpotReliabilityUiState.MEDIUM -> MEDIUM_SEGMENTS
            else                          -> LOW_SEGMENTS
        }
}

private const val SEGMENT_COUNT = 5
private const val HIGH_SEGMENTS = 5
private const val MEDIUM_SEGMENTS = 3
private const val LOW_SEGMENTS = 1
private const val TRACK_ALPHA = 0.15f
private val DEFAULT_BAR_WIDTH = 14.dp
private val DEFAULT_BAR_HEIGHT = 6.dp
private val BAR_GAP = 3.dp
