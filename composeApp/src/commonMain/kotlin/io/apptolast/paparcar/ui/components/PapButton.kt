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
 * TEXT-ONLY is the DEFAULT — the label already names the action; a glyph next
 * to it is noise, not information. An [icon] has to EARN its place, and only
 * two cases do: a destructive action that deserves an extra beat of attention
 * (Delete), or provider identity (social-login logos). When in doubt, no icon.
 * [UI-BUTTON-ICONS-EARN-THEIR-PLACE-001]
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
