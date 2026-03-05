package io.apptolast.paparcar.presentation.map.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.ui.theme.EcoGreen
import io.apptolast.paparcar.ui.theme.EcoGreenMuted
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.map_cd_go_to_car
import paparcar.composeapp.generated.resources.map_cd_midpoint
import paparcar.composeapp.generated.resources.map_cd_my_location

@Composable
internal fun MapControlButtons(
    userLocation: GpsPoint?,
    userParking: UserParking?,
    sheetBottomPadding: Dp,
    onMyLocation: () -> Unit,
    onParkedCar: () -> Unit,
    onMidpoint: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasParking = userParking != null
    val hasBothPoints = userLocation != null && userParking != null

    Column(
        modifier = modifier.padding(end = 12.dp, bottom = sheetBottomPadding + 12.dp),
        horizontalAlignment = Alignment.End,
    ) {
        AnimatedVisibility(
            visible = hasBothPoints,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
        ) {
            MapControlFab(
                icon = Icons.Outlined.Route,
                contentDescription = stringResource(Res.string.map_cd_midpoint),
                onClick = onMidpoint,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }

        AnimatedVisibility(
            visible = hasParking,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
        ) {
            MapControlFab(
                icon = Icons.Outlined.DirectionsCar,
                contentDescription = stringResource(Res.string.map_cd_go_to_car),
                iconTint = EcoGreen,
                surfaceColor = EcoGreenMuted,
                onClick = onParkedCar,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }

        MapControlFab(
            icon = Icons.Outlined.MyLocation,
            contentDescription = stringResource(Res.string.map_cd_my_location),
            onClick = onMyLocation,
        )
    }
}

@Composable
private fun MapControlFab(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
) {
    val resolvedSurface = if (surfaceColor == Color.Unspecified)
        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    else
        surfaceColor
    val resolvedTint = if (iconTint == Color.Unspecified)
        MaterialTheme.colorScheme.onSurface
    else
        iconTint

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = resolvedSurface,
        shadowElevation = 4.dp,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = resolvedTint,
            modifier = Modifier
                .padding(10.dp)
                .size(22.dp),
        )
    }
}
