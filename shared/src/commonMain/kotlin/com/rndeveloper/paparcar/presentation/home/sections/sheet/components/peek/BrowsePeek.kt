package com.rndeveloper.paparcar.presentation.home.sections.sheet.components.peek

import com.rndeveloper.paparcar.ui.components.PapShimmerBox
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.detection.DetectionPhase
import com.rndeveloper.paparcar.domain.model.AddressAndPlace
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.monitoringStatus
import com.rndeveloper.paparcar.presentation.home.DrivingMeta
import com.rndeveloper.paparcar.presentation.home.model.HomeNearbyLens
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheet
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheetEyebrowTone
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheetLead
import com.rndeveloper.paparcar.presentation.util.PlaceLine
import com.rndeveloper.paparcar.ui.icons.PaparcarIcons
import com.rndeveloper.paparcar.presentation.util.compactRelativeTimeText
import com.rndeveloper.paparcar.presentation.util.distanceMeters
import com.rndeveloper.paparcar.presentation.util.distanceString
import com.rndeveloper.paparcar.presentation.util.placeLineText
import com.rndeveloper.paparcar.presentation.util.secondaryPlaceLine
import com.rndeveloper.paparcar.presentation.util.toPlaceLine
import com.rndeveloper.paparcar.ui.components.rememberDrivingStatePulse
import com.rndeveloper.paparcar.ui.theme.VehicleWatch
import com.rndeveloper.paparcar.ui.theme.vehicleIdentityColor
import com.rndeveloper.paparcar.ui.theme.watch
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_address_unknown
import paparcar.composeapp.generated.resources.home_browse_eyebrow_zone
import paparcar.composeapp.generated.resources.subject_fuel_stations
import paparcar.composeapp.generated.resources.subject_public_parkings
import paparcar.composeapp.generated.resources.subject_free_spots
import paparcar.composeapp.generated.resources.home_browse_hint_swipe_mark_parking
import paparcar.composeapp.generated.resources.home_browse_hint_swipe_report
import paparcar.composeapp.generated.resources.home_browse_parked_ago
import paparcar.composeapp.generated.resources.home_browse_parked_meta
import paparcar.composeapp.generated.resources.home_det_ask_eyebrow
import paparcar.composeapp.generated.resources.home_det_monitoring
import paparcar.composeapp.generated.resources.home_peek_car_parked_label
import paparcar.composeapp.generated.resources.home_peek_parking_unmarked_title
import paparcar.composeapp.generated.resources.home_peek_vehicle_parked_label
import paparcar.composeapp.generated.resources.home_peek_vehicle_status
import paparcar.composeapp.generated.resources.home_vehicle_chip_status_candidate
import paparcar.composeapp.generated.resources.home_vehicle_fallback_name
import paparcar.composeapp.generated.resources.location_approximate_near
import paparcar.composeapp.generated.resources.location_fallback_parking

// ═════════════════════════════════════════════════════════════════════════════
// BrowsePeek — the default browse header (parked car / live trip / zone).
// [HOME-ATOMIZE-001 F3]
// ═════════════════════════════════════════════════════════════════════════════

// Horizontal inset of the Browse address row + its loading skeleton — the 16dp sheet grid.
private const val BROWSE_ROW_HORIZONTAL_PAD_DP = 16

/**
 * @param parkingVehicle the vehicle of [parking] (resolved by the orchestrator).
 * @param drivingVehicle the vehicle of [drivingMeta] (resolved by the orchestrator).
 * @param drivingPlace where the live trip IS — the car's own geocode, not the camera's.
 * @param drivingSecondaryLine the street/city under [drivingPlace].
 */
