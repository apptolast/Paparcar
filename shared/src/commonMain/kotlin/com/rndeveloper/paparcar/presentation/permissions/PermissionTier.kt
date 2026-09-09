package com.rndeveloper.paparcar.presentation.permissions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.ui.components.PapSectionHeaderRow
import com.rndeveloper.paparcar.ui.theme.PapColor
import com.rndeveloper.paparcar.ui.theme.PaparcarSpacing
import com.rndeveloper.paparcar.ui.theme.PaparcarType

private val HEADER_ICON_SIZE = 18.dp

/**
 * Sección de permisos: cabecera de **beneficio** (icono semántico + [PapSectionHeaderRow] + check
 * verde cuando el tier entero está concedido) y las filas a ancho completo.
 *
 * La timeline (nodo + conector vertical) que esto llevaba murió con los focos excluyentes
 * [ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001]: con 1-2 secciones por vista no queda ninguna
 * progresión que contar, y el raíl solo restaba ~48dp de ancho a cada card. El estado "concedido"
 * que contaba el nodo lo cuenta ahora el check de la cabecera. [ONB-SCAFFOLD-001]
 *
 * @param satisfied todas las filas requeridas del tier concedidas → check verde en la cabecera.
 * @param rows las [PermissionRow] del tier, apiladas con su propio espaciado.
 */
@Composable
internal fun PermissionTier(
    icon: ImageVector,
    title: String,
    benefit: String,
    satisfied: Boolean,
    modifier: Modifier = Modifier,
    rows: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        PapSectionHeaderRow(
            title = title,
            leading = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(HEADER_ICON_SIZE),
                )
            },
            trailing = if (satisfied) {
                {
                    // Verde de marca = acción completada; el estado en sí ya lo escribe cada fila.
                    // [UI-COLOR-DOCTRINE-001]
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = PapColor.progress,
                        modifier = Modifier.size(HEADER_ICON_SIZE),
                    )
                }
            } else {
                null
            },
        )
        Spacer(Modifier.height(PaparcarSpacing.xs))
        Text(
            text = benefit,
            style = PaparcarType.current.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(PaparcarSpacing.md))
        rows()
    }
}
