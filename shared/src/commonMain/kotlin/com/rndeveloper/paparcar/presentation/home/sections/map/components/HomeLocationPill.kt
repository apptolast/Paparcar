package com.rndeveloper.paparcar.presentation.home.sections.map.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.presentation.util.MAP_FLOATING_SHADOW_DP
import com.rndeveloper.paparcar.presentation.util.radiusString
import com.rndeveloper.paparcar.ui.theme.PapMotion
import com.rndeveloper.paparcar.ui.theme.PapColor
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import com.rndeveloper.paparcar.ui.theme.PapControlHeight
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_gps_accuracy_banner
import paparcar.composeapp.generated.resources.home_location_off_pill

/**
 * The one reading that makes the pill worth its space: past this, the fix is too coarse to place or
 * read a pin against. The 20–50 m amber tier it used to also carry was retired — an advisory that
 * shows up every few days for a fix still good enough to park on is noise, and noise on a surface
 * this small costs more than it warns. [UI-A-WARNING-COSTS-NOTHING-WHEN-SILENT-001]
 *
 * It also closes an accidental case: the old three-rung ladder started at `accuracy > 0f`, so a
 * reading of exactly `0f` — no precision data, not a perfect one — fell through to the amber rung
 * and painted a warning measured from nothing. A single `> 50f` has nowhere to fall through to.
 */
private const val ACCURACY_POOR_THRESHOLD_M = 50f

private const val PILL_FILL_ALPHA = 0.88f

/** Stadium, like every other pill of the floating family — a fixed radius on a 34dp pill would not be one. */
private const val PILL_RADIUS_DP = 999
private const val PILL_START_INSET_DP = 12
private const val PILL_END_INSET_DP = 14
private const val PILL_ICON_DP = 18
private const val PILL_ICON_GAP_DP = 6

/** How much of its own width the pill drifts through on the way in and out. */
private const val SLIDE_DRIFT_DIVISOR = 4

/**
 * What the pill has to say about the location right now — ONE verdict, never two.
 * [UI-NO-LOCATION-WEARS-THE-ACCURACY-PILL-001]
 */
internal sealed interface LocationPillMessage {
    /** No location at all: a permission (or the system setting) is waiting on the user. */
    data object NoLocation : LocationPillMessage

    /** There is a fix, and it is too coarse to place or read a pin against. */
    data class PoorAccuracy(val metres: Int) : LocationPillMessage
}

/**
 * Which of the two things the pill says, or null for the silence that is nearly always the answer.
 *
 * ⛔ **Missing location OUTRANKS poor accuracy**, and the order is written rather than left to which
 * field happens to arrive first: without a fix there are no metres to report, so a pill reading
 * «±120 m» while the permission is denied would be reporting on a measurement that does not exist.
 *
 * Pure on purpose — this is the whole product rule of the pill, and it is testable without a
 * composition. [UI-NO-LOCATION-WEARS-THE-ACCURACY-PILL-001]
 */
internal fun locationPillMessage(locationMissing: Boolean, accuracy: Float?): LocationPillMessage? = when {
    locationMissing -> LocationPillMessage.NoLocation
    accuracy != null && accuracy > ACCURACY_POOR_THRESHOLD_M ->
        LocationPillMessage.PoorAccuracy(accuracy.toInt())
    else -> null
}

/**
 * The pill at the start of the map's FAB band — Home's ONE voice about the location it is working
 * with. [UI-NO-LOCATION-WEARS-THE-ACCURACY-PILL-001]
 *
 * It says «Sin ubicación» when there is no location to work with, and the fix's metres when the fix
 * is too coarse to trust. Those used to be two surfaces for one subject: the missing-location one
 * was a badge in the peek's trailing slot, where it competed for width with the car's name and the
 * address (user, issue #27) — and it competed there while THIS pill, whose whole job is the
 * location, stood silent, because without a fix there is no accuracy to report.
 *
 * Hidden whenever there is nothing to say — which is nearly always, and that is the point: it costs
 * no layout for its silence. It used to hold a reserved 34dp lane in the floating header for exactly
 * that silence, because down there a zero-height arrival PUSHED the header's column. Out here it
 * floats over the map, where appearing pushes nobody. [UI-A-WARNING-COSTS-NOTHING-WHEN-SILENT-001]
 *
 * Living in the FAB band also fixes when it is allowed to speak: the header hides itself for any
 * selection or pin mode, so the warning went away the moment the user started placing a parking —
 * the one moment a ±80 m fix decides whether the pin is worth anything. The band survives those
 * modes on purpose, and now the missing-location warning does too, which the peek badge never did.
 * [UI-HEADER-PILLS-SHARE-ONE-GEOMETRY-001]
 *
 * ⛔ **Each message keeps its own colour.** Missing location is [PapColor.attention] — «pending, and
 * you can fix it»: nothing is broken, a permission is waiting on the user, and it is TAPPABLE,
 * because a warning you cannot act on turns a fixable problem into a complaint
 * [HOME-LOCATION-WARNING-IS-A-BADGE-NOT-A-MODAL-001]. A fix too coarse to trust is [PapColor.danger]
 * and has nothing to tap: no setting makes the sky better.
 *
 * It wears the geometry of the floating family — [PapControlHeight.chip], stadium shape and the shadow
 * every floating-over-map control carries. The FILL is the one thing it does NOT share: this pill's
 * colour IS its message, so it cannot go glass like the rest. [MAP-GLASS-001]
 */
