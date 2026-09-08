package com.rndeveloper.paparcar.presentation.home.sections.sheet.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.runtime.Composable
import com.rndeveloper.paparcar.domain.model.ParkingReleaseReason
import com.rndeveloper.paparcar.ui.components.PapAlertDialog
import com.rndeveloper.paparcar.ui.components.PapDialogAction
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
    // Consequence of leaving: detection re-arms. When the car being released wasn't the active one,
    // releasing IS the declaration that you drive it, so it becomes active. [VEH-ACTIVE-FENCE-001]
    detectionNote: String,
    /** WHICH release is running, so the spinner sits on the option the user chose. The two are not
     *  interchangeable — one hands the freed space to the community and the other doesn't — so the
     *  old shared `isLoading` was lighting up the answer nobody gave.
     *  [UI-A-SPINNER-LIVES-IN-THE-BUTTON-THAT-WAS-PRESSED-001] */
    releasingReason: ParkingReleaseReason? = null,
) {
    PapAlertDialog(
        onDismiss = onDismiss,
        loadingAction = when (releasingReason) {
            ParkingReleaseReason.DEPARTURE_PUBLISHED -> PapDialogAction.Primary
            ParkingReleaseReason.DEPARTURE_UNPUBLISHED -> PapDialogAction.Secondary
            // Any other reason belongs to a release this dialog did not start (auto-detected
            // departure, record deletion): nothing here is in flight, so nothing here spins.
            else -> null
        },
        icon = Icons.Rounded.Campaign,
        title = stringResource(Res.string.home_release_dialog_title),
        body = stringResource(Res.string.home_release_dialog_message) + "\n\n" + detectionNote,
        primaryLabel = stringResource(Res.string.home_release_dialog_publish),
        primaryLeadingIcon = Icons.Rounded.Campaign,
        onPrimary = onPublishSpot,
        secondaryLabel = stringResource(Res.string.home_release_dialog_delete_only),
        secondaryLeadingIcon = Icons.AutoMirrored.Rounded.Logout,
        onSecondary = onDeleteOnly,
        cancelLabel = stringResource(Res.string.home_release_dialog_cancel),
    )
}
