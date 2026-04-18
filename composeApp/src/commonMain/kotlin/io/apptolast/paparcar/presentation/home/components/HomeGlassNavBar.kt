package io.apptolast.paparcar.presentation.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.ui.components.GlassSurface

private val NAV_BAR_CORNER = 28.dp
private val ICON_SIZE = 22.dp
private val HIT_TARGET = 44.dp

@Composable
internal fun HomeGlassNavBar(
    onMapClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onMyCarClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        shape = RoundedCornerShape(NAV_BAR_CORNER),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavIcon(
                selected = Icons.Filled.Map,
                unselected = Icons.Outlined.Map,
                isSelected = true,
                onClick = onMapClick,
            )
            NavIcon(
                selected = Icons.Filled.History,
                unselected = Icons.Outlined.History,
                onClick = onHistoryClick,
            )
            NavIcon(
                selected = Icons.Filled.DirectionsCar,
                unselected = Icons.Outlined.DirectionsCar,
                onClick = onMyCarClick,
            )
            NavIcon(
                selected = Icons.Filled.Settings,
                unselected = Icons.Outlined.Settings,
                onClick = onSettingsClick,
            )
        }
    }
}

@Composable
private fun NavIcon(
    selected: ImageVector,
    unselected: ImageVector,
    isSelected: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (isSelected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)

    Surface(
        onClick = onClick,
        color = androidx.compose.ui.graphics.Color.Transparent,
        shape = RoundedCornerShape(12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(HIT_TARGET),
        ) {
            Icon(
                imageVector = if (isSelected) selected else unselected,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(ICON_SIZE),
            )
        }
    }
}
