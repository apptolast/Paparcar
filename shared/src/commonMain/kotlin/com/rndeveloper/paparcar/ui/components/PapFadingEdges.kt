package com.rndeveloper.paparcar.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * **Una fila que se desplaza tiene que DECIRLO.** [UI-A-SCROLLING-CHIP-ROW-SAYS-SO-001]
 *
 * Difumina el borde por el que queda contenido fuera de pantalla, y sólo ese: el que se puede
 * alcanzar. Un chip cortado a hueso contra el canto de la pantalla se lee como un bug de layout
 * —«buscando |»—; el mismo chip desvaneciéndose se lee como «hay más, arrastra». Es la diferencia
 * entre un texto roto y un gesto sugerido, y no cuesta ni una línea de copy ni acortar ninguna
 * etiqueta.
 *
 * ⛔ **Se difumina el CONTENIDO, no se pinta una cortina encima.** `BlendMode.DstIn` multiplica el
 * alfa de lo ya dibujado, así que funciona sobre cualquier fondo —card, hoja, mapa— sin tener que
 * saber de qué color es. Pintar un degradado del color del fondo obligaría a cada call site a
 * acertar ese color, y el día que la card cambie de tono quedarían dos bordes distintos.
 *
 * Requiere [CompositingStrategy.Offscreen] para que el blend vea sólo esta capa.
 *
 * @param state el estado del scroll — un `LazyListState`, un `ScrollState`, cualquiera.
 * @param edgeWidth ancho del desvanecido. El de por defecto deja ver medio chip, que es lo que
 *   convierte «cortado» en «hay más».
 */
@Composable
fun Modifier.papHorizontalFadingEdges(
    state: ScrollableState,
    edgeWidth: Dp = PapFadingEdgeWidth,
): Modifier {
    // Animados: al llegar a un extremo el borde se apaga en vez de desaparecer de golpe, que es lo
    // que delata el truco.
    val startFade by animateFloatAsState(
        targetValue = if (state.canScrollBackward) 1f else 0f,
        label = "papFadeStart",
    )
    val endFade by animateFloatAsState(
        targetValue = if (state.canScrollForward) 1f else 0f,
        label = "papFadeEnd",
    )
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val width = edgeWidth.toPx().coerceAtMost(size.width / 2f)
            if (startFade > 0f) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        // El alfa del extremo ES la intensidad del desvanecido: 0 = invisible ahí,
                        // 1 = intacto. Interpolarlo con el color evita tener que pasar `alpha`, que
                        // en DstIn se aplicaría también al tramo opaco.
                        colors = listOf(Color.Black.copy(alpha = 1f - startFade), Color.Black),
                        startX = 0f,
                        endX = width,
                    ),
                    topLeft = Offset.Zero,
                    size = Size(width, size.height),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (endFade > 0f) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Black, Color.Black.copy(alpha = 1f - endFade)),
                        startX = size.width - width,
                        endX = size.width,
                    ),
                    topLeft = Offset(size.width - width, 0f),
                    size = Size(width, size.height),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
}

/** Medio chip: lo justo para que el borde se lea como «hay más», no como un recorte. */
val PapFadingEdgeWidth: Dp = 28.dp