@Composable
internal fun BrowsePeek(
    parking: UserParking?,
    parkingVehicle: Vehicle?,
    /** The car Home speaks for when there is NO session to name — resolved by the orchestrator, the
     *  same "active vehicle" every other cold-start control targets. Null when the user has no
     *  vehicle yet: then the lane keeps the header, because there is nothing to ask for.
     *  [HOME-AN-UNPARKED-CAR-STILL-HAS-SOMETHING-TO-SAY-001] */
    unparkedVehicle: Vehicle?,
    drivingMeta: DrivingMeta?,
    drivingVehicle: Vehicle?,
    drivingPlace: PlaceLine?,
    drivingSecondaryLine: String?,
    /** [DET-ASK-STATE-001] A "did you park?" question is open — the phase eyebrow asks it. */
    isAwaitingAnswer: Boolean,
    cameraInfo: AddressAndPlace?,
    /**
     * La pregunta por el punto que mira la cámara está EN VUELO — o la cámara todavía se mueve.
     * Es lo ÚNICO que autoriza a esta cabecera a dibujar un esqueleto: una respuesta vacía es una
     * respuesta, y se escribe. [HOME-A-SKELETON-SAYS-WE-ARE-ASKING-001]
     */
    isCameraSettling: Boolean,
    userGpsPoint: GpsPoint?,
    freeCount: Int,
    showZoneHeader: Boolean,
    /** WHICH subject the open sheet is listing — the expanded header speaks for it.
     *  [HOME-THE-WATCH-COMES-FIRST-AND-THE-HEADER-FOLLOWS-THE-LANE-001] */
    nearbyLens: HomeNearbyLens,
    /** How many of that subject, or null when its lane has not been asked yet. */
    laneCount: Int?,
    /** Su carril está preguntando: la cápsula late. */
    laneLoading: Boolean,
    /**
     * The COLLAPSED header speaks for the lane instead of the parked car. Set when the user opens
     * Browse and re-decided when they close it ([nextLaneHeaderLatch]) — never from the live count
     * while collapsed. A live trip still outranks it (see the driving branch).
     * [HOME-PEEK-FOLLOWS-THE-LANE-ONCE-OPENED-001]
     * [HOME-THE-SUBJECT-IS-CHOSEN-WHEN-THE-SHEET-CLOSES-001]
     */
    laneHeaderLatched: Boolean = false,
) {
    // Whether this header speaks for the LANE: always when expanded, and collapsed too while the
    // latch holds. ⛔ The latch is NOT recomputed here from the live count — quién habla se decide
    // AL PLEGAR, ver [nextLaneHeaderLatch]. [HOME-PEEK-FOLLOWS-THE-LANE-ONCE-OPENED-001]
    // [HOME-THE-SUBJECT-IS-CHOSEN-WHEN-THE-SHEET-CLOSES-001]
    val followsLane = showZoneHeader || laneHeaderLatched
    // ⛔ The missing-location warning is NOT here any more. It was a trailing badge on all three
    // subjects, and «SIN UBICACIÓN» in caps ate the width this header needs to NAME things — the
    // car, the address, the lane. It now lives in the map's location pill, which was standing
    // silent at exactly that moment: without a fix there is no accuracy to report.
    // [UI-NO-LOCATION-WEARS-THE-ACCURACY-PILL-001]
    //
    // ⛔ Y por eso NINGUNA de las tres ramas de abajo pasa `onDismiss`: browse es el estado BASE de
    // Home — el sitio al que vuelven los demás—, así que no tiene nada que cerrar. Al mudarse el
    // badge, el hueco del trailing quedó libre y `PapSheet` colaba ahí su ✕ por defecto, cableada a
    // un `onDismiss` vacío: un botón de cerrar que no cerraba nada. Hoy la ✕ nace de la lambda, así
    // que no pasarla ES no tener salida. [UI-A-CLOSE-X-MUST-CLOSE-SOMETHING-001]

    // ── Sujeto = un COCHE ─────────────────────────────────────────────────────
    // ⛔ Las tres ramas de coche viven en composables propios CUYA FIRMA NO VE `cameraInfo`. No es
    // organización del fichero: es el invariante hecho estructura. *Si el sujeto de la cabecera es
    // un COCHE, el título nombra al coche — nunca a la cámara.* Esa regla se rompió TRES veces en
    // esta misma cabecera —el viaje vivo [UI-A-LIVE-TRIP-NAMES-ITS-PLACE-001], el coche aparcado
    // [UI-SHEET-004] («un coche aparcado en dos sitios») y el coche sin marcar
    // [HOME-AN-UNMARKED-CAR-HAS-NO-ADDRESS-001]— y las tres veces el arreglo fue el mismo: quitarle
    // la cámara. Escrita como comentario se saltó tres veces; escrita como firma hay que ampliarla
    // a propósito para volver a saltársela.
    //
    // `cameraInfo` se queda donde su sujeto SÍ es la cámara: la cabecera del carril, al final.

    if (drivingMeta != null && !showZoneHeader) {
        DrivingCarHeader(
            drivingVehicle = drivingVehicle,
            drivingMeta = drivingMeta,
            isAwaitingAnswer = isAwaitingAnswer,
            drivingPlace = drivingPlace,
            drivingSecondaryLine = drivingSecondaryLine,
        )
        return
    }

    if (parking != null && !followsLane) {
        ParkedCarHeader(
            parking = parking,
            parkingVehicle = parkingVehicle,
            userGpsPoint = userGpsPoint,
        )
        return
    }

    if (!followsLane && unparkedVehicle != null) {
        UnmarkedCarHeader(vehicle = unparkedVehicle)
        return
    }

    // ── Subject = the lane (no car to speak for, no live trip, expanded browse, or Browse opened once) ──
    // ⛔ **Un esqueleto dice «te estoy preguntando esto AHORA MISMO», y sólo la pregunta en vuelo
    // puede decirlo.** Esto se dibujaba a partir de «no tengo nada que enseñar», que es un estado
    // SIN SALIDA: sin red el geocodificador CONTESTA igualmente —fase 1 vacía, sin vecino en caché
    // que prestar— y esta cabecera latía para siempre, con el carril de debajo ya respondido y la
    // conexión restablecida hacía rato. La diferencia entre «contestó una nada» y «no contestó
    // nadie» ya es doctrina en el repositorio que produce esta respuesta
    // [POI-A-FAILED-LOOKUP-IS-NOT-AN-ANSWER-001]; la única de las cuatro cabeceras de peek que no la
    // aplicaba era esta. [HOME-A-SKELETON-SAYS-WE-ARE-ASKING-001]
    //
    // Contestada y sin nombre → la cabecera con el fallback honesto, que es exactamente lo que hacen
    // los peeks de modo-pin al asentarse (`cameraTitleWhileSettling`). Y el nombre sale del
    // resolutor COMPARTIDO, no de una tercera copia de la misma cadena de fallbacks: dos copias son
    // dos formas de nombrar el mismo punto, y de regalo la aproximación se declara «Cerca de X» aquí
    // también. [UI-A-LIVE-TRIP-NAMES-ITS-PLACE-001] [GEO-CACHE-ANSWERS-NEARBY-001]
    if (browseHeaderIsStillAsking(cameraInfo, isCameraSettling)) {
        PeekLocationSkeleton()
        return
    }
    val title = placeLineText(cameraInfo.toPlaceLine()) ?: stringResource(Res.string.home_address_unknown)
    // Secondary address line keeps the three zone variants the same height, so the
    // resting peek never changes size under the divider. [BUG-PEEK-DIVIDER-ALIGN]
    val secondaryLine = cameraInfo.secondaryPlaceLine()
    // ⛔ El sujeto de esta cabecera es el CARRIL, no siempre la zona.
    // [HOME-THE-WATCH-COMES-FIRST-AND-THE-HEADER-FOLLOWS-THE-LANE-001]
    //
    // Medido en device el 10-09-2026: en el carril de Parkings (4 resultados) esta cabecera seguía
    // diciendo «TU ZONA · 6 LIBRES». Dos cifras de dos sujetos distintos a 300 dp una de otra, que es
    // el mismo fallo que [A-SECTION-ANSWERS-ABOUT-WHERE-YOU-LOOK-001] arregló en el mapa — allí la
    // capa preguntaba por la cámara y la sección por tu GPS. La lección de aquel ticket era mirar QUÉ
    // ANCLA usa la cabecera; aquí usaba el sujeto equivocado.
    //
    // El TÍTULO no cambia: dónde estás mirando es verdad para los tres sujetos. Lo que sigue al
    // carril son el eyebrow y la cifra.
    //
    // ⛔ El nombre del sujeto se toma de las MISMAS claves que rotulan su pestaña (`subject_*`), no
    // de unas nuevas: la cabecera y la pestaña que la produjo tienen que decir la misma palabra, y
    // dos juegos de strings para un nombre son dos sitios donde se puede escribir distinto.
    val isSpotsLane = !followsLane || nearbyLens == HomeNearbyLens.SPOTS
    val hasAny = (if (isSpotsLane) freeCount else laneCount ?: 0) > 0

    PapSheet(
        lead = if (isSpotsLane) {
            PapSheetLead.SpotCounter(freeCount)
        } else {
            PapSheetLead.NearbyCounter(
                count = laneCount,
                isLoading = laneLoading,
                icon = when (nearbyLens) {
                    HomeNearbyLens.PARKINGS -> PaparcarIcons.CarParkMark
                    else -> PaparcarIcons.Fuel
                },
            )
        },
        eyebrow = stringResource(
            when {
                isSpotsLane -> Res.string.subject_free_spots
                nearbyLens == HomeNearbyLens.PARKINGS -> Res.string.subject_public_parkings
                else -> Res.string.subject_fuel_stations
            },
        ).uppercase(),
        eyebrowTone = if (hasAny) PapSheetEyebrowTone.Action else PapSheetEyebrowTone.Neutral,
        title = title,
        // Collapsed, on the spots lane, with 0 spots: the sub is the activation hint; otherwise the
        // address line.
        subtitle = if (isSpotsLane && freeCount == 0 && !showZoneHeader) {
            stringResource(Res.string.home_browse_hint_swipe_report)
        } else {
            secondaryLine
        },
    )
}

