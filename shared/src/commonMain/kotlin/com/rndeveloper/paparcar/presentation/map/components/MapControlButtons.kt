package com.rndeveloper.paparcar.presentation.map.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.presentation.util.MapCircleFab
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.map_cd_go_to_parking
import paparcar.composeapp.generated.resources.map_cd_midpoint_parking
import paparcar.composeapp.generated.resources.map_cd_my_location

/**
 * Control column of the HISTORY DETAIL map — the same "where am I" affordances Home has
 * ([com.rndeveloper.paparcar.presentation.home.sections.map.components.HomeMapFabColumn]), on the
 * same [MapCircleFab], for the screen that opens one past parking on a full-screen map.
 * [UI-HISTORY-DETAIL-HAS-THE-MAP-CONTROLS-001]
 *
 * The subject here is [parkingLocation] — the pin of the parking BEING READ — not the car parked
 * right now: on a historic entry the car is long gone, which is why the copy says "este
 * aparcamiento" and not "mi coche" [COPY-SPOT-IS-NOT-A-PARKING-001].
 *
 * It carries no identity colour: which car this was is the sheet's job (its eyebrow already wears
 * the watch colour), and a closed session has no live tier to announce. [UI-COLOR-DOCTRINE-001]
 */
@Composable
internal fun MapControlButtons(
    userLocation: GpsPoint?,
    parkingLocation: GpsPoint?,
    sheetBottomPadding: Dp,
    onMyLocation: () -> Unit,
    onParking: () -> Unit,
    onMidpoint: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasParking = parkingLocation != null
    val hasBothPoints = userLocation != null && parkingLocation != null

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
                icon = Icons.Rounded.Route,
                contentDescription = stringResource(Res.string.map_cd_midpoint_parking),
                onClick = onMidpoint,
                modifier = Modifier.padding(bottom = FAB_GAP_DP.dp),
                shadowElevation = SHADOW_DP.dp,
            )
        }

        AnimatedVisibility(
            visible = hasParking,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
        ) {
            MapCircleFab(
                // A parking sign, not a car: what this recenters on is the SPOT the car was left in.
                icon = Icons.Rounded.LocalParking,
                contentDescription = stringResource(Res.string.map_cd_go_to_parking),
                onClick = onParking,
                modifier = Modifier.padding(bottom = FAB_GAP_DP.dp),
                shadowElevation = SHADOW_DP.dp,
            )
        }

        MapCircleFab(
            icon = Icons.Rounded.MyLocation,
            contentDescription = stringResource(Res.string.map_cd_my_location),
            onClick = onMyLocation,
            shadowElevation = SHADOW_DP.dp,
        )
    }
}

private const val FAB_GAP_DP = 10
private const val SHADOW_DP = 4
