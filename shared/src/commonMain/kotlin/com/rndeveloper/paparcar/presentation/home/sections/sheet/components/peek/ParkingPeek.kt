package com.rndeveloper.paparcar.presentation.home.sections.sheet.components.peek

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.EditLocationAlt
import androidx.compose.material.icons.rounded.AccessTimeFilled
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.presentation.home.HomeIntent
import com.rndeveloper.paparcar.presentation.home.PeekStep
import com.rndeveloper.paparcar.presentation.home.sections.sheet.HomeSheetAction
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheet
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheetLead
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheetRoundIconButton
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheetRoundButtonSize
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheetStepper
import com.rndeveloper.paparcar.presentation.util.distanceMeters
import com.rndeveloper.paparcar.ui.components.PapFooterButton
import com.rndeveloper.paparcar.ui.components.PapFooterButtonStyle
import com.rndeveloper.paparcar.domain.model.monitoringStatus
import com.rndeveloper.paparcar.ui.theme.VehicleWatch
import com.rndeveloper.paparcar.ui.theme.vehicleIdentityColor
import com.rndeveloper.paparcar.ui.theme.watch
import com.rndeveloper.paparcar.ui.theme.PapGap
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_navigate_to_vehicle_cd
import paparcar.composeapp.generated.resources.home_parking_edit_menu_cd
import paparcar.composeapp.generated.resources.home_parking_leave_release
import paparcar.composeapp.generated.resources.home_parking_view_vehicle
import paparcar.composeapp.generated.resources.home_peek_car_parked_label
import paparcar.composeapp.generated.resources.home_peek_parking_duration_day
import paparcar.composeapp.generated.resources.home_peek_parking_duration_days
import paparcar.composeapp.generated.resources.home_peek_parking_duration_hm
import paparcar.composeapp.generated.resources.home_peek_parking_duration_min
import paparcar.composeapp.generated.resources.home_peek_parking_duration_now
import paparcar.composeapp.generated.resources.home_peek_step_next_car
import paparcar.composeapp.generated.resources.home_peek_step_prev_car
import paparcar.composeapp.generated.resources.home_peek_vehicle_parked_label
import paparcar.composeapp.generated.resources.location_fallback_parking

// ═════════════════════════════════════════════════════════════════════════════
// ParkingPeek — the user's selected active session. [HOME-ATOMIZE-001 F3]
// ═════════════════════════════════════════════════════════════════════════════