// ── Subject = the car being driven RIGHT NOW (monitored trip) ─────────────
// Collapsed peek only. A live trip OUTRANKS any parked car — the vehicle being actively
// monitored is the one standing in for the user, same hierarchy the readiness already follows
// (Monitoring > Parked). The live phase reads in the eyebrow — EN RUTA while driving,
// APARCANDO… once it stops and the detector is confirming a spot — and the address follows the
// moving car via ITS OWN geocode (⛔ no la de la cámara: ver `drivingPlace` abajo). This is where
// the removed floating "monitoring" pill's status now lives.
// [DET-STATUS-SHEET-001] [UI-BROWSE-DRIVING-OVER-PARKED-001]
//
// ⛔ Deliberately NOT released by `laneHeaderLatched`: a live trip (and the "did you park?"
// question it carries) is news, not the first-launch summary of your car — and collapsed, this
// eyebrow is the only place Home says it. [HOME-PEEK-FOLLOWS-THE-LANE-ONCE-OPENED-001]
@Composable
private fun DrivingCarHeader(
    drivingVehicle: Vehicle?,
    drivingMeta: DrivingMeta,
    isAwaitingAnswer: Boolean,
    drivingPlace: PlaceLine?,
    drivingSecondaryLine: String?,
) {
    val vehicleName = vehicleSummary(drivingVehicle) ?: stringResource(Res.string.home_vehicle_fallback_name)
    val isCandidate = drivingMeta.phase == DetectionPhase.Candidate
    // Reuse the already-translated phase words (same as the old pill / vehicle chip) so the eyebrow
    // stays i18n-complete without new per-locale strings. [DET-STATUS-SHEET-001]
    //
    // [DET-ASK-STATE-001] An OPEN question outranks both phase words: while the sheet is closed
    // this eyebrow is the only voice Home has, and "APARCANDO…" reads as "sit back, I'm working"
    // at the exact moment the app is waiting on the user. The answer itself lives one drag away,
    // in the row the expanded sheet renders — the peek's job is to say there IS something to
    // answer, not to grow a third pair of buttons.
    val phaseWord = stringResource(
        when {
            isAwaitingAnswer -> Res.string.home_det_ask_eyebrow
            isCandidate -> Res.string.home_vehicle_chip_status_candidate
            else -> Res.string.home_det_monitoring
        },
    )
    // [UI-A-LIVE-TRIP-NAMES-ITS-PLACE-001] The subject here is the CAR, so the address comes
    // from the car — its own geocode, resolved by the slice. It used to come from the camera,
    // which reads right only while the camera happens to be following the trip: pan the map
    // mid-drive and this header calmly told you your car was on the street you were looking at,
    // with the car's name next to it. That is the driving twin of the "one car parked in two
    // places" bug [UI-SHEET-004] already fixed for the parked branch below. The camera keeps
    // exactly the subject it owns: the zone header at the bottom of this file.
    val title = placeLineText(drivingPlace) ?: stringResource(Res.string.home_address_unknown)
    val secondaryLine = drivingSecondaryLine
    val identity = vehicleIdentityColor(drivingVehicle?.monitoringStatus()?.watch() ?: VehicleWatch.Off)
    PapSheet(
        lead = PapSheetLead.Vehicle(
            carbody = drivingVehicle?.carbodyType,
            size = drivingVehicle?.sizeCategory,
            color = drivingVehicle?.color,
            loading = drivingVehicle == null,
            // The trip is live, so the tile breathes — the same halo the vehicle chip shows one
            // surface below. Without it the same trip looked alive in the chip and dead here.
            // [UI-PEEK-DRIVING-HAS-NO-MOTION-001]
            drivingHaloColor = identity,
        ),
        eyebrow = stringResource(Res.string.home_peek_vehicle_status, vehicleName, phaseWord).uppercase(),
        // Only the NAME wears the identity colour (watch method); the phase word ("EN RUTA" /
        // "APARCANDO…") is state and stays neutral — and, being state in motion, it BREATHES.
        // The pulse rides the base eyebrow colour, so it reaches the phase words while the
        // name's own span keeps its opaque identity colour: no new API, and the vehicle never
        // blinks. [UI-COLOR-DOCTRINE-001] [UI-PEEK-DRIVING-HAS-NO-MOTION-001]
        eyebrowColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = rememberDrivingStatePulse()),
        eyebrowHighlight = vehicleName,
        eyebrowHighlightColor = identity,
        title = title,
        subtitle = secondaryLine,
        // No free-spots pill in the collapsed peek: the count already reads once the sheet is
        // expanded (spots section header), so a trailing pill here just duplicates it.
    )
}

