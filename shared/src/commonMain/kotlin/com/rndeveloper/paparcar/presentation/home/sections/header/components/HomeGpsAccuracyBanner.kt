package com.rndeveloper.paparcar.presentation.home.sections.header.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.ui.theme.PapMotion
import com.rndeveloper.paparcar.ui.theme.PapColor
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_gps_accuracy_banner

private const val ACCURACY_GOOD_THRESHOLD_M = 20f
private const val ACCURACY_MEDIUM_THRESHOLD_M = 50f
private const val BANNER_FILL_ALPHA = 0.88f

private enum class GpsAccuracyLevel { GOOD, MEDIUM, POOR }

private fun gpsAccuracyLevel(accuracy: Float): GpsAccuracyLevel = when {
    accuracy > 0f && accuracy < ACCURACY_GOOD_THRESHOLD_M -> GpsAccuracyLevel.GOOD
    accuracy < ACCURACY_MEDIUM_THRESHOLD_M -> GpsAccuracyLevel.MEDIUM
    else -> GpsAccuracyLevel.POOR
}

/**
 * Small pill shown below the search bar when GPS accuracy is degraded.
 *
 * Hidden when [accuracy] is null (no fix yet) or below [ACCURACY_GOOD_THRESHOLD_M].
 * Amber for medium accuracy (20–50 m), red for poor (> 50 m).
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
        enter = fadeIn(PapMotion.medium()) + slideInVertically(PapMotion.medium()) { -it / 2 },
        exit = fadeOut(PapMotion.medium()) + slideOutVertically(PapMotion.medium()) { -it / 2 },
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
            shape = RoundedCornerShape(20.dp),
            color = containerColor,
            shadowElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(Res.string.home_gps_accuracy_banner, accuracy?.toInt() ?: 0),
                    style = PaparcarType.current.label,
                    color = contentColor,
                )
            }
        }
    }
}
