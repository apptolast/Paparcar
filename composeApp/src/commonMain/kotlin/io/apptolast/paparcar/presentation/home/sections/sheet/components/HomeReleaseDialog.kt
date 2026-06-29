package io.apptolast.paparcar.presentation.home.sections.sheet.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.runtime.Composable
import io.apptolast.paparcar.ui.components.PapAlertDialog
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_release_dialog_cancel
import paparcar.composeapp.generated.resources.home_release_dialog_delete_only
import paparcar.composeapp.generated.resources.home_release_dialog_message
import paparcar.composeapp.generated.resources.home_release_dialog_publish
import paparcar.composeapp.generated.resources.home_release_dialog_title

/**
 * Release-parking confirmation. Uses the shared [PapAlertDialog] molde so it
 * reads consistently with every other dialog in the app.
 *
 * Positive accent — releasing the spot is conceptually positive (sharing with
 * the community); the destructive choice ("Just release") is the outlined
 * secondary instead of the primary. [PEEK-ACTIONS-001]
 */
@Composable
internal fun HomeReleaseDialog(
    onDismiss: () -> Unit,
    onPublishSpot: () -> Unit,
    onDeleteOnly: () -> Unit,
    isLoading: Boolean = false,
) {
    PapAlertDialog(
        onDismiss = onDismiss,
        isLoading = isLoading,
        icon = Icons.Rounded.Campaign,
        title = stringResource(Res.string.home_release_dialog_title),
        body = stringResource(Res.string.home_release_dialog_message),
        primaryLabel = stringResource(Res.string.home_release_dialog_publish),
        primaryLeadingIcon = Icons.Rounded.Campaign,
        onPrimary = onPublishSpot,
        secondaryLabel = stringResource(Res.string.home_release_dialog_delete_only),
        secondaryLeadingIcon = Icons.AutoMirrored.Rounded.Logout,
        onSecondary = onDeleteOnly,
        cancelLabel = stringResource(Res.string.home_release_dialog_cancel),
    )
}