// ── Subject = the parked car (collapsed peek, no live trip, Browse never opened) ──
// Title/sub come from THE SESSION — static. The camera must never drag your parked
// car around ("one car parked in two places"). Expanded browse hands the header to
// the zone below: the car's info lives in its TUS VEHÍCULOS card. [UI-SHEET-004]
// Once Browse has been opened, the collapsed peek keeps the lane too.
// [HOME-PEEK-FOLLOWS-THE-LANE-ONCE-OPENED-001]
@Composable
private fun ParkedCarHeader(
    parking: UserParking,
    parkingVehicle: Vehicle?,
    userGpsPoint: GpsPoint?,
) {
    val vehicleName = vehicleSummary(parkingVehicle)
    val eyebrow = if (vehicleName != null) {
        stringResource(Res.string.home_peek_vehicle_parked_label, vehicleName)
    } else {
        stringResource(Res.string.home_peek_car_parked_label)
    }
    // [UI-APPROXIMATE-ZONE-IN-HISTORY-001] An approximate session reads "Near X" here too:
    // this collapsed header has no ApproximateZoneRow below it (that lives in the parking
    // peek), so the title itself must not claim a point the session refused to claim. The
    // fallback stays plain — "Parking" names nothing exact to soften.
    val resolvedTitle = peekTitle(
        placeName = parking.placeInfo?.name,
        addressLine = parking.address?.displayLine,
        fallback = "",
    ).takeIf { it.isNotBlank() }
    val title = when {
        resolvedTitle != null && parking.isApproximate ->
            stringResource(Res.string.location_approximate_near, resolvedTitle)
        resolvedTitle != null -> resolvedTitle
        else -> stringResource(Res.string.location_fallback_parking)
    }
    val distM = userGpsPoint?.let {
        distanceMeters(it.latitude, it.longitude, parking.location.latitude, parking.location.longitude)
    }
    val subtitle = if (parking.location.timestamp > 0L) {
        val ago = compactRelativeTimeText(parking.location.timestamp)
        if (distM != null) {
            stringResource(Res.string.home_browse_parked_meta, ago, distanceString(distM))
        } else {
            stringResource(Res.string.home_browse_parked_ago, ago)
        }
    } else null
    PapSheet(
        lead = PapSheetLead.Vehicle(
            carbody = parkingVehicle?.carbodyType,
            size = parkingVehicle?.sizeCategory,
            color = parkingVehicle?.color,
            // Session's vehicle still resolving from Room → skeleton, not the generic fallback car.
            loading = parkingVehicle == null,
        ),
        eyebrow = eyebrow.uppercase(),
        // Only the NAME wears the vehicle's identity colour; "· APARCADO" is state and stays
        // neutral. [UI-COLOR-DOCTRINE-001]
        eyebrowColor = MaterialTheme.colorScheme.onSurfaceVariant,
        eyebrowHighlight = vehicleName,
        eyebrowHighlightColor = vehicleIdentityColor(parkingVehicle?.monitoringStatus()?.watch() ?: VehicleWatch.Off),
        title = title,
        subtitle = subtitle,
        // No free-spots pill in the collapsed peek: the count already reads once the sheet is
        // expanded (spots section header), so a trailing pill here just duplicates it.
    )
}

