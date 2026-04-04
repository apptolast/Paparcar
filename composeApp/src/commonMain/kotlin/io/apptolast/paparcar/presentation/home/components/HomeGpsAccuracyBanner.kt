package io.apptolast.paparcar.presentation.home.components

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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_gps_accuracy_banner

private const val ACCURACY_GOOD_THRESHOLD_M = 20f
private const val ACCURACY_MEDIUM_THRESHOLD_M = 50f

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
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
        modifier = modifier,
    ) {
        val containerColor = if (level == GpsAccuracyLevel.POOR) {
            Color(0xFFEF4444).copy(alpha = 0.88f)
        } else {
            Color(0xFFF59E0B).copy(alpha = 0.88f)
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
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(Res.string.home_gps_accuracy_banner, accuracy?.toInt() ?: 0),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }
    }
}
