package io.apptolast.paparcar.presentation.home.sections.sheet.components.peek

import io.apptolast.paparcar.ui.components.PapShimmerBox
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.detection.DetectionPhase
import io.apptolast.paparcar.domain.model.AddressAndPlace
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.monitoringStatus
import io.apptolast.paparcar.presentation.home.DrivingMeta
import io.apptolast.paparcar.presentation.home.sections.sheet.components.PapSheet
import io.apptolast.paparcar.presentation.home.sections.sheet.components.PapSheetEyebrowTone
import io.apptolast.paparcar.presentation.home.sections.sheet.components.PapSheetLead
import io.apptolast.paparcar.presentation.util.compactRelativeTimeText
import io.apptolast.paparcar.presentation.util.distanceMeters
import io.apptolast.paparcar.presentation.util.distanceString
import io.apptolast.paparcar.ui.components.rememberDrivingStatePulse
import io.apptolast.paparcar.ui.theme.VehicleWatch
import io.apptolast.paparcar.ui.theme.vehicleIdentityColor
import io.apptolast.paparcar.ui.theme.watch
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_address_unknown
import paparcar.composeapp.generated.resources.home_browse_eyebrow_zone
import paparcar.composeapp.generated.resources.home_browse_hint_swipe_report
import paparcar.composeapp.generated.resources.home_browse_parked_ago
import paparcar.composeapp.generated.resources.home_browse_parked_meta
import paparcar.composeapp.generated.resources.home_det_monitoring
import paparcar.composeapp.generated.resources.home_peek_car_parked_label
import paparcar.composeapp.generated.resources.home_peek_vehicle_parked_label
import paparcar.composeapp.generated.resources.home_peek_vehicle_status
import paparcar.composeapp.generated.resources.home_vehicle_chip_status_candidate
import paparcar.composeapp.generated.resources.home_vehicle_fallback_name

// ═════════════════════════════════════════════════════════════════════════════
// BrowsePeek — the default browse header (parked car / live trip / zone).
// [HOME-ATOMIZE-001 F3]
// ═════════════════════════════════════════════════════════════════════════════

// Horizontal inset of the Browse address row + its loading skeleton — the 16dp sheet grid.
private const val BROWSE_ROW_HORIZONTAL_PAD_DP = 16

/**
 * @param parkingVehicle the vehicle of [parking] (resolved by the orchestrator).
 * @param drivingVehicle the vehicle of [drivingMeta] (resolved by the orchestrator).
 */
@Composable
internal fun BrowsePeek(
    parking: UserParking?,
    parkingVehicle: Vehicle?,
    drivingMeta: DrivingMeta?,
    drivingVehicle: Vehicle?,
    cameraInfo: AddressAndPlace?,
    userGpsPoint: GpsPoint?,
    freeCount: Int,
    showZoneHeader: Boolean,
) {
    // ── Subject = the car being driven RIGHT NOW (monitored trip) ─────────────
    // Collapsed peek only. A live trip OUTRANKS any parked car — the vehicle being actively
    // monitored is the one standing in for the user, same hierarchy the readiness already follows
    // (Monitoring > Parked). The live phase reads in the eyebrow — EN RUTA while driving,
    // APARCANDO… once it stops and the detector is confirming a spot — and the address follows the
    // moving car via the camera geocode. This is where the removed floating "monitoring" pill's
    // status now lives. [DET-STATUS-SHEET-001] [UI-BROWSE-DRIVING-OVER-PARKED-001]
    if (drivingMeta != null && !showZoneHeader) {
        val vehicleName = vehicleSummary(drivingVehicle) ?: stringResource(Res.string.home_vehicle_fallback_name)
        val isCandidate = drivingMeta.phase == DetectionPhase.Candidate
        // Reuse the already-translated phase words (same as the old pill / vehicle chip) so the eyebrow
        // stays i18n-complete without new per-locale strings. [DET-STATUS-SHEET-001]
        val phaseWord = stringResource(
            if (isCandidate) Res.string.home_vehicle_chip_status_candidate
            else Res.string.home_det_monitoring,
        )
        val title = cameraInfo?.placeInfo?.name
            ?: cameraInfo?.displayLine?.takeIf { it.isNotBlank() }
            ?: stringResource(Res.string.home_address_unknown)
        val secondaryLine = if (cameraInfo?.placeInfo != null) {
            cameraInfo.address.displayLine?.takeIf { it != cameraInfo.placeInfo.name }
        } else {
            listOfNotNull(cameraInfo?.address?.city, cameraInfo?.address?.region)
                .joinToString(", ").takeIf { it.isNotEmpty() }
        }
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
            eyebrow = stringResource(Res.string.home_peek_vehicle_status, vehicleName, phaseWord),
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
            trailing = null,
        )
        return
    }

    // ── Subject = the parked car (collapsed peek only, no live trip) ──────────
    // Title/sub come from THE SESSION — static. The camera must never drag your parked
    // car around ("one car parked in two places"). Expanded browse hands the header to
    // the zone below: the car's info lives in its TUS VEHÍCULOS card. [UI-SHEET-004]
    if (parking != null && !showZoneHeader) {
        val vehicleName = vehicleSummary(parkingVehicle)
        val eyebrow = if (vehicleName != null) {
            stringResource(Res.string.home_peek_vehicle_parked_label, vehicleName)
        } else {
            stringResource(Res.string.home_peek_car_parked_label)
        }
        val title = peekTitle(
            placeName = parking.placeInfo?.name,
            addressLine = parking.address?.displayLine,
            lat = parking.location.latitude,
            lon = parking.location.longitude,
        )
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
            eyebrow = eyebrow,
            // Only the NAME wears the vehicle's identity colour; "· APARCADO" is state and stays
            // neutral. [UI-COLOR-DOCTRINE-001]
            eyebrowColor = MaterialTheme.colorScheme.onSurfaceVariant,
            eyebrowHighlight = vehicleName,
            eyebrowHighlightColor = vehicleIdentityColor(parkingVehicle?.monitoringStatus()?.watch() ?: VehicleWatch.Off),
            title = title,
            subtitle = subtitle,
            // No free-spots pill in the collapsed peek: the count already reads once the sheet is
            // expanded (spots section header), so a trailing pill here just duplicates it.
            trailing = null,
        )
        return
    }

    // ── Subject = the zone (no parked car, no live trip, or expanded browse) ──
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
    PapSheet(
        lead = PapSheetLead.SpotCounter(freeCount),
        eyebrow = stringResource(Res.string.home_browse_eyebrow_zone),
        eyebrowTone = PapSheetEyebrowTone.Neutral,
        title = title,
        // Collapsed with 0 spots: the sub is the activation hint; otherwise the address line.
        subtitle = if (freeCount == 0 && !showZoneHeader) {
            stringResource(Res.string.home_browse_hint_swipe_report)
        } else {
            secondaryLine
        },
        trailing = null,
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