// ── Subject = your car, NOT parked yet ────────────────────────────────────
// The header is the car's (the lane had nothing to show when the sheet closed) but there is no
// session to name. It still speaks: it says WHOSE car it is, that it is not marked, and what to
// do about it. Falling through to the lane here would leave Home's resting line on a counter the
// closing already judged empty — and would say nothing about the one action that is always
// available. [HOME-AN-UNPARKED-CAR-STILL-HAS-SOMETHING-TO-SAY-001]
//
// Tres líneas, tres cosas distintas: el eyebrow dice QUIÉN, el título dice QUÉ le pasa, el sub dice
// CÓMO se arregla.
//
// ⛔ No button, and the copy SAYS SO — same shape the spots lane already uses at zero: the sub is
// an activation hint («Desliza ↑ …»), not an imperative that looks tappable. The peek handle's
// tap belongs to the sheet, so an imperative in the title («Marcar aparcamiento», que es lo que
// esta cabecera ponía cuando la cámara no tenía nombre) promete una pulsación que hace otra cosa.
// The hint names the gesture that actually works, and one drag up is where the control lives.
// [UI-SHEET-003]
@Composable
private fun UnmarkedCarHeader(vehicle: Vehicle) {
    val vehicleName = vehicleSummary(vehicle) ?: stringResource(Res.string.home_vehicle_fallback_name)
    PapSheet(
        lead = PapSheetLead.Vehicle(
            carbody = vehicle.carbodyType,
            size = vehicle.sizeCategory,
            color = vehicle.color,
        ),
        // El eyebrow se queda con el SUJETO y suelta el estado, que ahora es el título: si las tres
        // líneas dicen «sin marcar», «sin aparcamiento marcado» y «marcar tu aparcamiento», la
        // cabecera repite una cosa tres veces en lugar de decir tres.
        eyebrow = vehicleName.uppercase(),
        // Todo el eyebrow ES el nombre, así que lo viste entero la identidad de vigilancia — no hay
        // estado teñido aquí, que es lo que la doctrina prohíbe. [UI-COLOR-DOCTRINE-001]
        eyebrowColor = MaterialTheme.colorScheme.onSurfaceVariant,
        eyebrowHighlight = vehicleName,
        eyebrowHighlightColor = vehicleIdentityColor(vehicle.monitoringStatus().watch()),
        // ⛔ NO la calle de la cámara. Un coche sin marcar no tiene un «dónde» —eso es exactamente
        // lo que le pasa—, así que el único sitio disponible era el de OTRO sujeto: bajo el nombre
        // del coche, y con la misma anatomía que la rama de arriba (glifo + nombre + calle), se
        // leía como su dirección, y panear el mapa lo «movía» de calle sin tocarlo.
        // [HOME-AN-UNMARKED-CAR-HAS-NO-ADDRESS-001]
        //
        // El título dice el ESTADO, que es la noticia real del coche y no cambia con la cámara.
        // Vocabulario: lo que falta por marcar es TU APARCAMIENTO, no una plaza — la plaza es de la
        // comunidad, y por eso el hint hermano (avisar de una plaza) NO puede decir lo mismo.
        // [COPY-SPOT-IS-NOT-A-PARKING-001]
        title = stringResource(Res.string.home_peek_parking_unmarked_title),
        subtitle = stringResource(Res.string.home_browse_hint_swipe_mark_parking),
    )
}


