package io.apptolast.paparcar.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Branded alert / confirmation dialog.
 *
 * - [confirmLabel] triggers [onConfirm] and is rendered as a [PapPrimaryButton]-style action.
 * - [dismissLabel] triggers [onDismiss] and is rendered as a [PapTextButton]-style action.
 * - [icon] is optional; when provided it appears above the title.
 */
@Composable
fun PapAlertDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String? = null,
    icon: ImageVector? = null,
    isLoading: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            PapPrimaryButton(
                label = confirmLabel,
                onClick = onConfirm,
                isLoading = isLoading,
            )
        },
        dismissButton = dismissLabel?.let {
            {
                PapTextButton(
                    label = it,
                    onClick = onDismiss,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
    )
}
