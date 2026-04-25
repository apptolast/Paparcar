package io.apptolast.paparcar.presentation.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.presentation.util.MapCircleFab
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_fab_release_car
import paparcar.composeapp.generated.resources.home_fab_report_spot

private const val MAIN_FAB_SIZE_DP = 56
private const val MAIN_FAB_ICON_SIZE_DP = 24
private const val MAIN_FAB_ELEVATION_DP = 6

// ─────────────────────────────────────────────────────────────────────────────
// Primary action FAB — single, contextual.
//   • not parked → "Report spot"   (Campaign)
//   • parked     → "Release my car" (Logout)
//
// Replaces the previous Speed-Dial: with at most 1 action visible per state,
// the dial cost (tap-to-expand + tap-to-act) wasn't worth it. The opposite
// action (release while not parked, report while parked) is intentionally
// unavailable from the FAB — release lives inside the parking peek row, and
// reporting after a release is so close to "leaving the parking" that the
// state will already have flipped.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun HomeActionFab(
    hasActiveParking: Boolean,
    onReportManualSpot: () -> Unit,
    onReleaseParking: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = hasActiveParking,
        transitionSpec = {
            (fadeIn() + scaleIn(initialScale = 0.85f)) togetherWith
                (fadeOut() + scaleOut(targetScale = 0.85f))
        },
        modifier = modifier,
        label = "primary_fab",
    ) { parked ->
        MapCircleFab(
            icon = if (parked) Icons.AutoMirrored.Outlined.Logout else Icons.Outlined.Campaign,
            onClick = if (parked) onReleaseParking else onReportManualSpot,
            contentDescription = stringResource(
                if (parked) Res.string.home_fab_release_car else Res.string.home_fab_report_spot
            ),
            iconTint = MaterialTheme.colorScheme.primary,
            size = MAIN_FAB_SIZE_DP.dp,
            iconSize = MAIN_FAB_ICON_SIZE_DP.dp,
            shadowElevation = MAIN_FAB_ELEVATION_DP.dp,
        )
    }
}