@Composable
fun HomeLocationPill(
    locationMissing: Boolean,
    accuracy: Float?,
    onFixLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = locationPillMessage(locationMissing, accuracy)

    // The message that JUSTIFIED the pill, held for the length of the exit. Without it a recovered
    // fix repaints the content under a pill that is still sliding out — the warning's last frames
    // would read "±9 m", which is the opposite of what it is there to say. Written from a
    // SideEffect (never during composition), and only ever read as the fallback, so the frame the
    // pill APPEARS on already carries the live value.
    var lastMessage by remember { mutableStateOf<LocationPillMessage>(LocationPillMessage.NoLocation) }
    SideEffect { message?.let { lastMessage = it } }
    val shown = message ?: lastMessage

    AnimatedVisibility(
        visible = message != null,
        // In from the start edge, out the same way. No vertical component: it hangs off the same
        // baseline as the FAB column and must not appear to rise from the sheet.
        enter = fadeIn(PapMotion.medium()) +
            slideInHorizontally(PapMotion.medium()) { -it / SLIDE_DRIFT_DIVISOR },
        exit = fadeOut(PapMotion.medium()) +
            slideOutHorizontally(PapMotion.medium()) { -it / SLIDE_DRIFT_DIVISOR },
        modifier = modifier,
    ) {
        val fill: Color = when (shown) {
            LocationPillMessage.NoLocation -> PapColor.attention
            is LocationPillMessage.PoorAccuracy -> PapColor.danger
        }
        val content: Color = when (shown) {
            LocationPillMessage.NoLocation -> PapColor.onAttention
            is LocationPillMessage.PoorAccuracy -> PapColor.onDanger
        }
        val icon: ImageVector = when (shown) {
            LocationPillMessage.NoLocation -> Icons.Rounded.LocationOff
            is LocationPillMessage.PoorAccuracy -> Icons.Rounded.LocationOn
        }
        val label = when (shown) {
            LocationPillMessage.NoLocation -> stringResource(Res.string.home_location_off_pill)
            is LocationPillMessage.PoorAccuracy ->
                // The accuracy IS a radius — the circle the fix could be anywhere inside — so it
                // goes through the same formatter and stops saying "m" to someone who asked for
                // miles. [I18N-A-RADIUS-WRITTEN-BY-HAND-IGNORES-MILES-001]
                stringResource(Res.string.home_gps_accuracy_banner, radiusString(shown.metres.toFloat()))
        }

        Surface(
            shape = RoundedCornerShape(PILL_RADIUS_DP.dp),
            // The pill's content colour is the `on…` of whatever fills it, never a raw white: a fill
            // token and its content token move together. [UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001]
            color = fill.copy(alpha = PILL_FILL_ALPHA),
            shadowElevation = MAP_FLOATING_SHADOW_DP.dp,
        ) {
            Row(
                // Declared, not emergent — the zone row declares its height for exactly this
                // reason, and a pill that measures itself from its text is a pill of a different
                // size in every locale.
                modifier = Modifier
                    .height(PapControlHeight.chip)
                    // Only the actionable message is a target: the whole pill, like the badge it
                    // replaces. A coarse fix has nowhere to send anyone.
                    .then(
                        if (shown is LocationPillMessage.NoLocation) {
                            Modifier.clickable(role = Role.Button, onClick = onFixLocation)
                        } else {
                            Modifier
                        },
                    )
                    .padding(start = PILL_START_INSET_DP.dp, end = PILL_END_INSET_DP.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(PILL_ICON_DP.dp),
                )
                Spacer(Modifier.width(PILL_ICON_GAP_DP.dp))
                Text(
                    text = label,
                    style = PaparcarType.current.label,
                    color = content,
                )
            }
        }
    }
}