/**
 * Does the lane deserve to keep the collapsed header, asked **at the moment the sheet closes**?
 *
 * `false` = it has nothing to show and there is a car to hand it to, so the car takes the header.
 * The resting peek — the only line Home says when nobody is looking at it — must not be a subject
 * sitting over a zero while the car stays silent one drag away.
 * [HOME-AN-EMPTY-LANE-HANDS-THE-HEADER-BACK-TO-THE-CAR-001]
 *
 * Two cautions the rule needs to stay honest:
 *  - **Asking is not empty** ([laneLoading]): a lane mid-question has not answered zero yet.
 *  - **A car to hand it to is not the same as a PARKED car** ([hasCarToSpeakFor]): an unparked one
 *    has plenty to say — whose it is, that it is not marked, and to go mark it — so it takes the
 *    header just the same. What leaves the lane holding it is having NO vehicle at all: then browse's
 *    base subject is the free-spots counter, exactly what the un-latched header already shows, and
 *    handing over would swap a quiet lane for a different lane rather than for a car.
 *    [HOME-AN-UNPARKED-CAR-STILL-HAS-SOMETHING-TO-SAY-001]
 *
 * ⛔ This is asked ONCE per closing, never frame by frame: see [nextLaneHeaderLatch].
 */
/**
 * ¿Sigue esta cabecera ESPERANDO el nombre del punto que mira la cámara — lo único que la autoriza a
 * dibujar un esqueleto? [HOME-A-SKELETON-SAYS-WE-ARE-ASKING-001]
 *
 * Dos mitades, y la segunda es la que faltaba:
 *  - **no hay nombre** que enseñar (`toPlaceLine`, el mismo resolutor que nombra el punto), y
 *  - **la pregunta sigue viva**: la cámara moviéndose o el geocodificador en vuelo.
 *
 * ⛔ Sin la segunda, «no tengo nombre» era un estado sin salida. Sin red el geocodificador contesta
 * igual —fase 1 vacía, sin vecino que prestar—, así que la cabecera latía para siempre encima de un
 * carril que ya había respondido. Una respuesta sin nombre es una respuesta: se escribe
 * («Dirección desconocida»), no se disimula con una animación de estar trabajando.
 */