@Composable
internal fun ParkingPeek(
    parking: UserParking,
    vehicle: Vehicle?,
    userGps: GpsPoint?,
    /** Neighbouring VEHICLES (parked or not) — the header ‹ / ›. Carries vehicle ids: an unparked
     *  neighbour opens its add-parking peek instead of a session. Empty with a single vehicle, and
     *  then the peek ends at "Me voy" exactly as before.
     *  [MULTI-PARKING-001] [UI-PEEK-STEPS-WALK-VEHICLES-NOT-SESSIONS-001] */
    step: PeekStep,
    onIntent: (HomeIntent) -> Unit,
    onAction: (HomeSheetAction) -> Unit,
) {
    val distM = userGps?.let { g ->
        distanceMeters(g.latitude, g.longitude, parking.location.latitude, parking.location.longitude)
    }
    val title = peekTitle(
        placeName = parking.placeInfo?.name,
        addressLine = parking.address?.displayLine,
        fallback = stringResource(Res.string.location_fallback_parking),
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
        eyebrow = headerLabel.uppercase(),
        // Only the NAME wears the identity colour (watch method); the state words around it stay
        // neutral — "TOYOTA COROLLA (verde/azul) · APARCADO (onSurface)". [UI-COLOR-DOCTRINE-001]
        eyebrowColor = MaterialTheme.colorScheme.onSurfaceVariant,
        eyebrowHighlight = vehicleName,
        eyebrowHighlightColor = accentColor,
        title = title,
        onDismiss = { onIntent(HomeIntent.SelectItem(null)) },
        // With a second car, going from one to the other used to mean closing this peek and
        // hunting for the other marker or chip. Same chrome as the spot peek and the history
        // detail. [UI-PEEK-STEPS-BETWEEN-PINS-001] [UI-PEEK-STEPS-WALK-VEHICLES-NOT-SESSIONS-001]
        stepper = PapSheetStepper(
            prevContentDescription = stringResource(Res.string.home_peek_step_prev_car),
            nextContentDescription = stringResource(Res.string.home_peek_step_next_car),
            onPrev = step.prevId?.let { id -> { onAction(HomeSheetAction.StepToVehicle(id)) } },
            onNext = step.nextId?.let { id -> { onAction(HomeSheetAction.StepToVehicle(id)) } },
        ),
        meta = {
            DistanceRow(distanceM = distM, mode = TravelMode.WALKING, accentColor = accentColor)
            ParkingDurationRow(timestampMs = parking.location.timestamp, accentColor = accentColor)
            // Last row, and only when the session is an AREA: the distance above is measured to the
            // centre of a circle we are not sure about, so the caveat has to follow it, not precede
            // it. [UI-APPROXIMATE-PARKING-DRAWS-ITS-DOUBT-001]
            // Tappable: its own copy promises "edit to place it exactly", so one tap keeps that
            // promise — same flow as the round edit button. Validating the place turns the zone
            // into a user-placed exact pin. [SPOT-A-ZONE-PARK-FREES-A-ZONE-SPOT-001]
            ApproximateZoneRow(
                zoneRadiusMeters = parking.zoneRadiusMeters,
                accentColor = accentColor,
                onAdjust = {
                    onIntent(
                        HomeIntent.EnterAddParkingMode(
                            initialGps = parking.location,
                            editingParkingId = parking.id,
                        ),
                    )
                },
            )
        },
        // Two twin round utilities, grouped on the meta row: navigate to the car (external
        // intent) and edit. Both low-emphasis — matching circles read as a pair — so the footer
        // below is left to "Me voy" alone. Directions leads (the more frequent reach), edit stays
        // anchored far-right. [UX-PARKED-STATE-001]
        metaAction = {
            Row(
                // Los dos redondos ya reservan sus 48 de toque, así que el hueco se pide en lo que
                // se VE. ⚠️ Con 38 dp pintados el aire invisible es de 5 por lado: lo mínimo que
                // puede verse son 10, y pedir menos solaparía las áreas de toque.
                // [UI-THE-SPACING-SWEEP-IS-SCREEN-BY-SCREEN-001]
                horizontalArrangement = Arrangement.spacedBy(
                    PapGap.betweenControls(
                        visible = META_ACTIONS_GAP_DP.dp,
                        controlHeight = PapSheetRoundButtonSize,
                    ),
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Directions diamond — universally read as "navigate"; opens the user's
                // default maps app via a geo: intent. [UX-PARKED-STATE-001]
                PapSheetRoundIconButton(
                    icon = Icons.Rounded.Directions,
                    contentDescription = stringResource(Res.string.home_navigate_to_vehicle_cd),
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
            // ⛔ La flecha que SALE, no un morro de coche. `TimeToLeave` llegó aquí para desbancar
            // a `Logout` (que se leía como cerrar sesión), y era mejor — pero en cuanto debajo
            // aparece «Ver vehículo», el coche pasa a ser el icono de ESE botón y arriba quedaban
            // dos coches diciendo cosas opuestas. Irse es un movimiento, y se dibuja como tal.
            //
            // La flecha SUELTA, no `Outbound`: aquel metía la flecha en un disco sólido y sobre el
            // relleno verde se leía como un botón dentro del botón (visto en device, 15-09). Aquí
            // el icono ya está pidiendo permiso para existir —la política del componente es
            // texto solo [UI-BUTTON-ICONS-EARN-THEIR-PLACE-001]—, así que lo mínimo que se puede
            // dibujar es lo que le toca.
            // [UI-A-PARKED-CARD-OFFERS-THE-WAY-ACROSS-001] [UX-PARKED-STATE-001]
            //
            // El Filled sigue siendo UNO: la única acción que hace avanzar el bucle comunitario
            // (tu aparcamiento libera una plaza). [COPY-SPOT-IS-NOT-A-PARKING-001]
            PapFooterButton(
                label = stringResource(Res.string.home_parking_leave_release),
                leadingIcon = Icons.Rounded.ArrowOutward,
                onClick = { onAction(HomeSheetAction.RequestRelease(parking.id)) },
                style = PapFooterButtonStyle.Filled,
                modifier = Modifier.fillMaxWidth(),
            )
            // Ver el coche — su ficha, su historial, sus figuras. Hasta ahora había que cerrar el
            // modal, cambiar de pestaña y buscar el coche a mano; el modal SABE de qué coche habla.
            // `Outlined` es exactamente lo que `PapFooterButtonStyle` define como la hermana
            // relevante que NO avanza el bucle. [UI-A-PARKED-CARD-OFFERS-THE-WAY-ACROSS-001]
            //
            // Sin coche atado no hay ficha que abrir, y el botón no aparece: un botón que lleva a
            // ninguna parte es peor que su ausencia.
            parking.vehicleId?.let { vehicleId ->
                Spacer(Modifier.height(ACTIONS_GAP_DP.dp))
                PapFooterButton(
                    label = stringResource(Res.string.home_parking_view_vehicle),
                    leadingIcon = Icons.Rounded.DirectionsCar,
                    onClick = { onAction(HomeSheetAction.OpenVehicle(vehicleId)) },
                    style = PapFooterButtonStyle.Outlined,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
    val elapsedHours = elapsedMin / MINUTES_PER_HOUR
    val elapsedDays = elapsedHours / HOURS_PER_DAY
    val durationText = when {
        // A counter at zero says nothing — the first minute reads like a person would say it.
        // [UI-JUST-PARKED-READS-AS-NOW-001]
        elapsedMin < 1 -> stringResource(Res.string.home_peek_parking_duration_now)
        elapsedMin < MINUTES_PER_HOUR -> stringResource(Res.string.home_peek_parking_duration_min, elapsedMin)
        elapsedHours < HOURS_PER_DAY ->
            stringResource(Res.string.home_peek_parking_duration_hm, elapsedHours, elapsedMin % MINUTES_PER_HOUR)
        // ⛔ Pasado un día, los minutos dejan de ser un dato y el contador pasaba a decir cosas como
        // «hace 50h 45m», que nadie lee como "anteayer". La escala sube a días, con el mismo corte
        // singular/plural que usa el resto de la app para "hace cuánto".
        elapsedDays == 1 -> stringResource(Res.string.home_peek_parking_duration_day, elapsedDays)
        else -> stringResource(Res.string.home_peek_parking_duration_days, elapsedDays)
    }
    PeekMetaRow(icon = Icons.Rounded.AccessTimeFilled, text = durationText, tint = accentColor)
}

private const val MINUTES_PER_HOUR = 60
private const val HOURS_PER_DAY = 24

private const val META_ACTIONS_GAP_DP = 8
/** Aire entre los dos botones del pie — el mismo que ya separa los del modal de aparcar. */
private const val ACTIONS_GAP_DP = 8
