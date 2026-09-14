package com.rndeveloper.paparcar.presentation.home.sections.sheet.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.runtime.Composable
import com.rndeveloper.paparcar.domain.model.ParkingReleaseReason
import com.rndeveloper.paparcar.ui.components.PapAlertDialog
import com.rndeveloper.paparcar.ui.components.PapDialogAction
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_release_dialog_cancel
import paparcar.composeapp.generated.resources.home_release_dialog_confirm
import paparcar.composeapp.generated.resources.home_release_dialog_message
import paparcar.composeapp.generated.resources.home_release_dialog_message_private_zone
import paparcar.composeapp.generated.resources.home_release_dialog_title

/**
 * Leaving-in-the-car confirmation. Uses the shared [PapAlertDialog] molde so it reads consistently
 * with every other dialog in the app.
 *
 * ## One button, because the question belonged somewhere else
 *
 * [SPOT-A-PRIVATE-ZONE-DECIDES-THE-PUBLICATION-NOT-A-BUTTON-001] This dialog used to offer two
 * departures — "Release and notify" and "Just release" — and ask the user to choose per departure,
 * with the car started, for something that is a property of the PLACE: whether this spot is one you
 * share. Answered per-departure it has to be re-answered every single time you leave the same
 * garage; answered per-place it is a private zone, which the app already has and already remembers.
 *
 * So the place decides and this dialog TELLS instead of ASKING: the body says what leaving will do,
 * and when the park sits inside a private zone it says that no plaza goes out, names the zone, and
 * points at where that was decided. Silently publishing nothing under a button labelled "Release
 * and notify" was the alternative, and it is the same mute failure as a string missing from a
 * locale. [feedback_no_internals_in_user_copy] [feedback_no_silent_overload]
 */
@Composable
internal fun HomeReleaseDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    // Consequence of leaving: detection re-arms. When the car being released wasn't the active one,
    // releasing IS the declaration that you drive it, so it becomes active. [VEH-ACTIVE-FENCE-001]
    detectionNote: String,
    /** Name of the private zone this park falls inside, or null when it frees a community plaza.
     *  Resolved LIVE from the user's current zones, so a zone drawn around an already-parked car
     *  changes this copy — same answer the publish gate will give.
     *  [SPOT-A-PRIVATE-ZONE-DECIDES-THE-PUBLICATION-NOT-A-BUTTON-001] */
    privateZoneName: String? = null,
    /** WHICH release is running, so the spinner sits on the option the user chose.
     *  [UI-A-SPINNER-LIVES-IN-THE-BUTTON-THAT-WAS-PRESSED-001] */
    releasingReason: ParkingReleaseReason? = null,
) {
    val consequence = if (privateZoneName != null) {
        stringResource(Res.string.home_release_dialog_message_private_zone, privateZoneName)
    } else {
        stringResource(Res.string.home_release_dialog_message)
    }
    PapAlertDialog(
        onDismiss = onDismiss,
        loadingAction = when (releasingReason) {
            ParkingReleaseReason.DEPARTURE -> PapDialogAction.Primary
            // Any other reason belongs to a release this dialog did not start (auto-detected
            // departure, record deletion): nothing here is in flight, so nothing here spins.
            else -> null
        },
        // The icon is the DEPARTURE now, not the megaphone: what the user is confirming is that
        // they are leaving. Whether a plaza is announced is the body's job, and in a private zone
        // the megaphone would have been announcing nothing.
        icon = Icons.AutoMirrored.Rounded.Logout,
        title = stringResource(Res.string.home_release_dialog_title),
        body = consequence + "\n\n" + detectionNote,
        // No leading icon on the button: TEXT-ONLY is the default and an icon has to earn its place
        // (destructive, or provider identity). The old primary carried a megaphone because it was
        // competing with a second button. [UI-BUTTON-ICONS-EARN-THEIR-PLACE-001]
        primaryLabel = stringResource(Res.string.home_release_dialog_confirm),
        onPrimary = onConfirm,
        cancelLabel = stringResource(Res.string.home_release_dialog_cancel),
    )
}