internal fun browseHeaderIsStillAsking(cameraInfo: AddressAndPlace?, isCameraSettling: Boolean): Boolean =
    cameraInfo.toPlaceLine() == null && isCameraSettling

internal fun laneKeepsHeaderOnCollapse(
    laneCount: Int?,
    laneLoading: Boolean,
    hasCarToSpeakFor: Boolean,
): Boolean {
    // Nothing to show = it answered zero, or nobody asked it (`null`) — and it is not asking now.
    // ⛔ Null is still not zero: the lane losing the header is NOT the same as printing a `0` on a
    // subject no source ever counted. [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
    val laneHasNothingToShow = !laneLoading && (laneCount ?: 0) == 0
    return !(laneHasNothingToShow && hasCarToSpeakFor)
}

/**
 * What [nextLaneHeaderLatch] reads, bundled so the one effect that owns the latch watches a single
 * value. [HOME-THE-SUBJECT-IS-CHOSEN-WHEN-THE-SHEET-CLOSES-001]
 *
 * @param browseOpen the sheet sits beyond peek in PURE Browse. Automatic openings count; a marker
 *   tap's expand is a detail opening, not Browse.
 * @param laneKeepsHeader [laneKeepsHeaderOnCollapse] over the CURRENT lane — only read on closing.
 */
