package io.apptolast.paparcar.ui.components

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.apptolast.paparcar.ui.theme.PapBorders

/**
 * The one divider for the whole app — a single source of truth so every separator reads the same and
 * a tweak to weight/tone lands everywhere at once. 1dp at [PapBorders.HAIRLINE_DIVIDER_ALPHA] over
 * `outline`: present enough to structure a row, never a hard line. Do NOT hand-roll
 * `HorizontalDivider(color = outline.copy(alpha = …))` in feature code — use this. [UI-METRICS-POLISH-001]
 */
@Composable
fun PapDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outline.copy(alpha = PapBorders.HAIRLINE_DIVIDER_ALPHA),
) {
    HorizontalDivider(modifier = modifier, thickness = PapBorders.thin, color = color)
}

/** Vertical counterpart of [PapDivider] — for separating cells in a row (e.g. the stat readout). */
@Composable
fun PapVerticalDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outline.copy(alpha = PapBorders.HAIRLINE_DIVIDER_ALPHA),
) {
    VerticalDivider(modifier = modifier, thickness = PapBorders.thin, color = color)
}
