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
import com.rndeveloper.paparcar.ui.theme.PapGap
import com.rndeveloper.paparcar.ui.theme.PapIconSize

private val CHIP_SHAPE = PapShapes.chip
private const val DISABLED_BG_ALPHA = 0.5f
private val DISABLED_BORDER_ALPHA = PapAlpha.dim
private val DISABLED_FG_ALPHA = PapAlpha.disabled
private val TRAILING_ICON_SIZE = 12.dp
private val TRAILING_ICON_ALPHA = PapAlpha.muted

/**
 * Paparcar's canonical chip — the single base used for filter chips, zone
 * chips, and any other label-plus-state pill across the app.
 *
 * **Seleccionado = FONDO y BORDE verdes, con el texto en `onSurface`.**
 * [UI-A-SELECTED-CHIP-IS-GREEN-BUT-ITS-WORD-IS-NOT-001]
 *
 * El verde marca el chip elegido con el contenedor de marca (`primaryContainer`) y su borde
 * `primary`; sin seleccionar, [PapBorders.DEFAULT_OUTLINE_ALPHA] —el mismo contorno tenue de una
 * card— mantiene los chips en la familia de las cards. [UI-SHEET-001]
 *
 * ⛔ **El texto NO se tiñe: `onSurface`, elegido o no.** Es lo que cambió respecto al diseño
 * anterior y no es un detalle — *lo verde es la superficie, no la palabra*. Con el texto también en
 * verde, un chip encendido tenía dos señales diciendo lo mismo y la etiqueta perdía legibilidad
 * justo cuando más se lee. Medido en los dos temas, `onSurface` sobre `primaryContainer` da
 * **13,8:1** en claro y **10,7:1** en oscuro — por encima del par de diseño `onPrimaryContainer`
 * (12,6:1 en claro), así que el contraste no es el precio de nada.
 * [UI-COLOR-GREEN-TEXT-EARNS-ITS-CONTRAST-001]
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
    val bg = when {
        !enabled -> cs.surfaceContainerHigh.copy(alpha = DISABLED_BG_ALPHA)
        selected -> cs.primaryContainer
        else -> cs.surfaceContainerHigh
    }
    val borderColor = when {
        !enabled -> cs.outlineVariant.copy(alpha = DISABLED_BORDER_ALPHA)
        selected -> PapColor.selected
        else -> cs.outline.copy(alpha = PapBorders.DEFAULT_OUTLINE_ALPHA)
    }
    // ⛔ `selected` NO entra aquí a propósito: la palabra se queda en `onSurface` esté o no elegido
    // el chip, y el verde vive sólo en la superficie y el borde. Ver el KDoc para la medida.
    // [UI-A-SELECTED-CHIP-IS-GREEN-BUT-ITS-WORD-IS-NOT-001]
    val contentColor = when {
        !enabled -> cs.onSurface.copy(alpha = DISABLED_FG_ALPHA)
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
            horizontalArrangement = Arrangement.spacedBy(PapGap.chipRow),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    // ⛔ El glifo lleva el MISMO color que la etiqueta, y por tanto tampoco se tiñe
                    // de verde al seleccionar. Fue verde siempre, «para que los chips se leyeran
                    // como acentos de marca»: una fila pintaba cinco verdes cuyo único mensaje era
                    // que eran chips, y el elegido competía con sus propios hermanos apagados. Lo
                    // que dice cuál está encendido es la superficie, no la tinta del contenido.
                    // [UI-ONE-SKELETON-ONE-EMPHASIS-ONE-GREEN-001]
                    // [UI-A-SELECTED-CHIP-IS-GREEN-BUT-ITS-WORD-IS-NOT-001]
                    tint = contentColor,
                    modifier = Modifier.size(PapIconSize.withLabel),
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

