package com.rndeveloper.paparcar.presentation.home.sections.sheet.components.peek

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.AccessTimeFilled
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.parkings.FeeInfo
import com.rndeveloper.paparcar.domain.usecase.parkings.NearbyPublicParking
import com.rndeveloper.paparcar.presentation.home.HomeIntent
import com.rndeveloper.paparcar.presentation.home.PeekStep
import com.rndeveloper.paparcar.presentation.home.model.publicParkingKindLabel
import com.rndeveloper.paparcar.presentation.home.sections.sheet.HomeSheetAction
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheet
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheetEyebrowTone
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheetLead
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheetStepper
import com.rndeveloper.paparcar.presentation.util.distanceMeters
import com.rndeveloper.paparcar.ui.components.PapFooterButton
import com.rndeveloper.paparcar.ui.components.PapFooterButtonStyle
import com.rndeveloper.paparcar.ui.icons.PaparcarIcons
import com.rndeveloper.paparcar.ui.theme.PapColor
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.common_directions
import paparcar.composeapp.generated.resources.parking_capacity
import paparcar.composeapp.generated.resources.parking_fee_free
import paparcar.composeapp.generated.resources.parking_fee_paid
import paparcar.composeapp.generated.resources.parking_max_height
import paparcar.composeapp.generated.resources.parking_peek_eyebrow
import paparcar.composeapp.generated.resources.parking_peek_step_next
import paparcar.composeapp.generated.resources.parking_peek_step_prev

// ═════════════════════════════════════════════════════════════════════════════
// PublicParkingPeek — the selected car park, from the map layer or the list.
// [PARKINGS-ON-THE-MAP-001]
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Everything the source ACTUALLY said about this car park, and not one line more.
 *
 * ⛔ Each fact is rendered only where it exists — measured coverage says most of them usually do
 * not: `capacity` 1-16%, `maxheight` 0-7%, `opening_hours` 0-3%, `fee` 19-29%. So the body of this
 * peek is short by design, and its shortness is the honest answer rather than a missing feature.
 * Inventing "free" out of an untagged fee would send a driver into a barrier.
 *
 * Geometry belongs to [PapSheet] untouched: no inner scroll, one-line title. Distance is always in
 * DRIVING mode — you do not walk to a car park, you arrive at one.
 */
@Composable
internal fun PublicParkingPeek(
    nearby: NearbyPublicParking,
    userLocation: Pair<Double, Double>?,
    step: PeekStep,
    onIntent: (HomeIntent) -> Unit,
    onAction: (HomeSheetAction) -> Unit,
) {
    val parking = nearby.parking
    val distM = userLocation?.let { (uLat, uLon) ->
        distanceMeters(uLat, uLon, parking.latitude, parking.longitude)
    }
    val kindLabel = publicParkingKindLabel(parking.kind)
    // Titled by its kind when nobody named it — the common case outside big cities.
    val title = parking.displayName ?: kindLabel

    PapSheet(
        // La misma P que el marcador que acabas de tocar: un parking público se dibuja igual en el
        // mapa, en la fila, en la pestaña y aquí. [UI-THE-CAR-PARK-WEARS-OUR-OWN-P-001]
        lead = PapSheetLead.GenericIcon(
            icon = PaparcarIcons.CarParkMark,
            container = PapColor.carPark,
            tint = PapColor.onCarPark,
        ),
        eyebrow = stringResource(Res.string.parking_peek_eyebrow).uppercase(),
        eyebrowTone = PapSheetEyebrowTone.Action,
        title = title,
        // The kind under the name — unless the title already IS the kind.
        subtitle = kindLabel.takeIf { it != title },
        onDismiss = { onIntent(HomeIntent.SelectItem(null)) },
        // ‹ / › walk the car parks in the list's own order, through the SAME action a row or a
        // capsule fires — the same stepper the spot and fuel peeks use.
        // [CARPARK-DETAIL-HAS-PREV-NEXT-CLOSE-001]
        stepper = PapSheetStepper(
            prevContentDescription = stringResource(Res.string.parking_peek_step_prev),
            nextContentDescription = stringResource(Res.string.parking_peek_step_next),
            onPrev = step.prevId?.let { id -> { onAction(HomeSheetAction.SelectPublicParking(id)) } },
            onNext = step.nextId?.let { id -> { onAction(HomeSheetAction.SelectPublicParking(id)) } },
        ),
        meta = {
            DistanceRow(
                distanceM = distM,
                mode = TravelMode.DRIVING,
                accentColor = MaterialTheme.colorScheme.primary,
            )
            parking.openingHours?.let { hours ->
                // As the source words it — "24/7", "Mo-Sa 08:00-22:00"… never parsed, never judged:
                // a wrong parse closes a car park that is open.
                PeekMetaRow(
                    icon = Icons.Rounded.AccessTimeFilled,
                    text = hours,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        content = {
            // ⛔ UNKNOWN says nothing at all. It is not "free", and it is not "unknown" written out
            // either — a row that exists only to say we do not know is noise in a peek this small.
            when (parking.fee) {
                FeeInfo.PAID -> ParkingFactRow(stringResource(Res.string.parking_fee_paid))
                FeeInfo.FREE -> ParkingFactRow(stringResource(Res.string.parking_fee_free))
                FeeInfo.UNKNOWN -> Unit
            }
            parking.capacity?.let { spaces ->
                // The TOTAL, and it is never dressed as availability: how many are free right now is
                // what `PARKINGS-LIVE-OCCUPANCY-001` will answer, and until then nobody claims it.
                ParkingFactRow(stringResource(Res.string.parking_capacity, spaces.toString()))
            }
            parking.maxHeightMeters?.let { metres ->
                // The one fact a van driver cannot guess from a map.
                ParkingFactRow(stringResource(Res.string.parking_max_height, formatClearance(metres)))
            }
            Spacer(Modifier.height(14.dp))
        },
        actions = {
            PapFooterButton(
                label = stringResource(Res.string.common_directions),
                leadingIcon = Icons.Rounded.Navigation,
                onClick = {
                    onAction(
                        HomeSheetAction.NavigateExternal(
                            lat = parking.latitude,
                            lon = parking.longitude,
                            walking = false,
                        ),
                    )
                },
                style = PapFooterButtonStyle.Filled,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

/** One stated fact, in the reading voice. */
@Composable
private fun ParkingFactRow(text: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = text,
            style = PaparcarType.current.body,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * `2.0` reads as "2 m", `2.1` as "2.1 m" — a clearance is quoted the way the sign at the ramp
 * quotes it, and a trailing `.0` looks like a rounding nobody made.
 */
private fun formatClearance(metres: Double): String {
    val rounded = (metres * 10).toInt()
    return if (rounded % 10 == 0) "${rounded / 10}" else "${rounded / 10}.${rounded % 10}"
}