internal data class LaneHeaderLatchInputs(
    val browseOpen: Boolean,
    val laneKeepsHeader: Boolean,
)

/**
 * WHO heads the collapsed peek, decided **on the two gestures that bracket browsing** — opening the
 * sheet and closing it — and never in between.
 *
 * | Gesture | Subject |
 * |---|---|
 * | opens Browse | the lane: you asked for its list |
 * | closes it, lane with results | the lane keeps it |
 * | closes it, lane empty + car parked | the car |
 *
 * Collapsed, the subject is FROZEN; only its content keeps moving. That is the whole point:
 *
 *  - The count is LIVE. Spots appear and expire under a parked car; car parks and fuel re-ask on
 *    camera moves ([NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001]), and the spots query re-centres past a
 *    300 m pan. A subject recomputed from that count changes under a phone nobody is touching.
 *  - **And panning into an empty block must NOT summon the car.** You are following the lane across
 *    the map; the honest answer there is its own zero — "nothing here" — not a different subject
 *    swapped in under your thumb. The car comes back the next time you open and close the sheet,
 *    which is you asking again.
 *
 * [HOME-THE-SUBJECT-IS-CHOSEN-WHEN-THE-SHEET-CLOSES-001] [HOME-PEEK-FOLLOWS-THE-LANE-ONCE-OPENED-001]
 *
 * ⛔ A latch that is already `false` stays there on closing: the car has the header because Browse
 * was never opened (the base state), or because a previous closing gave it back. Re-asking would let
 * a lane that refilled on its own steal it without the user ever opening the sheet.
 */
internal fun nextLaneHeaderLatch(
    latched: Boolean,
    browseOpen: Boolean,
    laneKeepsHeader: Boolean,
): Boolean = when {
    browseOpen -> true
    latched -> laneKeepsHeader
    else -> false
}

@Composable
private fun PeekLocationSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Mirrors the browse header's inset so the skeleton doesn't jump on load.
            .padding(horizontal = BROWSE_ROW_HORIZONTAL_PAD_DP.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PapShimmerBox(modifier = Modifier.size(26.dp), shape = CircleShape)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PapShimmerBox(
                modifier = Modifier.fillMaxWidth(0.62f).height(14.dp),
                shape = RoundedCornerShape(7.dp),
            )
            PapShimmerBox(
                modifier = Modifier.fillMaxWidth(0.38f).height(10.dp),
                shape = RoundedCornerShape(5.dp),
                alphaScale = 0.7f,
            )
        }
        PapShimmerBox(
            modifier = Modifier.size(width = 56.dp, height = 26.dp),
            shape = RoundedCornerShape(8.dp),
            alphaScale = 0.7f,
        )
    }
}
