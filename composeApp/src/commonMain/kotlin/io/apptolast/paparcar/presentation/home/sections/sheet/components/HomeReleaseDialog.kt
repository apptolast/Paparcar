package io.apptolast.paparcar.presentation.home.sections.sheet.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_release_dialog_delete_only
import paparcar.composeapp.generated.resources.home_release_dialog_message
import paparcar.composeapp.generated.resources.home_release_dialog_publish
import paparcar.composeapp.generated.resources.home_release_dialog_title

/**
 * Confirmation prompt shown when the user taps the "release parking" action.
 * Offers two terminal choices — share the freed spot with the community, or
 * just clear the session privately. Both implicitly dismiss the dialog
 * because the caller drives the visibility flag via [onDismiss]. [PEEK-ACTIONS-001]
 */
@Composable
internal fun HomeReleaseDialog(
    onDismiss: () -> Unit,
    onPublishSpot: () -> Unit,
    onDeleteOnly: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.home_release_dialog_title)) },
        text = { Text(stringResource(Res.string.home_release_dialog_message)) },
        confirmButton = {
            TextButton(onClick = onPublishSpot) {
                Text(stringResource(Res.string.home_release_dialog_publish))
            }
        },
        dismissButton = {
            TextButton(onClick = onDeleteOnly) {
                Text(stringResource(Res.string.home_release_dialog_delete_only))
            }
        },
    )
}
