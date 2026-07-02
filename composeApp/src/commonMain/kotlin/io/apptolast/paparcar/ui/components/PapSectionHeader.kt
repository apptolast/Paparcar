package io.apptolast.paparcar.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.ui.theme.PaparcarType

/**
 * Canonical section header for Paparcar — single typographic recipe used
 * everywhere a vertical section starts (TU COCHE, PLAZAS LIBRES, ACTIVIDAD
 * SEMANAL, ZONAS HABITUALES, etc.).
 *
 * Recipe: uppercase + `labelMedium` + ExtraBold + 1sp tracking + muted tint.
 * Anchored on the Vehicle/History screen's pattern (the one the user
 * explicitly liked) and demoted to a neutral colour by default so it works
 * in both structural and emphasised contexts.
 */
@Composable
fun PapSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = title.uppercase(),
        modifier = modifier.fillMaxWidth(),
        style = PaparcarType.current.sectionHeader,
        color = color,
    )
}

/**
 * Section header with leading + trailing slots — same typographic recipe as
 * [PapSectionHeader] but composed inside a [Row] so callers can prepend a
 * status dot or append a count badge without forking the composable.
 */
@Composable
fun PapSectionHeaderRow(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        leading?.invoke()
        Text(
            text = title.uppercase(),
            style = PaparcarType.current.sectionHeader,
            color = color,
        )
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}
