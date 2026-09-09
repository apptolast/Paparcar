package com.rndeveloper.paparcar.presentation.home.sections.map.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Route
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.model.VehicleMonitoringStatus
import com.rndeveloper.paparcar.presentation.util.MapCircleFab
import com.rndeveloper.paparcar.ui.theme.PapColor
import com.rndeveloper.paparcar.ui.theme.PapGreen
import com.rndeveloper.paparcar.ui.theme.VehicleWatch
import com.rndeveloper.paparcar.ui.theme.PapMotion
import com.rndeveloper.paparcar.ui.theme.vehicleIdentityColor
import com.rndeveloper.paparcar.ui.theme.watch
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.map_cd_fuel_layer
import paparcar.composeapp.generated.resources.map_cd_go_to_car
import paparcar.composeapp.generated.resources.map_cd_midpoint
import paparcar.composeapp.generated.resources.map_cd_my_location

// ─────────────────────────────────────────────────────────────────────────────
// Right-side map control column — "where am I" affordances plus the one LAYER
// toggle (fuel stations). The layer toggle cannot live beside MapTypeToggle in
// the header: a third 56dp circle there squeezed the search bar to two lines,
// measured. [UI-HOME-SEARCH-BAR-MUST-STAY-ONE-LINE-001] [FUEL-STATIONS-MAP-AND-PEEK-001]
//   • Coche (only when a parking session is active) → recenters on the spot. It
//     cycles between parked sessions [MULTI-PARKING-001], so it wears the
//     selected vehicle's IDENTITY colour (blue = Bluetooth, green = active
//     detection, grey = unwatched) — the only cue for which car the camera
//     landed on. Neutral while nothing is selected. [UI-FAB-CAR-IDENTITY-001]
//   • Midpoint (only when both parking and GPS are known) → fits both in view
//     so the user can see how to reach the car.
//   • MyLocation → recenters on the live GPS position, OR — while detection is
//     monitoring a trip ([followsCar]) — re-engages driver-follow on the moving
//     car. It stays visible during the trip (the floating "monitoring" pill was
//     removed; the live phase now reads in the sheet eyebrow) and tints en-route
//     blue to signal it follows the car. [DET-STATUS-SHEET-001]
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun HomeMapFabColumn(
    hasActiveParking: Boolean,
    hasGpsFix: Boolean,
    // How the selected parked vehicle is watched; null when no session is selected.
    selectedParkingWatch: VehicleMonitoringStatus?,
    /** How the car being followed is watched — tints the `MyLocation` FAB while [followsCar].
     *  [UI-A-TRACKED-TRIP-WEARS-ITS-WATCH-001] */
    drivingWatch: VehicleMonitoringStatus?,
    onMyLocation: () -> Unit,
    onParkedCar: () -> Unit,
    onMidpoint: () -> Unit,
    modifier: Modifier = Modifier,
    // True while a trip is being monitored — tapping MyLocation follows the moving car, not GPS.
    followsCar: Boolean = false,
    /** The fuel-stations layer toggle. Brand green while ON — a layer you switched on is an action
     *  in effect, which is exactly what the green means. [FUEL-STATIONS-MAP-AND-PEEK-001] */
    fuelLayerOn: Boolean = false,
    onToggleFuelLayer: () -> Unit = {},
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MapCircleFab(
            icon = Icons.Rounded.LocalGasStation,
            iconTint = if (fuelLayerOn) PapGreen else Color.Unspecified,
            onClick = onToggleFuelLayer,
            contentDescription = stringResource(Res.string.map_cd_fuel_layer),
        )
        AnimatedVisibility(
            visible = hasActiveParking,
            enter = slideInVertically(PapMotion.medium(), initialOffsetY = { it }) + fadeIn(PapMotion.medium()),
            exit = slideOutVertically(PapMotion.medium(), targetOffsetY = { it }) + fadeOut(PapMotion.medium()),
        ) {
            MapCircleFab(
                icon = Icons.Rounded.DirectionsCar,
                // Neutral by default; once a session is selected the FAB wears THAT car's identity
                // colour, straight from the single resolver. [UI-FAB-CAR-IDENTITY-001]
                iconTint = selectedParkingWatch
                    ?.let { vehicleIdentityColor(it.watch()) }
                    ?: Color.Unspecified,
                onClick = onParkedCar,
                contentDescription = stringResource(Res.string.map_cd_go_to_car),
            )
        }
        AnimatedVisibility(
            visible = hasActiveParking && hasGpsFix,
            enter = slideInVertically(PapMotion.medium(), initialOffsetY = { it }) + fadeIn(PapMotion.medium()),
            exit = slideOutVertically(PapMotion.medium(), targetOffsetY = { it }) + fadeOut(PapMotion.medium()),
        ) {
            MapCircleFab(
                icon = Icons.Rounded.Route,
                onClick = onMidpoint,
                contentDescription = stringResource(Res.string.map_cd_midpoint),
            )
        }
        // Always visible — during a trip it re-engages driver-follow (tinted en-route blue to say so),
        // otherwise it recenters on GPS. Replaces the old floating "Following your trip" pill. [DET-STATUS-SHEET-001]
        MapCircleFab(
            icon = Icons.Rounded.MyLocation,
            // Following the LIVE car — it wears THAT car's watch colour, the same one its trail and
            // puck carry on the map. It used to be the movement blue, which on a Bluetooth car sat
            // 10.0 ΔE00 from the BT identity: two blues, two meanings, one screen. This FAB was
            // exempted from the identity resolver back when identity meant the BRAND green; now
            // that a watched car has its own hue, the exemption has no argument left.
            // [UI-A-TRACKED-TRIP-WEARS-ITS-WATCH-001] [UI-FAB-CAR-IDENTITY-001]
            iconTint = when {
                !followsCar -> Color.Unspecified
                // No watch to declare (or an unwatched car, whose grey vanishes over tiles) →
                // the movement blue it always had.
                drivingWatch == null || drivingWatch.watch() == VehicleWatch.Off -> PapColor.live
                else -> vehicleIdentityColor(drivingWatch.watch())
            },
            onClick = onMyLocation,
            contentDescription = stringResource(Res.string.map_cd_my_location),
        )
    }
}
