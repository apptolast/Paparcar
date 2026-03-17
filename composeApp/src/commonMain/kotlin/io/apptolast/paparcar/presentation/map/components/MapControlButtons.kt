package io.apptolast.paparcar.presentation.map.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.presentation.util.MapCircleFab
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
            MapCircleFab(
                icon = Icons.Outlined.Route,
                contentDescription = stringResource(Res.string.map_cd_midpoint),
                onClick = onMidpoint,
                modifier = Modifier.padding(bottom = 10.dp),
                shadowElevation = 4.dp,
            )
        }

        AnimatedVisibility(
            visible = hasParking,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
        ) {
            MapCircleFab(
                icon = Icons.Outlined.DirectionsCar,
                contentDescription = stringResource(Res.string.map_cd_go_to_car),
                iconTint = MaterialTheme.colorScheme.primary,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                onClick = onParkedCar,
                modifier = Modifier.padding(bottom = 10.dp),
                shadowElevation = 4.dp,
            )
        }

        MapCircleFab(
            icon = Icons.Outlined.MyLocation,
            contentDescription = stringResource(Res.string.map_cd_my_location),
            onClick = onMyLocation,
            shadowElevation = 4.dp,
        )
    }
}
