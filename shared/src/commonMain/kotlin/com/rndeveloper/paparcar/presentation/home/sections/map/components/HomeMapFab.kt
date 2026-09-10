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
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Route
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.model.VehicleMonitoringStatus
import com.rndeveloper.paparcar.presentation.util.MapCircleFab
import com.rndeveloper.paparcar.ui.theme.PapColor
import com.rndeveloper.paparcar.ui.theme.VehicleWatch
import com.rndeveloper.paparcar.ui.theme.PapMotion
import com.rndeveloper.paparcar.ui.theme.vehicleIdentityColor
import com.rndeveloper.paparcar.ui.theme.watch
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.map_cd_go_to_car
import paparcar.composeapp.generated.resources.map_cd_midpoint
import paparcar.composeapp.generated.resources.map_cd_my_location

// ─────────────────────────────────────────────────────────────────────────────
// Right-side map control column — "where am I" affordances, and ONLY those.
//
// ⛔ It used to carry a second group: one circle holding the map LAYERS (fuel, car parks). That
// group is gone, and what took its job is the sheet's lens: the map paints the complement of the
// lane you are looking at, so "what is on the map" and "what is in the list" cannot disagree, and
// two label layers can no longer stack over the spot pins that are the product.
// [HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001] [PARKINGS-ON-THE-MAP-001]
//
// ⛔ Whatever is added here later does NOT go beside MapTypeToggle in the header: a third 56dp
// circle there squeezed the search bar to two lines, measured.
// [UI-HOME-SEARCH-BAR-MUST-STAY-ONE-LINE-001]
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
) {
    // With the layers group gone there is one group left, so there is no outer Column wrapping two
    // rhythms any more — the camera trio IS the column.
    CameraFabGroup(
        hasActiveParking = hasActiveParking,
        hasGpsFix = hasGpsFix,
        selectedParkingWatch = selectedParkingWatch,
        drivingWatch = drivingWatch,
        followsCar = followsCar,
        onMyLocation = onMyLocation,
        onParkedCar = onParkedCar,
        onMidpoint = onMidpoint,
        modifier = modifier,
    )
}

/**
 * The camera trio — car, midpoint, locate-me — in one rhythm. Two of the three come and go with the
 * session, and an [AnimatedVisibility] that is fully hidden emits no node, so the group simply
 * shrinks instead of leaving a hole where a FAB used to be.
 */
@Composable
private fun CameraFabGroup(
    hasActiveParking: Boolean,
    hasGpsFix: Boolean,
    selectedParkingWatch: VehicleMonitoringStatus?,
    drivingWatch: VehicleMonitoringStatus?,
    followsCar: Boolean,
    onMyLocation: () -> Unit,
    onParkedCar: () -> Unit,
    onMidpoint: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(FAB_GAP_DP.dp),
    ) {
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

/** Air between two FABs that do the same KIND of thing. */
private const val FAB_GAP_DP = 10
