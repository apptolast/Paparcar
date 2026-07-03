package io.apptolast.paparcar.presentation.permissions

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.ui.components.PapSectionHeader
import io.apptolast.paparcar.ui.theme.PaparcarSpacing
import io.apptolast.paparcar.ui.theme.PaparcarType

private val NODE_SIZE        = 32.dp
private val NODE_ICON_SIZE   = 18.dp
private val CONNECTOR_WIDTH  = 2.dp

/**
 * Hito de la timeline de permisos: un nodo en la columna izquierda (disco + conector vertical) con
 * el grupo de filas a la derecha bajo una cabecera de **beneficio** (no de etiqueta burocrática).
 *
 * La timeline va por *tier* (3 nodos: básico → detección → fiabilidad), no por permiso: comunica la
 * progresión de valor ("cuanto más desbloqueas, mejor funciona") sin recargar con un badge por fila.
 * El nodo se pone verde de marca cuando el tier entero está concedido. [ONB-SCAFFOLD-001]
 *
 * @param satisfied todas las filas requeridas del tier concedidas → nodo verde + check.
 * @param isLast último tier → sin conector hacia abajo.
 * @param rows las [PermissionRow] del tier, apiladas con su propio espaciado.
 */
@Composable
internal fun PermissionTier(
    icon: ImageVector,
    title: String,
    benefit: String,
    satisfied: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
    rows: @Composable ColumnScope.() -> Unit,
) {
    val nodeColor by animateColorAsState(
        targetValue = if (satisfied) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        label = "tier_node_bg",
    )
    val connectorColor = MaterialTheme.colorScheme.outlineVariant

    // IntrinsicSize.Min → la altura del Row la fija la columna de contenido; el conector (weight 1f)
    // rellena lo que quede bajo el nodo y enlaza con el siguiente tier de forma continua.
    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top,
    ) {
        // ── Spine: nodo + conector ──────────────────────────────────────────
        Column(
            modifier = Modifier.fillMaxHeight().width(NODE_SIZE),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(shape = CircleShape, color = nodeColor, modifier = Modifier.size(NODE_SIZE)) {
                Box(contentAlignment = Alignment.Center) {
                    // Concedido → check verde; pendiente → icono semántico del tier (jerarquía visual
                    // + el usuario reconoce de un vistazo de qué va cada sección).
                    Icon(
                        imageVector = if (satisfied) Icons.Rounded.Check else icon,
                        contentDescription = null,
                        tint = if (satisfied) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(NODE_ICON_SIZE),
                    )
                }
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(CONNECTOR_WIDTH)
                        .weight(1f)
                        .background(connectorColor),
                )
            }
        }

        Spacer(Modifier.width(PaparcarSpacing.lg))

        // ── Contenido: cabecera de beneficio + filas ────────────────────────
        Column(modifier = Modifier.weight(1f)) {
            PapSectionHeader(title = title)
            Spacer(Modifier.height(PaparcarSpacing.xs))
            Text(
                text = benefit,
                style = PaparcarType.current.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(PaparcarSpacing.md))
            rows()
            // Espacio inter-tier dentro del contenido para que el conector llegue al siguiente nodo.
            if (!isLast) Spacer(Modifier.height(PaparcarSpacing.xl))
        }
    }
}
