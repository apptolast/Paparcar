package io.apptolast.paparcar.presentation.home.sections.map.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.ui.theme.PapMotion
import io.apptolast.paparcar.ui.theme.PapShapes
import io.apptolast.paparcar.ui.theme.rememberDataTypography
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_det_monitoring

/**
 * Ephemeral "detection is following your trip" pill that floats over the map while a tracking job
 * runs (DetectionUiState.Monitoring). [DET-READY-001h]
 *
 * It is NOT a fixed bar and NOT part of the bottom sheet: the caller anchors it over the map,
 * centred under the search bar, and toggles [visible]. It slides in with a soft spring and fades
 * out when the trip ends. A pulsing "live" dot signals that tracking is active.
 *
 * @param elapsedLabel optional trip-elapsed text (Barlow Condensed); omitted when null.
 */
@Composable
internal fun HomeMonitoringPill(
    visible: Boolean,
    modifier: Modifier = Modifier,
    elapsedLabel: String? = null,
) {
    AnimatedVisibility(
        visible = visible,
        // Sober "settle" spring (no overshoot) — alive but not bouncy.
        enter = slideInVertically(
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            initialOffsetY = { -it / ENTER_SLIDE_DIVISOR },
        ) + scaleIn(initialScale = ENTER_INITIAL_SCALE) + fadeIn(),
        exit = slideOutVertically(
            animationSpec = tween(EXIT_DURATION_MS),
            targetOffsetY = { -it / EXIT_SLIDE_DIVISOR },
        ) + fadeOut(animationSpec = tween(EXIT_DURATION_MS)),
        modifier = modifier,
    ) {
        MonitoringPillContent(elapsedLabel = elapsedLabel)
    }
}

/**
 * The settled pill visual, decoupled from the [AnimatedVisibility] wrapper so previews can render
 * it directly (a static `@Preview` of `AnimatedVisibility(visible = true)` captures the enter
 * transition's first frame — alpha 0 — and shows nothing).
 */
@Composable
internal fun MonitoringPillContent(elapsedLabel: String? = null) {
    Surface(
        shape = PapShapes.chip,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = PILL_ELEVATION_DP.dp,
        border = BorderStroke(BORDER_DP.dp, MaterialTheme.colorScheme.primary.copy(alpha = BORDER_ALPHA)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PILL_PADDING_H_DP.dp, vertical = PILL_PADDING_V_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PILL_GAP_DP.dp),
        ) {
            LiveDot()
            Icon(
                imageVector = Icons.Rounded.Route,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(ICON_DP.dp),
            )
            Text(
                text = stringResource(Res.string.home_det_monitoring),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (elapsedLabel != null) {
                Text(
                    text = elapsedLabel,
                    style = rememberDataTypography().distanceBadge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LiveDot() {
    val transition = rememberInfiniteTransition(label = "monitoring_live_dot")
    val pulseAlpha by transition.animateFloat(
        initialValue = DOT_ALPHA_MIN,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(DOT_PULSE_MS, easing = PapMotion.EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "monitoring_live_dot_alpha",
    )
    Box(
        modifier = Modifier
            .size(DOT_DP.dp)
            .alpha(pulseAlpha)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
    )
}

private const val ENTER_SLIDE_DIVISOR = 4
private const val ENTER_INITIAL_SCALE = 0.95f
private const val EXIT_DURATION_MS = 260
private const val EXIT_SLIDE_DIVISOR = 5
private const val PILL_ELEVATION_DP = 6
private const val PILL_PADDING_H_DP = 14
private const val PILL_PADDING_V_DP = 8
private const val PILL_GAP_DP = 8
private const val ICON_DP = 18
private const val DOT_DP = 8
private const val DOT_ALPHA_MIN = 0.3f
private const val DOT_PULSE_MS = PapMotion.PulseExpand
private const val BORDER_DP = 1
private const val BORDER_ALPHA = 0.4f
