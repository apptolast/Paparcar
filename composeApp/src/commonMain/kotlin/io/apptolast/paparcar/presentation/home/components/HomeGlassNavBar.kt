package io.apptolast.paparcar.presentation.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_nav_history
import paparcar.composeapp.generated.resources.home_nav_map
import paparcar.composeapp.generated.resources.home_nav_my_car
import paparcar.composeapp.generated.resources.home_nav_settings

@Composable
internal fun HomeGlassNavBar(
    onMapClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onMyCarClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Sheet + nav form a single cohesive bottom block:
    // - both read MaterialTheme.colorScheme.surfaceContainer (PapInkContainer).
    // - tonalElevation = 0 prevents Surface from tinting on top of the token.
    // - HorizontalDivider acts as the visual seam between sheet peek content and nav.
    Column(modifier = modifier) {
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = NAV_DIVIDER_ALPHA),
        )
        NavigationBar(tonalElevation = 0.dp) {
            HomeNavItem(
                selected = Icons.Filled.NearMe,
                unselected = Icons.Outlined.NearMe,
                isSelected = true,
                onClick = onMapClick,
                contentDescription = stringResource(Res.string.home_nav_map),
            )
            HomeNavItem(
                selected = Icons.Filled.History,
                unselected = Icons.Outlined.History,
                onClick = onHistoryClick,
                contentDescription = stringResource(Res.string.home_nav_history),
            )
            HomeNavItem(
                selected = Icons.Filled.DirectionsCar,
                unselected = Icons.Outlined.DirectionsCar,
                onClick = onMyCarClick,
                contentDescription = stringResource(Res.string.home_nav_my_car),
            )
            HomeNavItem(
                selected = Icons.Filled.Settings,
                unselected = Icons.Outlined.Settings,
                onClick = onSettingsClick,
                contentDescription = stringResource(Res.string.home_nav_settings),
            )
        }
    }
}

@Composable
private fun RowScope.HomeNavItem(
    selected: ImageVector,
    unselected: ImageVector,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    contentDescription: String,
) {
    NavigationBarItem(
        selected = isSelected,
        onClick = onClick,
        icon = {
            Icon(
                imageVector = if (isSelected) selected else unselected,
                contentDescription = contentDescription,
            )
        },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = UNSELECTED_ICON_ALPHA),
            indicatorColor = Color.Transparent,
        ),
    )
}

private const val UNSELECTED_ICON_ALPHA = 0.55f
private const val NAV_DIVIDER_ALPHA = 0.12f
