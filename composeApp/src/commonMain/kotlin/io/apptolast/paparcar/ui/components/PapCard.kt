package io.apptolast.paparcar.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.ui.theme.PaparcarSpacing

private val CardElevation          = 1.dp
private val CardShadowElevation    = 1.dp

/**
 * Standard surface card.
 *
 * Uses [MaterialTheme.shapes.medium] (16 dp) and [MaterialTheme.colorScheme.surface]
 * by default. Wrap content in a [Column] internally so callers get a slot API.
 *
 * @param padding Inner padding applied around [content]. Defaults to [PaparcarSpacing.lg].
 */
@Composable
fun PapCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    tonalElevation: Dp = CardElevation,
    shadowElevation: Dp = CardShadowElevation,
    padding: Dp = PaparcarSpacing.lg,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
    ) {
        Column(modifier = Modifier.padding(padding)) {
            content()
        }
    }
}

/**
 * Clickable card variant. Identical to [PapCard] but interactive (ripple on tap).
 */
@Composable
fun PapClickableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    tonalElevation: Dp = CardElevation,
    shadowElevation: Dp = CardShadowElevation,
    padding: Dp = PaparcarSpacing.lg,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
    ) {
        Column(modifier = Modifier.padding(padding)) {
            content()
        }
    }
}
