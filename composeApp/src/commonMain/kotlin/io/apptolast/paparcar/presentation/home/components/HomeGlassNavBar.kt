package io.apptolast.paparcar.presentation.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.ui.components.GlassDefaults
import io.apptolast.paparcar.ui.components.GlassSurface
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_nav_history
import paparcar.composeapp.generated.resources.home_nav_map
import paparcar.composeapp.generated.resources.home_nav_my_car
import paparcar.composeapp.generated.resources.home_nav_settings

private val NAV_BAR_CORNER_DP = 24.dp
private val TAB_CORNER_DP = 16.dp
private val ICON_SIZE_DP = 20.dp

@Composable
internal fun HomeGlassNavBar(
    onMapClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onMyCarClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        shape = RoundedCornerShape(NAV_BAR_CORNER_DP),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            NavTabItem(
                icon = Icons.Outlined.Map,
                label = stringResource(Res.string.home_nav_map),
                isSelected = true,
                onClick = onMapClick,
            )
            NavTabItem(
                icon = Icons.Outlined.History,
                label = stringResource(Res.string.home_nav_history),
                onClick = onHistoryClick,
            )
            NavTabItem(
                icon = Icons.Outlined.DirectionsCar,
                label = stringResource(Res.string.home_nav_my_car),
                onClick = onMyCarClick,
            )
            NavTabItem(
                icon = Icons.Outlined.Settings,
                label = stringResource(Res.string.home_nav_settings),
                onClick = onSettingsClick,
            )
        }
    }
}

@Composable
private fun NavTabItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (isSelected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant

    GlassSurface(
        onClick = onClick,
        shape = RoundedCornerShape(TAB_CORNER_DP),
        colors = if (isSelected) GlassDefaults.colors(
            container = MaterialTheme.colorScheme.primaryContainer,
        ) else GlassDefaults.colors(
            container = Color.Transparent,
        ),
        modifier = Modifier.padding(2.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(ICON_SIZE_DP),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
            )
        }
    }
}
