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
import androidx.compose.material.icons.rounded.LocationOff
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
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheetTrailing
import com.rndeveloper.paparcar.presentation.util.PlaceLine
import com.rndeveloper.paparcar.ui.icons.PaparcarIcons
import com.rndeveloper.paparcar.presentation.util.compactRelativeTimeText
import com.rndeveloper.paparcar.presentation.util.distanceMeters
import com.rndeveloper.paparcar.presentation.util.distanceString
import com.rndeveloper.paparcar.presentation.util.placeLineText
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
import paparcar.composeapp.generated.resources.home_browse_hint_swipe_report
import paparcar.composeapp.generated.resources.home_browse_parked_ago
import paparcar.composeapp.generated.resources.home_browse_parked_meta
import paparcar.composeapp.generated.resources.home_det_ask_eyebrow
import paparcar.composeapp.generated.resources.home_det_monitoring
import paparcar.composeapp.generated.resources.home_location_off_badge
import paparcar.composeapp.generated.resources.home_peek_car_parked_label
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
    drivingMeta: DrivingMeta?,
    drivingVehicle: Vehicle?,
    drivingPlace: PlaceLine?,
    drivingSecondaryLine: String?,
    /** [DET-ASK-STATE-001] A "did you park?" question is open — the phase eyebrow asks it. */
    isAwaitingAnswer: Boolean,
    cameraInfo: AddressAndPlace?,
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
     * The user has opened Browse at least once: from then on the COLLAPSED header also speaks for the
     * lane instead of the parked car. A live trip still outranks it (see the driving branch).
     * [HOME-PEEK-FOLLOWS-THE-LANE-ONCE-OPENED-001]
     */
    laneHeaderLatched: Boolean = false,
    /** Non-null while Home warns that location is missing (`warnsLocationMissing`): every browse
     *  header wears the warning badge, and tapping it runs this. [HOME-LOCATION-WARNING-IS-A-BADGE-NOT-A-MODAL-001] */
    onFixLocation: (() -> Unit)? = null,
) {
    // Whether this header speaks for the LANE: always when expanded, and collapsed too once Browse
    // has been opened. [HOME-PEEK-FOLLOWS-THE-LANE-ONCE-OPENED-001]
    val followsLane = showZoneHeader || laneHeaderLatched
    // One badge for the three subjects below — the car on the move, the parked car and the zone:
    // missing location is true of all of them.
    val locationWarning = onFixLocation?.let { onClick ->
        PapSheetTrailing.Warning(
            label = stringResource(Res.string.home_location_off_badge).uppercase(),
            icon = Icons.Rounded.LocationOff,
            onClick = onClick,
        )
    }

    // ── Subject = the car being driven RIGHT NOW (monitored trip) ─────────────
    // Collapsed peek only. A live trip OUTRANKS any parked car — the vehicle being actively
    // monitored is the one standing in for the user, same hierarchy the readiness already follows
    // (Monitoring > Parked). The live phase reads in the eyebrow — EN RUTA while driving,
    // APARCANDO… once it stops and the detector is confirming a spot — and the address follows the
    // moving car via the camera geocode. This is where the removed floating "monitoring" pill's
    // status now lives. [DET-STATUS-SHEET-001] [UI-BROWSE-DRIVING-OVER-PARKED-001]
    //
    // ⛔ Deliberately NOT released by `laneHeaderLatched`: a live trip (and the "did you park?"
    // question it carries) is news, not the first-launch summary of your car — and collapsed, this
    // eyebrow is the only place Home says it. [HOME-PEEK-FOLLOWS-THE-LANE-ONCE-OPENED-001]
    if (drivingMeta != null && !showZoneHeader) {
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
            trailing = locationWarning,
        )
        return
    }

    // ── Subject = the parked car (collapsed peek, no live trip, Browse never opened) ──
    // Title/sub come from THE SESSION — static. The camera must never drag your parked
    // car around ("one car parked in two places"). Expanded browse hands the header to
    // the zone below: the car's info lives in its TUS VEHÍCULOS card. [UI-SHEET-004]
    // Once Browse has been opened, the collapsed peek keeps the lane too.
    // [HOME-PEEK-FOLLOWS-THE-LANE-ONCE-OPENED-001]
    if (parking != null && !followsLane) {
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
            trailing = locationWarning,
        )
        return
    }

    // ── Subject = the lane (no parked car, no live trip, expanded browse, or Browse opened once) ──
    // Show skeleton when there is no displayable content — covers:
    //  • info still null (initial load before first geocode)
    //  • geocoding in flight with no previous content
    //  • geocoding finished but address + POI both empty (prevents "unknown address" flash)
    val hasContent = cameraInfo != null && (!cameraInfo.displayLine.isNullOrBlank() || cameraInfo.placeInfo != null)
    if (!hasContent) {
        PeekLocationSkeleton()
        return
    }
    val title = if (cameraInfo.placeInfo != null) cameraInfo.placeInfo.name
                else cameraInfo.displayLine ?: stringResource(Res.string.home_address_unknown)
    // Secondary address line keeps the three zone variants the same height, so the
    // resting peek never changes size under the divider. [BUG-PEEK-DIVIDER-ALIGN]
    val secondaryLine = if (cameraInfo.placeInfo != null) {
        cameraInfo.address.displayLine?.takeIf { it != cameraInfo.placeInfo.name }
    } else {
        listOfNotNull(cameraInfo.address.city, cameraInfo.address.region)
            .joinToString(", ").takeIf { it.isNotEmpty() }
    }
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
        trailing = locationWarning,
    )
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
