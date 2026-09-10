package com.rndeveloper.paparcar.presentation.home.sections.map.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.model.VehicleMonitoringStatus
import com.rndeveloper.paparcar.presentation.util.MapCircleFab
import com.rndeveloper.paparcar.ui.theme.PapColor
import com.rndeveloper.paparcar.ui.theme.PapGreen
import com.rndeveloper.paparcar.ui.theme.VehicleWatch
import com.rndeveloper.paparcar.ui.theme.PapMotion
import com.rndeveloper.paparcar.ui.theme.vehicleIdentityColor
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import com.rndeveloper.paparcar.ui.theme.watch
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.map_cd_layers
import paparcar.composeapp.generated.resources.map_layer_fuel_stations
import paparcar.composeapp.generated.resources.map_layer_public_parkings
import paparcar.composeapp.generated.resources.map_cd_go_to_car
import paparcar.composeapp.generated.resources.map_cd_midpoint
import paparcar.composeapp.generated.resources.map_cd_my_location

// ─────────────────────────────────────────────────────────────────────────────
// Right-side map control column — "where am I" affordances plus ONE circle that
// holds every map LAYER (see [MapLayersFab]). No layer toggle may live beside MapTypeToggle in
// the header: a third 56dp circle there squeezed the search bar to two lines,
// measured. [UI-HOME-SEARCH-BAR-MUST-STAY-ONE-LINE-001] [FUEL-STATIONS-MAP-AND-PEEK-001]
//
// TWO groups, not four buttons in a row. The layer toggle sat in the same 10dp
// rhythm as the camera trio, so it read as a fourth camera control — and what it
// does (paint something else on the map) has nothing to do with where the camera
// points. Same order, same heights; only the gap says they are different kinds.
// [UI-A-WARNING-COSTS-NOTHING-WHEN-SILENT-001]
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
    /** The public-parkings layer — the SECOND entry of the same menu, which is why the layer slot
     *  below is a menu now and not a toggle. [PARKINGS-ON-THE-MAP-001] */
    parkingsLayerOn: Boolean = false,
    onTogglePublicParkingsLayer: () -> Unit = {},
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(GROUP_GAP_DP.dp),
    ) {
        // ── Layers ── ONE circle, every layer inside it. [PARKINGS-ON-THE-MAP-001]
        MapLayersFab(
            fuelLayerOn = fuelLayerOn,
            parkingsLayerOn = parkingsLayerOn,
            onToggleFuelLayer = onToggleFuelLayer,
            onTogglePublicParkingsLayer = onTogglePublicParkingsLayer,
        )
        // ── Camera ──
        CameraFabGroup(
            hasActiveParking = hasActiveParking,
            hasGpsFix = hasGpsFix,
            selectedParkingWatch = selectedParkingWatch,
            drivingWatch = drivingWatch,
            followsCar = followsCar,
            onMyLocation = onMyLocation,
            onParkedCar = onParkedCar,
            onMidpoint = onMidpoint,
        )
    }
}

/**
 * The camera trio — car, midpoint, locate-me — in its own rhythm. Two of the three come and go with
 * the session, and an [AnimatedVisibility] that is fully hidden emits no node, so the group simply
 * shrinks; the gap to the layer toggle above never doubles up.
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
) {
    Column(
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

/** Air between two kinds. Wider than [FAB_GAP_DP] on purpose: it is the only thing that says the
 *  layer toggle is not a fourth camera button. */
private const val GROUP_GAP_DP = 24

/**
 * One circle for every map LAYER, with the layers themselves inside it.
 * [PARKINGS-ON-THE-MAP-001]
 *
 * The fuel layer owned this slot alone, and that worked while there was one layer. Public car parks
 * make two, and the next one would make three: the column's own grouping rule already says the
 * layer slot is a DIFFERENT KIND from the camera trio, so what grows inside it is a list of choices
 * about what the map shows — not more circles in the one place the map is meant to be visible
 * through.
 *
 * ⛔ Not a modal over the sheet: it is a menu anchored to its own button, it covers nothing else,
 * and a tap outside dismisses it.
 *
 * Green when at least one layer is on — "an action in effect", the same green the fuel toggle wore
 * when it was alone here.
 */
@Composable
private fun MapLayersFab(
    fuelLayerOn: Boolean,
    parkingsLayerOn: Boolean,
    onToggleFuelLayer: () -> Unit,
    onTogglePublicParkingsLayer: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        MapCircleFab(
            icon = Icons.Rounded.Layers,
            iconTint = if (fuelLayerOn || parkingsLayerOn) PapGreen else Color.Unspecified,
            onClick = { expanded = true },
            contentDescription = stringResource(Res.string.map_cd_layers),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LayerMenuItem(
                icon = Icons.Rounded.LocalGasStation,
                label = stringResource(Res.string.map_layer_fuel_stations),
                checked = fuelLayerOn,
                onClick = onToggleFuelLayer,
            )
            LayerMenuItem(
                icon = Icons.Rounded.LocalParking,
                label = stringResource(Res.string.map_layer_public_parkings),
                checked = parkingsLayerOn,
                onClick = onTogglePublicParkingsLayer,
            )
        }
    }
}

/**
 * A layer row: its glyph, its name, and a check when it is on.
 *
 * ⛔ The menu stays OPEN after a tap, on purpose — turning both layers on is two taps, and a menu
 * that closed under the finger would make the second one a fresh journey.
 */
@Composable
private fun LayerMenuItem(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                style = PaparcarType.current.body,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) PapGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            if (checked) {
                Icon(imageVector = Icons.Rounded.Check, contentDescription = null, tint = PapGreen)
            }
        },
        onClick = onClick,
    )
}
