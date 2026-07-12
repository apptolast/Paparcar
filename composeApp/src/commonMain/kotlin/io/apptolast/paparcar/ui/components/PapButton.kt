package io.apptolast.paparcar.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.ui.theme.PaparcarType

private val ButtonHorizontalPadding = 24.dp
private val ButtonVerticalPadding   = 14.dp
private val LoadingIndicatorSize    = 18.dp
private val LeadingIconSize         = 18.dp
private val IconLabelGap            = 8.dp

private val DefaultContentPadding = PaddingValues(
    horizontal = ButtonHorizontalPadding,
    vertical   = ButtonVerticalPadding,
)

/**
 * Primary filled button. Use for the main CTA in a screen.
 *
 * [icon] is the DEFAULT — every Paparcar button that names a concrete action
 * carries a leading icon so it reads at a glance. [UI-SHEET-002] Pass `null`
 * only for generic flow-control whose meaning the screen already fixes: the
 * sole submit of a single-purpose screen (login/register) or a wayfinding
 * "Next" — a glyph there is redundant noise.
 *
 * Supports an [isLoading] state — while loading the content is replaced by a
 * [CircularProgressIndicator] and the button is disabled.
 */
@Composable
fun PapPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isLoading: Boolean = false,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !isLoading,
        contentPadding = DefaultContentPadding,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(LoadingIndicatorSize),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(LeadingIconSize))
                        Spacer(Modifier.width(IconLabelGap))
                    }
                    Text(text = label, style = PaparcarType.current.cta)
                }
            }
        }
    }
}

/**
 * Text-only button. Use for low-emphasis actions (e.g. "Cancel", "Skip").
 * [icon] is REQUIRED — same at-a-glance rule as every other button. [UI-SHEET-002]
 */
@Composable
fun PapTextButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(LeadingIconSize))
        Spacer(Modifier.width(IconLabelGap))
        Text(
            text = label,
            style = PaparcarType.current.cta,
        )
    }
}
