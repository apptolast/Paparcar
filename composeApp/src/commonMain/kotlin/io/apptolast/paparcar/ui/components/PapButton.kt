package io.apptolast.paparcar.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val ButtonHorizontalPadding = 24.dp
private val ButtonVerticalPadding   = 14.dp
private val LoadingIndicatorSize    = 18.dp

private val DefaultContentPadding = PaddingValues(
    horizontal = ButtonHorizontalPadding,
    vertical   = ButtonVerticalPadding,
)

/**
 * Primary filled button. Use for the main CTA in a screen.
 *
 * Supports an [isLoading] state — while loading the label is replaced by a
 * [CircularProgressIndicator] and the button is disabled.
 */
@Composable
fun PapPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !isLoading,
        contentPadding = DefaultContentPadding,
    ) {
        ButtonContent(label = label, isLoading = isLoading)
    }
}

/**
 * Secondary outlined button. Use for secondary actions alongside a primary CTA.
 */
@Composable
fun PapSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !isLoading,
        contentPadding = DefaultContentPadding,
    ) {
        ButtonContent(label = label, isLoading = isLoading)
    }
}

/**
 * Text-only button. Use for low-emphasis actions (e.g. "Cancel", "Skip").
 */
@Composable
fun PapTextButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun ButtonContent(label: String, isLoading: Boolean) {
    Box(contentAlignment = Alignment.Center) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(LoadingIndicatorSize),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
