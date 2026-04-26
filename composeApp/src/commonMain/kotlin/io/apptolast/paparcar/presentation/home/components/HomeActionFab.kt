package io.apptolast.paparcar.presentation.home.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.presentation.util.MapCircleFab
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_fab_report_spot

private const val MAIN_FAB_SIZE_DP = 56
private const val MAIN_FAB_ICON_SIZE_DP = 24
private const val MAIN_FAB_ELEVATION_DP = 6

@Composable
internal fun HomeActionFab(
    onReportFreeSpot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MapCircleFab(
        icon = Icons.Outlined.Campaign,
        onClick = onReportFreeSpot,
        contentDescription = stringResource(Res.string.home_fab_report_spot),
        iconTint = MaterialTheme.colorScheme.primary,
        size = MAIN_FAB_SIZE_DP.dp,
        iconSize = MAIN_FAB_ICON_SIZE_DP.dp,
        shadowElevation = MAIN_FAB_ELEVATION_DP.dp,
        modifier = modifier,
    )
}
