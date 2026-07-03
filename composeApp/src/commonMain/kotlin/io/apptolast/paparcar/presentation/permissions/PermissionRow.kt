package io.apptolast.paparcar.presentation.permissions

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.ui.theme.PaparcarSpacing
import io.apptolast.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.permissions_status_granted
import paparcar.composeapp.generated.resources.permissions_status_optional
import paparcar.composeapp.generated.resources.permissions_status_pending

private val ROW_VERTICAL_PADDING = 14.dp
private val ROW_CONTENT_SPACING  = 14.dp
private val TITLE_TO_REASON_GAP  = 2.dp
private val MAIN_ICON_SIZE       = 24.dp
private val STATUS_ICON_SIZE     = 22.dp

/**
 * Estado visual de un permiso en la UI de onboarding/grant.
 * - [Granted] → verde de marca (concedido).
 * - [Pending] → neutro/gris (requerido, aún sin conceder).
 * - [Optional] → ámbar (opcional, mejora fiabilidad; sin conceder).
 */
enum class PermissionUiState { Granted, Pending, Optional }

/** Permiso requerido (esencial/detección): concedido → verde; si no → neutro. */
internal fun requiredState(granted: Boolean): PermissionUiState =
    if (granted) PermissionUiState.Granted else PermissionUiState.Pending

/** Permiso opcional (fiabilidad): concedido → verde; si no → ámbar. */
internal fun optionalState(granted: Boolean): PermissionUiState =
    if (granted) PermissionUiState.Granted else PermissionUiState.Optional

/**
 * Fila de permiso canónica reutilizada en la pantalla explicativa (rationale) y en la de concesión
 * (grant): disco circular con icono Material (Nivel 1/2, tintado) + título + razón + chip de estado.
 *
 * Si [onGrant] no es nulo y el estado no es [PermissionUiState.Granted], toda la fila es pulsable
 * para lanzar la concesión (permisos opcionales). En la pantalla explicativa se pasa `onGrant = null`
 * y la fila es informativa.
 */
@Composable
internal fun PermissionRow(
    icon: ImageVector,
    title: String,
    reason: String,
    state: PermissionUiState,
    modifier: Modifier = Modifier,
    onGrant: (() -> Unit)? = null,
) {
    val granted = state == PermissionUiState.Granted
    val surfaceColor by animateColorAsState(
        targetValue = if (granted) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        label = "perm_row_bg",
    )

    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = PaparcarSpacing.lg, vertical = ROW_VERTICAL_PADDING),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ROW_CONTENT_SPACING),
        ) {
            // Icono del permiso sin disco — glifo suelto, verde al conceder. [ONB-IDENTITY-001 D]
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (granted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(MAIN_ICON_SIZE),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(TITLE_TO_REASON_GAP),
            ) {
                Text(
                    text = title,
                    style = PaparcarType.current.rowTitle,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = reason,
                    style = PaparcarType.current.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Estado a la derecha, centrado verticalmente — solo glifo (sin chip/label).
            StatusIcon(state)
        }
    }

    if (onGrant != null && !granted) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = surfaceColor,
            onClick = onGrant,
            modifier = modifier.fillMaxWidth(),
        ) { rowContent() }
    } else {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = surfaceColor,
            modifier = modifier.fillMaxWidth(),
        ) { rowContent() }
    }
}

/**
 * Indicador de estado a la izquierda de la cabecera: glifo relleno verde si está concedido, círculo
 * de contorno (neutro/ámbar según requerido u opcional) si sigue pendiente. Sustituye al chip de
 * texto para no robar ancho a la descripción en pantallas estrechas. El label se mantiene como
 * `contentDescription` para lectores de pantalla.
 */
@Composable
private fun StatusIcon(state: PermissionUiState) {
    val (statusIcon, tint, label) = when (state) {
        PermissionUiState.Granted -> Triple(
            Icons.Rounded.CheckCircle,
            MaterialTheme.colorScheme.primary,
            stringResource(Res.string.permissions_status_granted),
        )
        PermissionUiState.Pending -> Triple(
            Icons.Rounded.RadioButtonUnchecked,
            MaterialTheme.colorScheme.onSurfaceVariant,
            stringResource(Res.string.permissions_status_pending),
        )
        PermissionUiState.Optional -> Triple(
            Icons.Rounded.RadioButtonUnchecked,
            MaterialTheme.colorScheme.secondary,
            stringResource(Res.string.permissions_status_optional),
        )
    }
    Icon(
        imageVector = statusIcon,
        contentDescription = label,
        tint = tint,
        modifier = Modifier.size(STATUS_ICON_SIZE),
    )
}
