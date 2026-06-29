package io.apptolast.paparcar.presentation.permissions

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.permissions_status_granted
import paparcar.composeapp.generated.resources.permissions_status_optional
import paparcar.composeapp.generated.resources.permissions_status_pending

private val ROW_VERTICAL_PADDING = 14.dp
private val ROW_CONTENT_SPACING  = 14.dp
private val ICON_BOX_SIZE        = 40.dp
private val ICON_SIZE            = 22.dp
private val BADGE_ICON_SIZE      = 14.dp
private const val DISC_TINT_ALPHA = 0.15f

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
            // Disco circular del icono — gris en reposo, verde tenue al conceder. [ONB-IDENTITY-001 D]
            Surface(
                shape = CircleShape,
                color = if (granted) MaterialTheme.colorScheme.primary.copy(alpha = DISC_TINT_ALPHA)
                else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(ICON_BOX_SIZE),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (granted) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(ICON_SIZE),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusChip(state)
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

@Composable
private fun StatusChip(state: PermissionUiState) {
    val (chipBg, chipFg, label) = when (state) {
        PermissionUiState.Granted -> Triple(
            MaterialTheme.colorScheme.primary.copy(alpha = DISC_TINT_ALPHA),
            MaterialTheme.colorScheme.primary,
            stringResource(Res.string.permissions_status_granted),
        )
        PermissionUiState.Pending -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            stringResource(Res.string.permissions_status_pending),
        )
        PermissionUiState.Optional -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.secondary,
            stringResource(Res.string.permissions_status_optional),
        )
    }
    Surface(shape = MaterialTheme.shapes.small, color = chipBg) {
        Row(
            modifier = Modifier.padding(horizontal = PaparcarSpacing.sm, vertical = PaparcarSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PaparcarSpacing.xs),
        ) {
            if (state == PermissionUiState.Granted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = chipFg,
                    modifier = Modifier.size(BADGE_ICON_SIZE),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = chipFg,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
