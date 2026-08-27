package io.apptolast.paparcar.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.apptolast.paparcar.ui.theme.PapBorders
import io.apptolast.paparcar.ui.theme.PapShapes

/**
 * THE destructive-zone container. Before this existed, Settings and VehicleRegistration each
 * declared their own "red card" with zero shared values (bg 0.15 vs 0.3, border medium@0.7 vs
 * thin@0.4) — two incompatible grammars for the same warning. Canonical values are Settings'
 * (the SETTINGS-REMODEL-001 refinement). Content brings its own inner padding; pass [onClick]
 * when the whole card IS the action (row style). [SETTINGS-AUDIT-REMEDIATION-001]
 */
@Composable
fun PapDangerCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val color = cs.errorContainer.copy(alpha = DANGER_BG_ALPHA)
    val border = BorderStroke(PapBorders.medium, cs.error.copy(alpha = DANGER_BORDER_ALPHA))
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = PapShapes.card,
            color = color,
            border = border,
        ) { Column(content = content) }
    } else {
        Surface(
            modifier = modifier,
            shape = PapShapes.card,
            color = color,
            border = border,
        ) { Column(content = content) }
    }
}

private const val DANGER_BG_ALPHA = 0.15f
private const val DANGER_BORDER_ALPHA = 0.7f
