package io.apptolast.paparcar.presentation.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_fab_actions_collapse
import paparcar.composeapp.generated.resources.home_fab_actions_expand
import paparcar.composeapp.generated.resources.home_fab_release_car
import paparcar.composeapp.generated.resources.home_fab_report_spot

private const val ITEM_SPACING_DP = 10
private const val LABEL_CORNER_RADIUS_DP = 8
private const val LABEL_HORIZONTAL_PADDING_DP = 12
private const val LABEL_VERTICAL_PADDING_DP = 8
private const val ITEM_ICON_SIZE_DP = 20

// ─────────────────────────────────────────────────────────────────────────────
// Speed-Dial FAB
// Expands into two actions:
//   • "Report spot"    — always available
//   • "Release my car" — only when user has an active parking session
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun HomeActionFab(
    hasActiveParking: Boolean,
    onReportManualSpot: () -> Unit,
    onReleaseParking: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(ITEM_SPACING_DP.dp, Alignment.Bottom),
    ) {
        // ── "Release my car" — only when parked ──────────────────────────────
        AnimatedVisibility(
            visible = isExpanded && hasActiveParking,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            HomeActionFabItem(
                label = stringResource(Res.string.home_fab_release_car),
                icon = Icons.Outlined.DirectionsCar,
                onClick = {
                    isExpanded = false
                    onReleaseParking()
                },
            )
        }

        // ── "Report spot" — always available ─────────────────────────────────
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            HomeActionFabItem(
                label = stringResource(Res.string.home_fab_report_spot),
                icon = Icons.Outlined.Campaign,
                onClick = {
                    isExpanded = false
                    onReportManualSpot()
                },
            )
        }

        // ── Main FAB ──────────────────────────────────────────────────────────
        FloatingActionButton(
            onClick = { isExpanded = !isExpanded },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(),
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Outlined.Close else Icons.Outlined.Add,
                contentDescription = if (isExpanded)
                    stringResource(Res.string.home_fab_actions_collapse)
                else
                    stringResource(Res.string.home_fab_actions_expand),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Single speed-dial item: label chip + small FAB
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HomeActionFabItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ITEM_SPACING_DP.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(LABEL_CORNER_RADIUS_DP.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            shadowElevation = 4.dp,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(
                    horizontal = LABEL_HORIZONTAL_PADDING_DP.dp,
                    vertical = LABEL_VERTICAL_PADDING_DP.dp,
                ),
            )
        }
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(ITEM_ICON_SIZE_DP.dp),
            )
        }
    }
}
