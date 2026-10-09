package com.rndeveloper.paparcar.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One chevron of a pager stepper — the ‹ / › that step from the open item to its neighbour, shared
 * by the Home peek and the history detail. [HISTORY-DETAIL-002] [UI-PEEK-STEPS-BETWEEN-PINS-001]
 *
 * **A bare glyph, no pill.** It used to wear a `surfaceVariant` circle, and on device that put two
 * identical tonal circles side by side in the peek header: the × and the ›. One closes the card,
 * the other advances it, and they read as twins. So the fill is the hierarchy — a FILLED circle is
 * an action on this card (dismiss), a bare chevron is chrome that moves between cards — and the
 * 32dp footprint stays as the touch target and the circular ripple.
 *
 * Level-1 system icon: tinted with the theme, never a self-coloured glyph. [UI-COLOR-DOCTRINE-001]
 */
@Composable
internal fun PapStepperButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        // ⛔ El área de toque era de **32 dp** y el mínimo son 48.
        // [UI-THE-SPACING-SWEEP-IS-SCREEN-BY-SCREEN-001]
        //
        // `Modifier.clickable` NO aplica `minimumInteractiveComponentSize` — sólo lo hacen los
        // componentes de Material3 (`Surface(onClick)`, `Button`, `Chip`…), así que un control
        // dibujado a mano se queda exactamente con el área que pinta. Mismo patrón que el
        // `IconButton` de Material3: el mínimo PRIMERO, el tamaño pintado después.
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(PapStepperButtonSize)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(STEPPER_ICON_DP.dp),
        )
    }
}

/** Lo que PINTA un [PapStepperButton]. Lo que OCUPA es el mínimo táctil de 48 dp, que es más:
 *  [UI-THE-SPACING-SWEEP-IS-SCREEN-BY-SCREEN-001]. */
internal val PapStepperButtonSize: Dp = STEPPER_TOUCH_DP.dp

/**
 * A stepper slot: the chevron when there IS a neighbour ([onClick] non-null), and a spent, untappable
 * one when there isn't. [UI-PEEK-STEPS-BETWEEN-PINS-001]
 *
 * Why it dims instead of vanishing — the slot always holds its 32dp either way, because the block
 * between the two chevrons must not slide sideways when you reach an end. Leave the slot EMPTY and
 * that reserved gap has no visible cause: the header sits indented from the card's own body for no
 * reason the eye can explain (a single parked car, the first spot of the list). A spent chevron
 * explains the gap and states the boundary — "nothing older than this" — which is the argument the
 * history detail's disabled buttons were making all along.
 *
 * It carries no contentDescription: there is nothing to act on, so it is decoration to a screen
 * reader, not a dead control to stumble over.
 */
@Composable
internal fun PapStepperSlot(
    icon: ImageVector,
    contentDescription: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (onClick != null) {
        PapStepperButton(icon = icon, contentDescription = contentDescription, onClick = onClick, modifier = modifier)
        return
    }
    // El gastado reserva lo MISMO que el tappable —mínimo táctil incluido— o el bloque entre los dos
    // chevrones se desliza al llegar a un extremo, que es justo lo que este slot existe para evitar.
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(PapStepperButtonSize),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = STEPPER_SPENT_ALPHA),
            modifier = Modifier.size(STEPPER_ICON_DP.dp),
        )
    }
}

private const val STEPPER_ICON_DP = 22
private const val STEPPER_TOUCH_DP = 32
// Spent chevron: present enough to explain its slot and mark the end of the list, faint enough that
// nobody tries to press it.
private const val STEPPER_SPENT_ALPHA = 0.25f
