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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * La marca que dice «éste es el control del que habla el tutorial».
 * [FIRST-STEPS-SHOW-WHERE-NOT-OPEN-001] [ONB-A-GUIDE-ANSWERS-UNTIL-YOU-ACT-001]
 *
 * ### Por qué se late el TAMAÑO y no sólo el color
 * Era un contorno de 2 dp cuyo **alpha** iba de 0,25 a 1. En device, sobre un chip de cristal que ya
 * flota encima de un mapa lleno de marcadores verdes, eso no se ve: el reporte fue literal — *«se
 * ilumina con un recuadro verde pero no capta la atención»*. Un borde que aparece y desaparece
 * compite con todo lo que hay debajo; **algo que se MUEVE no compite con nada**, porque el
 * movimiento es lo único que un mapa quieto no tiene.
 *
 * Así que la marca late en escala —un respiro de [GUIDE_SCALE_MAX], dibujo puro, sin tocar el
 * layout ni mover a sus vecinos— y el contorno se queda **fijo y opaco**, más grueso, para que en
 * todo momento se sepa QUÉ está señalando. El movimiento llama; el borde señala. Antes el borde
 * intentaba hacer las dos cosas y no hacía ninguna.
 *
 * ⛔ Draw-only: ni pointer input, ni overlay, ni scrim. El control marcado sigue funcionando bajo el
 * dedo — un coach mark que se come el gesto que enseña es el bug
 * [UI-A-COACH-MARK-MUST-NOT-EAT-THE-GESTURE-IT-TEACHES-001], ya arreglado una vez.
 *
 * ⚠️ `scale` es un `graphicsLayer`: NO cambia la medida, así que un chip marcado no empuja a los de
 * al lado ni reordena la fila. Lo único que puede recortarlo es un padre que clipee, y el header
 * flotante no lo hace.
 */
@Composable
fun Modifier.papGuideHighlight(active: Boolean, shape: Shape): Modifier {
    // The transition runs unconditionally so toggling [active] never changes the composition's shape.
    val breath by rememberInfiniteTransition(label = "guide_highlight").animateFloat(
        initialValue = 1f,
        targetValue = GUIDE_SCALE_MAX,
        animationSpec = infiniteRepeatable(tween(GUIDE_PULSE_MS), RepeatMode.Reverse),
        label = "guide_highlight_scale",
    )
    if (!active) return this
    return this
        .scale(breath)
        // Opaco y a 3 dp: el borde ya no parpadea, porque quien llama la atención es el respiro.
        .border(GUIDE_BORDER_DP.dp, MaterialTheme.colorScheme.primary, shape)
}

/** Un respiro completo. Más lento que los 700 ms de antes: a esa velocidad un cambio de TAMAÑO se
 *  lee como un temblor, no como una respiración. */
private const val GUIDE_PULSE_MS = 1_100

/** ⚠️ 6 %, no más. Medido contra el alto del chip (34 dp): a 1,06 el crecimiento es de 2 dp, que
 *  se ve de sobra y todavía no invade el aire de 8 dp que lo separa de sus vecinos. */
private const val GUIDE_SCALE_MAX = 1.06f

private const val GUIDE_BORDER_DP = 3
