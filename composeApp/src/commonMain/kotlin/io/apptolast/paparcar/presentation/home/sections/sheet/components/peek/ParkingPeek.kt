package io.apptolast.paparcar.presentation.home.sections.sheet.components.peek

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.presentation.home.HomeIntent
import io.apptolast.paparcar.presentation.home.sections.sheet.HomeSheetAction
import io.apptolast.paparcar.presentation.home.sections.sheet.components.PapSheet
import io.apptolast.paparcar.presentation.home.sections.sheet.components.PapSheetEditButton
import io.apptolast.paparcar.presentation.home.sections.sheet.components.PapSheetLead
import io.apptolast.paparcar.presentation.util.distanceMeters
import io.apptolast.paparcar.ui.components.PapFooterButton
import io.apptolast.paparcar.ui.components.PapFooterButtonStyle
import io.apptolast.paparcar.ui.components.vehicleBadgeAccent
import io.apptolast.paparcar.ui.components.vehicleBadgeTone
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_navigate_to_vehicle
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
    userLocation: Pair<Double, Double>?,
    onIntent: (HomeIntent) -> Unit,
    onAction: (HomeSheetAction) -> Unit,
) {
    val distM = userLocation?.let { (uLat, uLon) ->
        distanceMeters(uLat, uLon, parking.location.latitude, parking.location.longitude)
    }
    val title = peekTitle(
        placeName = parking.placeInfo?.name,
        addressLine = parking.address?.displayLine,
        lat = parking.location.latitude,
        lon = parking.location.longitude,
    )
    // Unified semantic tone — a parked car is green (or blue if BT), same as its map marker. [DET-READY-001k]
    val tone = vehicleBadgeTone(
        isParked = true,
        isBluetoothPaired = vehicle?.bluetoothDeviceId != null,
        isActive = true,
    )
    val accentColor = vehicleBadgeAccent(tone)
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
        ),
        eyebrow = headerLabel,
        // Green when parked, drive-blue when BT-paired — same tone as its map marker. [DET-READY-001k]
        eyebrowColor = accentColor,
        title = title,
        onDismiss = { onIntent(HomeIntent.SelectItem(null)) },
        meta = {
            DistanceRow(distanceM = distM, mode = TravelMode.WALKING, accentColor = accentColor)
            ParkingDurationRow(timestampMs = parking.location.timestamp, accentColor = accentColor)
        },
        // The edit icon-button jumps straight into the add-parking sheet in edit mode —
        // delete lives THERE as the destructive action. [UI-SHEET-004]
        metaAction = {
            PapSheetEditButton(
                onEdit = {
                    // Pre-centre on THIS session and tag its id so the confirm updates the
                    // row in place via UpdateParkingLocationUseCase. [MULTI-PARKING-001]
                    onIntent(
                        HomeIntent.EnterAddParkingMode(
                            initialGps = parking.location,
                            editingParkingId = parking.id,
                        ),
                    )
                },
            )
        },
        actions = {
            // Primary = leaving IS the action that advances the community loop here:
            // it frees the spot (release dialog: publish / just delete).
            PapFooterButton(
                label = stringResource(Res.string.home_parking_leave_release),
                leadingIcon = Icons.AutoMirrored.Rounded.Logout,
                onClick = { onAction(HomeSheetAction.RequestRelease) },
                style = PapFooterButtonStyle.Filled,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            // Navigate back to the car — relevant but external intent, so secondary.
            PapFooterButton(
                label = stringResource(Res.string.home_navigate_to_vehicle),
                leadingIcon = Icons.AutoMirrored.Rounded.DirectionsWalk,
                onClick = {
                    onAction(
                        HomeSheetAction.NavigateExternal(
                            lat = parking.location.latitude,
                            lon = parking.location.longitude,
                            walking = true,
                        ),
                    )
                },
                style = PapFooterButtonStyle.Outlined,
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
