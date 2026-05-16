package io.apptolast.paparcar.presentation.home.sections.map.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.presentation.util.MapCircleFab
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_report_fab_cd

/**
 * Glass circular FAB anchored bottom-left of the map that switches the
 * Home surface into [HomeMode.Reporting]. Mirrors the right-side cluster
 * (location, parked car, midpoint) so the asymmetric layout reads as
 * "utilities right, contribution left". [HOME-REPORTMODE-001]
 */
@Composable
internal fun HomeReportFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MapCircleFab(
        icon = Icons.Outlined.Campaign,
        onClick = onClick,
        contentDescription = stringResource(Res.string.home_report_fab_cd),
        iconTint = MaterialTheme.colorScheme.primary,
        size = REPORT_FAB_SIZE_DP.dp,
        iconSize = REPORT_FAB_ICON_SIZE_DP.dp,
        shadowElevation = REPORT_FAB_ELEVATION_DP.dp,
        modifier = modifier,
    )
}

private const val REPORT_FAB_SIZE_DP = 56
private const val REPORT_FAB_ICON_SIZE_DP = 24
private const val REPORT_FAB_ELEVATION_DP = 6
