package com.rndeveloper.paparcar.ui.components.chips

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.ui.theme.PapBorders
import com.rndeveloper.paparcar.ui.theme.PapColor
import com.rndeveloper.paparcar.ui.theme.PapShapes
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import com.rndeveloper.paparcar.ui.theme.PapAlpha

private val CHIP_SHAPE = PapShapes.chip
private const val DISABLED_BG_ALPHA = 0.5f
private val DISABLED_BORDER_ALPHA = PapAlpha.dim
private val DISABLED_FG_ALPHA = PapAlpha.disabled
private val CHIP_ICON_SIZE = 18.dp
private val TRAILING_ICON_SIZE = 12.dp
private val TRAILING_ICON_ALPHA = PapAlpha.muted

/**
 * Paparcar's canonical chip — the single base used for filter chips, zone
 * chips, and any other label-plus-state pill across the app.
 *
 * **Seleccionado = BORDE + TEXTO verdes sobre el MISMO fondo que sin seleccionar.**
 * [UI-A-SELECTED-CHIP-IS-OUTLINED-NOT-FILLED-001]
 *
 * ⛔ El fondo NO cambia con la selección. Tenía `primaryContainer`, y una fila de filtros con uno
 * relleno de verde convierte el filtro en lo más llamativo de la pantalla: el verde de Paparcar es
 * la tinta más escasa que hay —es lo que se puede HACER ahora y la identidad de un coche vigilado—
 * y un filtro no es ninguna de las dos cosas. El estado se lee igual de bien con el borde y el
 * texto, que es lo que el ojo sigue en una fila de píldoras, y deja el neón para el CTA.
 * Sin seleccionar sigue con [PapBorders.DEFAULT_OUTLINE_ALPHA] —el mismo contorno tenue de una
 * card— así que los chips siguen siendo de la familia de las cards. [UI-SHEET-001]
 * [UI-ONE-SKELETON-ONE-EMPHASIS-ONE-GREEN-001]
 *
 * Optional [trailingIcon] + [onTrailingClick] add a tap-handled icon slot
 * (e.g. `×` for delete on a saved zone chip) without forking the composable.
 */
@Composable
fun PaparcarFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    trailingContentDescription: String? = null,
    onTrailingClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    // ⛔ `selected` NO entra aquí a propósito: el fondo es el mismo esté o no elegido. Ver el KDoc.
    // [UI-A-SELECTED-CHIP-IS-OUTLINED-NOT-FILLED-001]
    val bg = when {
        !enabled -> cs.surfaceContainerHigh.copy(alpha = DISABLED_BG_ALPHA)
        else -> cs.surfaceContainerHigh
    }
    val borderColor = when {
        !enabled -> cs.outlineVariant.copy(alpha = DISABLED_BORDER_ALPHA)
        selected -> PapColor.selected
        else -> cs.outline.copy(alpha = PapBorders.DEFAULT_OUTLINE_ALPHA)
    }
    // ⛔ El TEXTO va en `actionText`, no en `selected`, y es consecuencia directa de quitar el
    // relleno: el verde elegido se apoyaba en `primaryContainer`, un par de color diseñado junto.
    // Sobre la superficie neutra ese mismo verde es una palabra verde sobre gris claro, que es
    // exactamente lo que [UI-COLOR-GREEN-TEXT-EARNS-ITS-CONTRAST-001] existe para impedir. El BORDE
    // sí se queda en `selected`: un trazo no se lee, se ve.
    // [UI-A-SELECTED-CHIP-IS-OUTLINED-NOT-FILLED-001]
    val contentColor = when {
        !enabled -> cs.onSurface.copy(alpha = DISABLED_FG_ALPHA)
        selected -> PapColor.actionText
        else -> cs.onSurface
    }

    val hasTrailing = trailingIcon != null && onTrailingClick != null
    val endPadding = if (hasTrailing) 6.dp else 12.dp

    Surface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = CHIP_SHAPE,
        color = bg,
        border = BorderStroke(width = PapBorders.thin, color = borderColor),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = endPadding, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    // ⛔ The glyph wears the CHIP's own content colour — green only when the chip is
                    // selected, because then the green means "this one is on". It used to be green
                    // always, "so chips read as branded accents": with a row of chips that painted
                    // five greens whose only message was that they were chips, and left the selected
                    // one competing with its own unselected siblings.
                    // [UI-ONE-SKELETON-ONE-EMPHASIS-ONE-GREEN-001]
                    tint = contentColor,
                    modifier = Modifier.size(CHIP_ICON_SIZE),
                )
            }
            Text(
                text = label,
                style = PaparcarType.current.label,
                color = contentColor,
            )
            if (trailingIcon != null && onTrailingClick != null) {
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .clickable(onClick = onTrailingClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = trailingIcon,
                        contentDescription = trailingContentDescription,
                        tint = contentColor.copy(alpha = TRAILING_ICON_ALPHA),
                        modifier = Modifier.size(TRAILING_ICON_SIZE),
                    )
                }
            }
        }
    }
}

