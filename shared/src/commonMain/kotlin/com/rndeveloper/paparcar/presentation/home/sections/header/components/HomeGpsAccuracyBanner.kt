package com.rndeveloper.paparcar.presentation.home.sections.header.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.ZONE_CHIP_HEIGHT
import com.rndeveloper.paparcar.presentation.util.MAP_FLOATING_SHADOW_DP
import com.rndeveloper.paparcar.ui.theme.PapMotion
import com.rndeveloper.paparcar.ui.theme.PapColor
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_gps_accuracy_banner

private const val ACCURACY_GOOD_THRESHOLD_M = 20f
private const val ACCURACY_MEDIUM_THRESHOLD_M = 50f
private const val BANNER_FILL_ALPHA = 0.88f

/** Stadium, like every other pill in the header — a fixed radius on a 34dp pill would not be one. */
private const val PILL_RADIUS_DP = 999
private const val PILL_START_INSET_DP = 12
private const val PILL_END_INSET_DP = 14
private const val PILL_ICON_DP = 18
private const val PILL_ICON_GAP_DP = 6

/** How much of its own width the pill drifts through on the way in and out. */
private const val SLIDE_DRIFT_DIVISOR = 4

private enum class GpsAccuracyLevel { GOOD, MEDIUM, POOR }

private fun gpsAccuracyLevel(accuracy: Float): GpsAccuracyLevel = when {
    accuracy > 0f && accuracy < ACCURACY_GOOD_THRESHOLD_M -> GpsAccuracyLevel.GOOD
    accuracy < ACCURACY_MEDIUM_THRESHOLD_M -> GpsAccuracyLevel.MEDIUM
    else -> GpsAccuracyLevel.POOR
}

/**
 * Pill shown in the header lane below the zone chips when GPS accuracy is degraded.
 *
 * Hidden when [accuracy] is null (no fix yet) or below [ACCURACY_GOOD_THRESHOLD_M].
 * Amber for medium accuracy (20–50 m), red for poor (> 50 m).
 *
 * It wears the geometry the zone row already declares — [ZONE_CHIP_HEIGHT], stadium shape and the
 * FAB shadow every floating-over-map control carries — because it is a fourth control of the same
 * family, not something stuck underneath. Its LANE is reserved by the caller, so appearing never
 * changes the header's height and the motion is a horizontal drift, nothing more.
 * [UI-HEADER-PILLS-SHARE-ONE-GEOMETRY-001]
 */
@Composable
fun HomeGpsAccuracyBanner(
    accuracy: Float?,
    modifier: Modifier = Modifier,
) {
    val level = accuracy?.let { gpsAccuracyLevel(it) } ?: GpsAccuracyLevel.GOOD
    val visible = level != GpsAccuracyLevel.GOOD

    AnimatedVisibility(
        visible = visible,
        // In from the start edge, out the same way. No vertical component: the lane is already
        // there, so there is nothing to push. [UI-HEADER-PILLS-SHARE-ONE-GEOMETRY-001]
        enter = fadeIn(PapMotion.medium()) +
            slideInHorizontally(PapMotion.medium()) { -it / SLIDE_DRIFT_DIVISOR },
        exit = fadeOut(PapMotion.medium()) +
            slideOutHorizontally(PapMotion.medium()) { -it / SLIDE_DRIFT_DIVISOR },
        modifier = modifier,
    ) {
        // The banner's content colour is the `on…` of whatever fills it, never a raw white: a fill
        // token and its content token move together. [UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001]
        val poor = level == GpsAccuracyLevel.POOR
        val containerColor = if (poor) {
            PapColor.danger.copy(alpha = BANNER_FILL_ALPHA)
        } else {
            PapColor.attention.copy(alpha = BANNER_FILL_ALPHA)
        }
        val contentColor = if (poor) {
            PapColor.onDanger
        } else {
            PapColor.onAttention
        }

        Surface(
            shape = RoundedCornerShape(PILL_RADIUS_DP.dp),
            color = containerColor,
            // Same FAB shadow as the chips and the search bar — it floats over the same map.
            // The FILL is the one thing it does NOT share: this pill's colour IS its message, so it
            // cannot go glass like the rest of the family. [MAP-GLASS-001]
            shadowElevation = MAP_FLOATING_SHADOW_DP.dp,
        ) {
            Row(
                // Declared, not emergent — the row above declares its height for exactly this
                // reason, and a pill that measures itself from its text is a pill of a different
                // size in every locale.
                modifier = Modifier
                    .height(ZONE_CHIP_HEIGHT)
                    .padding(start = PILL_START_INSET_DP.dp, end = PILL_END_INSET_DP.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(PILL_ICON_DP.dp),
                )
                Spacer(Modifier.width(PILL_ICON_GAP_DP.dp))
                Text(
                    text = stringResource(Res.string.home_gps_accuracy_banner, accuracy?.toInt() ?: 0),
                    style = PaparcarType.current.label,
                    color = contentColor,
                )
            }
        }
    }
}
