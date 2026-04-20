package io.apptolast.paparcar.presentation.home.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
internal fun HomeGlassNavBar(
    onMapClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onMyCarClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        HomeNavItem(
            selected = Icons.Filled.Map,
            unselected = Icons.Outlined.Map,
            isSelected = true,
            onClick = onMapClick,
        )
        HomeNavItem(
            selected = Icons.Filled.History,
            unselected = Icons.Outlined.History,
            onClick = onHistoryClick,
        )
        HomeNavItem(
            selected = Icons.Filled.DirectionsCar,
            unselected = Icons.Outlined.DirectionsCar,
            onClick = onMyCarClick,
        )
        HomeNavItem(
            selected = Icons.Filled.Settings,
            unselected = Icons.Outlined.Settings,
            onClick = onSettingsClick,
        )
    }
}

@Composable
private fun RowScope.HomeNavItem(
    selected: ImageVector,
    unselected: ImageVector,
    isSelected: Boolean = false,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = isSelected,
        onClick = onClick,
        icon = {
            Icon(
                imageVector = if (isSelected) selected else unselected,
                contentDescription = null,
            )
        },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            indicatorColor = Color.Transparent,
        ),
    )
}
