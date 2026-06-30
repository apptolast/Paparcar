package io.apptolast.paparcar.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.ui.theme.PapShapes
import io.apptolast.paparcar.ui.theme.PaparcarSpacing
import io.apptolast.paparcar.ui.theme.outlineSubtle

private val CardElevation          = 1.dp
private val CardShadowElevation    = 1.dp
private val EmptyStateVerticalPadding = 24.dp
private val EmptyStateItemSpacing     = 6.dp

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
 * Outlined (bordered, flat) surface card — the canonical "neutral bordered card"
 * used across Settings, vehicle and detection surfaces. Defaults match the design
 * system: [PapShapes.card], `surfaceContainerHigh` fill and the [outlineSubtle]
 * hairline border. Unlike [PapCard] this is a raw slot (no imposed padding) so the
 * caller supplies its own Row/Column — matching the hand-rolled `Surface` it replaces.
 *
 * Pass [onClick] for an interactive (ripple) card.
 */
@Composable
fun PapOutlinedCard(
    modifier: Modifier = Modifier,
    shape: Shape = PapShapes.card,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    border: BorderStroke = outlineSubtle,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = containerColor,
            border = border,
            content = content,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = containerColor,
            border = border,
            content = content,
        )
    }
}

/**
 * Empty-state card shell: a [PapOutlinedCard] ([PapShapes.cardSmall]) wrapping a centred,
 * full-width [Column] with the standard vertical padding + item spacing. Callers fill in
 * the leading visual (illustration/icon), title, subtitle and optional action — keeping
 * their own text styles. Centralises only the repeated card+column scaffold.
 */
@Composable
fun PapEmptyStateCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    PapOutlinedCard(modifier = modifier.fillMaxWidth(), shape = PapShapes.cardSmall) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = EmptyStateVerticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(EmptyStateItemSpacing),
            content = content,
        )
    }
}
