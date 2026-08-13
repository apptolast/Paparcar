package io.apptolast.paparcar.presentation.home.sections.sheet.components.peek

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.EditLocationAlt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TimeToLeave
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.presentation.home.HomeIntent
import io.apptolast.paparcar.presentation.home.sections.sheet.HomeSheetAction
import io.apptolast.paparcar.presentation.home.sections.sheet.components.PapSheet
import io.apptolast.paparcar.presentation.home.sections.sheet.components.PapSheetLead
import io.apptolast.paparcar.presentation.home.sections.sheet.components.PapSheetRoundIconButton
import io.apptolast.paparcar.presentation.util.distanceMeters
import io.apptolast.paparcar.ui.components.PapFooterButton
import io.apptolast.paparcar.ui.components.PapFooterButtonStyle
import io.apptolast.paparcar.domain.model.monitoringStatus
import io.apptolast.paparcar.ui.theme.VehicleWatch
import io.apptolast.paparcar.ui.theme.vehicleIdentityColor
import io.apptolast.paparcar.ui.theme.watch
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_navigate_to_vehicle
import paparcar.composeapp.generated.resources.home_parking_edit_menu_cd
import paparcar.composeapp.generated.resources.home_parking_leave_release
import paparcar.composeapp.generated.resources.home_peek_car_parked_label
import paparcar.composeapp.generated.resources.home_peek_parking_duration_hm
import paparcar.composeapp.generated.resources.home_peek_parking_duration_min
import paparcar.composeapp.generated.resources.home_peek_vehicle_parked_label

// ═════════════════════════════════════════════════════════════════════════════
// ParkingPeek — the user's selected active session. [HOME-ATOMIZE-001 F3]
// ═════════════════════════════════════════════════════════════════════════════

@Composable
internal fun ParkingPeek(
    parking: UserParking,
    vehicle: Vehicle?,
    userGps: GpsPoint?,
    onIntent: (HomeIntent) -> Unit,
    onAction: (HomeSheetAction) -> Unit,
) {
    val distM = userGps?.let { g ->
        distanceMeters(g.latitude, g.longitude, parking.location.latitude, parking.location.longitude)
    }
    val title = peekTitle(
        placeName = parking.placeInfo?.name,
        addressLine = parking.address?.displayLine,
        lat = parking.location.latitude,
        lon = parking.location.longitude,
    )
    // The peek's accent is the vehicle's identity colour — its watch method (green = active
    // detection, blue = BT, grey = unwatched). "Parked" is state and stays neutral text.
    // [UI-COLOR-DOCTRINE-001]
    val accentColor = vehicleIdentityColor(vehicle?.monitoringStatus()?.watch() ?: VehicleWatch.Off)
    val vehicleName = vehicleSummary(vehicle)
    val headerLabel = if (vehicleName != null) {
        stringResource(Res.string.home_peek_vehicle_parked_label, vehicleName)
    } else {
        stringResource(Res.string.home_peek_car_parked_label)
    }

    PapSheet(
        lead = PapSheetLead.Vehicle(
            carbody = vehicle?.carbodyType,
            size = vehicle?.sizeCategory,
            color = vehicle?.color,
            loading = vehicle == null,
        ),
        eyebrow = headerLabel,
        // Only the NAME wears the identity colour (watch method); the state words around it stay
        // neutral — "TOYOTA COROLLA (verde/azul) · APARCADO (onSurface)". [UI-COLOR-DOCTRINE-001]
        eyebrowColor = MaterialTheme.colorScheme.onSurfaceVariant,
        eyebrowHighlight = vehicleName,
        eyebrowHighlightColor = accentColor,
        title = title,
        onDismiss = { onIntent(HomeIntent.SelectItem(null)) },
        meta = {
            DistanceRow(distanceM = distM, mode = TravelMode.WALKING, accentColor = accentColor)
            ParkingDurationRow(timestampMs = parking.location.timestamp, accentColor = accentColor)
        },
        // Two twin round utilities, grouped on the meta row: navigate to the car (external
        // intent) and edit. Both low-emphasis — matching circles read as a pair — so the footer
        // below is left to "Me voy" alone. Directions leads (the more frequent reach), edit stays
        // anchored far-right. [UX-PARKED-STATE-001]
        metaAction = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(META_ACTIONS_GAP_DP.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Directions diamond — universally read as "navigate"; opens the user's
                // default maps app via a geo: intent. [UX-PARKED-STATE-001]
                PapSheetRoundIconButton(
                    icon = Icons.Rounded.Directions,
                    contentDescription = stringResource(Res.string.home_navigate_to_vehicle),
                    onClick = {
                        onAction(
                            HomeSheetAction.NavigateExternal(
                                lat = parking.location.latitude,
                                lon = parking.location.longitude,
                                walking = true,
                            ),
                        )
                    },
                )
                // Edit enters the pin-positioning sheet, where correct / re-park / delete are all
                // decided at confirm — they share the same map tool, so the choice is made there
                // (pin already placed) instead of guessing up front. [UX-PARKED-STATE-001]
                PapSheetRoundIconButton(
                    icon = Icons.Rounded.EditLocationAlt,
                    contentDescription = stringResource(Res.string.home_parking_edit_menu_cd),
                    onClick = {
                        onIntent(
                            HomeIntent.EnterAddParkingMode(
                                initialGps = parking.location,
                                editingParkingId = parking.id,
                            ),
                        )
                    },
                )
            }
        },
        actions = {
            // TimeToLeave (a car front, literally "time to go") — Logout read as account
            // sign-out, the wrong metaphor for freeing a spot. Sole full-width action = the
            // ONE move that advances the community loop. [UX-PARKED-STATE-001]
            PapFooterButton(
                label = stringResource(Res.string.home_parking_leave_release),
                leadingIcon = Icons.Rounded.TimeToLeave,
                onClick = { onAction(HomeSheetAction.RequestRelease(parking.id)) },
                style = PapFooterButtonStyle.Filled,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
internal fun ParkingDurationRow(timestampMs: Long, accentColor: Color) {
    if (timestampMs <= 0L) return
    // Live clock so the parked-duration counter ticks up on screen. [SPOT-TTL-LIVE-001]
    val nowMs = rememberNowMinuteTick()
    val elapsedMin = ((nowMs - timestampMs) / MS_PER_MINUTE)
        .toInt().coerceAtLeast(0)
    val durationText = if (elapsedMin < 60) {
        stringResource(Res.string.home_peek_parking_duration_min, elapsedMin)
    } else {
        stringResource(Res.string.home_peek_parking_duration_hm, elapsedMin / 60, elapsedMin % 60)
    }
    PeekMetaRow(icon = Icons.Rounded.Schedule, text = durationText, tint = accentColor)
}

private const val META_ACTIONS_GAP_DP = 8
