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
import androidx.compose.material.icons.rounded.DirectionsCar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.detection.DetectionPhase
import io.apptolast.paparcar.ui.theme.PapDriveBlue
import io.apptolast.paparcar.ui.theme.PapMotion
import io.apptolast.paparcar.ui.theme.PapShapes
import io.apptolast.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_det_monitoring
import paparcar.composeapp.generated.resources.home_vehicle_chip_status_candidate

/**
 * Ephemeral "detection is tracking your trip" pill that floats over the map while a tracking job
 * runs (DetectionUiState.Monitoring). [DET-READY-001h]
 *
 * It is NOT a fixed bar and NOT part of the bottom sheet: the caller anchors it over the map,
 * centred under the search bar, and toggles [visible]. It slides in with a soft spring and fades
 * out when the trip ends. A pulsing "live" dot signals that tracking is active.
 *
 * @param elapsedLabel optional trip-elapsed text (Barlow Condensed); omitted when null.
 * @param phase coarse trip phase — flips the label/icon to a "Parking…" treatment once the user
 *   stops and the detector starts confirming a spot, mirroring the vehicle chip. [DET-PHASE-001]
 */
@Composable
internal fun HomeMonitoringPill(
    visible: Boolean,
    modifier: Modifier = Modifier,
    elapsedLabel: String? = null,
    phase: DetectionPhase = DetectionPhase.Driving,
    onClick: (() -> Unit)? = null,
) {
    AnimatedVisibility(
        visible = visible,
        // Sober "settle" spring (no overshoot) — alive but not bouncy. Slides UP from below since the
        // pill now lives at the bottom of the map, between the FABs. [FOLLOW-001]
        enter = slideInVertically(
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            initialOffsetY = { it / ENTER_SLIDE_DIVISOR },
        ) + scaleIn(initialScale = ENTER_INITIAL_SCALE) + fadeIn(),
        exit = slideOutVertically(
            animationSpec = tween(EXIT_DURATION_MS),
            targetOffsetY = { it / EXIT_SLIDE_DIVISOR },
        ) + fadeOut(animationSpec = tween(EXIT_DURATION_MS)),
        modifier = modifier,
    ) {
        MonitoringPillContent(elapsedLabel = elapsedLabel, phase = phase, onClick = onClick)
    }
}

/**
 * The settled pill visual, decoupled from the [AnimatedVisibility] wrapper so previews can render
 * it directly (a static `@Preview` of `AnimatedVisibility(visible = true)` captures the enter
 * transition's first frame — alpha 0 — and shows nothing).
 */
@Composable
internal fun MonitoringPillContent(
    elapsedLabel: String? = null,
    phase: DetectionPhase = DetectionPhase.Driving,
    onClick: (() -> Unit)? = null,
) {
    val shape = PapShapes.chip
    val color = MaterialTheme.colorScheme.surfaceContainer
    // The icon and accent colour track the trip phase, matching the vehicle chip so banner and chip
    // never disagree — following the shared map language: en-route BLUE while driving ("On the way",
    // same accent as the driving puck's halo), brand GREEN once stopped and confirming a spot
    // ("Parking…", hinting the upcoming parked state, which is always green). The label itself stays
    // neutral onSurface — the accent is carried by the dot/icon/border. [DET-PHASE-001]
    val isCandidate = phase == DetectionPhase.Candidate
    val accent = if (isCandidate) MaterialTheme.colorScheme.primary else PapDriveBlue
    val label = if (isCandidate) Res.string.home_vehicle_chip_status_candidate else Res.string.home_det_monitoring
    val trailingIcon = if (isCandidate) Icons.Rounded.DirectionsCar else Icons.Rounded.Route
    val border = BorderStroke(BORDER_DP.dp, accent.copy(alpha = BORDER_ALPHA))
    // Symmetric bookends: a live dot leads, the phase icon trails — mirroring each other around the
    // label — and the whole pill replaces the GPS FAB during a trip, so tapping it recentres on the
    // moving car (resumes follow). Thicker accent border gives it presence. [FOLLOW-001]
    val pill = @Composable {
        Row(
            modifier = Modifier.padding(horizontal = PILL_PADDING_H_DP.dp, vertical = PILL_PADDING_V_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PILL_GAP_DP.dp),
        ) {
            LiveDot(color = accent)
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (elapsedLabel != null) {
                Text(
                    text = elapsedLabel,
                    style = PaparcarType.current.distance,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(ICON_DP.dp),
            )
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = color, shadowElevation = PILL_ELEVATION_DP.dp, border = border) { pill() }
    } else {
        Surface(shape = shape, color = color, shadowElevation = PILL_ELEVATION_DP.dp, border = border) { pill() }
    }
}

@Composable
private fun LiveDot(color: Color) {
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
            .background(color),
    )
}

private const val ENTER_SLIDE_DIVISOR = 4
private const val ENTER_INITIAL_SCALE = 0.95f
private const val EXIT_DURATION_MS = 260
private const val EXIT_SLIDE_DIVISOR = 5
private const val PILL_ELEVATION_DP = 6
// Bigger at the bottom — there's room between the FABs, and a trip-in-progress status earns presence. [FOLLOW-001]
private const val PILL_PADDING_H_DP = 18
private const val PILL_PADDING_V_DP = 11
private const val PILL_GAP_DP = 9
private const val ICON_DP = 20
private const val DOT_DP = 9
private const val DOT_ALPHA_MIN = 0.3f
private const val DOT_PULSE_MS = PapMotion.PulseExpand
private const val BORDER_DP = 2          // thicker border for presence [FOLLOW-001]
private const val BORDER_ALPHA = 0.55f
