@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.home.sections.sheet.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditLocationAlt
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.apptolast.paparcar.ui.components.PapDivider
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.model.Zone
import io.apptolast.paparcar.domain.model.ZoneIcon
import io.apptolast.paparcar.domain.model.displayName
import io.apptolast.paparcar.presentation.home.HomeMode
import io.apptolast.paparcar.presentation.home.HomePeekSlice
import io.apptolast.paparcar.presentation.home.model.DetectionUiState
import io.apptolast.paparcar.presentation.util.SpotReliabilityUiState
import io.apptolast.paparcar.presentation.util.compactRelativeTimeText
import io.apptolast.paparcar.presentation.util.distanceMeters
import io.apptolast.paparcar.presentation.util.distanceString
import io.apptolast.paparcar.presentation.util.driveTimeString
import io.apptolast.paparcar.presentation.util.toReliabilityUiState
import io.apptolast.paparcar.presentation.util.walkTimeString
import io.apptolast.paparcar.presentation.util.zoneIconFor
import io.apptolast.paparcar.ui.components.PapClearIconButton
import io.apptolast.paparcar.ui.components.PapSectionHeader
import io.apptolast.paparcar.ui.components.chips.PaparcarFilterChip
import io.apptolast.paparcar.ui.components.ReliabilityMeter
import io.apptolast.paparcar.ui.components.PapFooterButton
import io.apptolast.paparcar.ui.components.PapFooterButtonStyle
import io.apptolast.paparcar.ui.icons.PaparcarIcons
import io.apptolast.paparcar.ui.icons.icon
import io.apptolast.paparcar.ui.theme.PapDriveBlue
import io.apptolast.paparcar.ui.theme.PapMotion
import io.apptolast.paparcar.ui.theme.PaparcarType
import io.apptolast.paparcar.ui.theme.stateColors
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_add_parking_cancel_cd
import paparcar.composeapp.generated.resources.home_add_parking_confirm_create
import paparcar.composeapp.generated.resources.home_add_parking_confirm_edit
import paparcar.composeapp.generated.resources.home_add_parking_header_label_create
import paparcar.composeapp.generated.resources.home_add_parking_header_label_edit
import paparcar.composeapp.generated.resources.home_add_parking_helper_primary_create
import paparcar.composeapp.generated.resources.home_add_parking_helper_primary_edit
import paparcar.composeapp.generated.resources.home_add_parking_helper_secondary
import paparcar.composeapp.generated.resources.home_address_unknown
import paparcar.composeapp.generated.resources.home_browse_eyebrow_zone
import paparcar.composeapp.generated.resources.home_browse_hint_swipe_report
import paparcar.composeapp.generated.resources.home_browse_parked_ago
import paparcar.composeapp.generated.resources.home_browse_parked_meta
import paparcar.composeapp.generated.resources.home_det_monitoring
import paparcar.composeapp.generated.resources.home_navigate_to_spot
import paparcar.composeapp.generated.resources.home_navigate_to_vehicle
import paparcar.composeapp.generated.resources.home_parking_action_move_location
import paparcar.composeapp.generated.resources.home_parking_leave_release
import paparcar.composeapp.generated.resources.home_parking_menu_delete
import paparcar.composeapp.generated.resources.home_spot_gone
import paparcar.composeapp.generated.resources.home_spot_still_there
import paparcar.composeapp.generated.resources.home_peek_car_parked_label
import paparcar.composeapp.generated.resources.home_peek_parking_duration_hm
import paparcar.composeapp.generated.resources.home_peek_parking_duration_min
import paparcar.composeapp.generated.resources.home_peek_spot_age_hour
import paparcar.composeapp.generated.resources.home_peek_spot_age_min
import paparcar.composeapp.generated.resources.home_peek_spot_compat_large
import paparcar.composeapp.generated.resources.home_peek_spot_compat_medium
import paparcar.composeapp.generated.resources.home_peek_spot_compat_moto
import paparcar.composeapp.generated.resources.home_peek_spot_compat_small
import paparcar.composeapp.generated.resources.home_peek_spot_compat_van
import paparcar.composeapp.generated.resources.home_peek_spot_compatible
import paparcar.composeapp.generated.resources.home_peek_spot_en_route
import paparcar.composeapp.generated.resources.home_peek_spot_expires
import paparcar.composeapp.generated.resources.home_peek_spot_high
import paparcar.composeapp.generated.resources.home_peek_spot_incompatible
import paparcar.composeapp.generated.resources.home_peek_spot_low
import paparcar.composeapp.generated.resources.home_peek_spot_manual
import paparcar.composeapp.generated.resources.home_peek_spot_medium
import paparcar.composeapp.generated.resources.home_peek_spot_reliability_label
import paparcar.composeapp.generated.resources.home_peek_spot_size_unknown
import paparcar.composeapp.generated.resources.home_peek_vehicle_parked_label
import paparcar.composeapp.generated.resources.home_peek_vehicle_status
import paparcar.composeapp.generated.resources.home_report_confirm_here
import paparcar.composeapp.generated.resources.home_report_header_label
import paparcar.composeapp.generated.resources.home_report_helper_primary
import paparcar.composeapp.generated.resources.home_report_helper_secondary
import paparcar.composeapp.generated.resources.home_report_size_section
import paparcar.composeapp.generated.resources.home_spot_peek_show_list
import paparcar.composeapp.generated.resources.home_peek_no_spots
import paparcar.composeapp.generated.resources.home_stats_free_spots_badge
import paparcar.composeapp.generated.resources.home_vehicle_chip_status_candidate
import paparcar.composeapp.generated.resources.home_vehicle_fallback_name
import paparcar.composeapp.generated.resources.home_zone_action_delete
import paparcar.composeapp.generated.resources.home_zone_action_edit
import paparcar.composeapp.generated.resources.home_zone_edit_header_label
import paparcar.composeapp.generated.resources.home_zone_header_label
import paparcar.composeapp.generated.resources.home_zone_icon_section
import paparcar.composeapp.generated.resources.home_zone_name_placeholder
import paparcar.composeapp.generated.resources.home_zone_private_badge
import paparcar.composeapp.generated.resources.home_zone_private_hint
import paparcar.composeapp.generated.resources.home_zone_private_label
import paparcar.composeapp.generated.resources.home_zone_radius_meters
import paparcar.composeapp.generated.resources.home_zone_radius_section
import paparcar.composeapp.generated.resources.home_zone_save_action
import paparcar.composeapp.generated.resources.vehicle_size_large
import paparcar.composeapp.generated.resources.vehicle_size_medium
import paparcar.composeapp.generated.resources.vehicle_size_moto
import paparcar.composeapp.generated.resources.vehicle_size_small
import paparcar.composeapp.generated.resources.vehicle_size_unknown
import paparcar.composeapp.generated.resources.vehicle_size_van
import kotlin.math.roundToInt

@Composable
internal fun HomePeekHandle(
    slice: HomePeekSlice,
    /** True while the sheet sits beyond peek — expanded browse swaps to the zone header. [UI-SHEET-004] */
    browseShowsZoneHeader: Boolean = false,
    onDismiss: () -> Unit = {},
    onRelease: () -> Unit = {},
    onAcceptSpot: () -> Unit = {},
    onRejectSpot: () -> Unit = {},
    onDeleteParking: () -> Unit = {},
    onNavigateExternal: (lat: Double, lon: Double, walking: Boolean) -> Unit = { _, _, _ -> },
    onCancelReport: () -> Unit = {},
    onConfirmReport: () -> Unit = {},
    onReportSizeSelected: (VehicleSize?) -> Unit = {},
    onCancelAddZone: () -> Unit = {},
    onConfirmAddZone: () -> Unit = {},
    onUpdateZoneName: (String) -> Unit = {},
    onUpdateZoneIcon: (String) -> Unit = {},
    onZoneRadiusChanged: (Float) -> Unit = {},
    onZoneIsPrivateToggled: (Boolean) -> Unit = {},
    onCancelAddParking: () -> Unit = {},
    onConfirmAddParking: () -> Unit = {},
    onMoveParkingLocation: () -> Unit = {},
    onToggle: () -> Unit = {},
    spotListExpanded: Boolean = false,
    onToggleSpotList: () -> Unit = {},
    /** CORE/GPS blocker CTA — opens the permission flow focused on location. [DET-READY-001n] */
    onActivateLocation: () -> Unit = {},
) {
    val freeCount = slice.freeCount
    val isParkingSelected = slice.isParkingSelected
    val selectedSpot = slice.selectedSpot
    // Under multi-parking pick the *specific* selected session, not just the first active one,
    // so the peek's title, address and actions refer to the vehicle the user actually tapped.
    val parkingToShow = slice.selectedSession

    val peekState: PeekState = when {
        slice.mode is HomeMode.AddingParking ->
            PeekState.AddingParking(isEditing = slice.editingParkingId != null)
        slice.mode is HomeMode.Reporting -> PeekState.Reporting
        slice.mode is HomeMode.AddingZone -> PeekState.AddingZone
        selectedSpot != null -> PeekState.SelectedSpot(selectedSpot.id)
        parkingToShow != null -> PeekState.SelectedParking(parkingToShow.id)
        else -> PeekState.Browse
    }

    Column(modifier = Modifier.fillMaxWidth()) {

        // Drag pill — hidden in the CORE/GPS blocker, where the sheet is static (no drag). [DET-READY-001n]
        if (slice.detectionUiState != DetectionUiState.BlockedCore) {
            Box(
                modifier = Modifier
                    // Glued to the header — no dead air between pill and eyebrow; the header's own
                    // top padding (12dp) is all the breathing room the peek needs. [UI-SHEET-003]
                    .padding(top = 8.dp, bottom = 2.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        CircleShape,
                    )
                    .align(Alignment.CenterHorizontally),
            )
        }

        if (slice.detectionUiState == DetectionUiState.BlockedCore) {
            // Consumer Home can't work without location/GPS — take over the sheet with the full
            // blocker instead of a peek + small surface + redundant header. [DET-READY-001n]
            HomeLocationBlockedState(onActivate = onActivateLocation)
        } else AnimatedContent(
            targetState = peekState,
            transitionSpec = {
                // Explicit duration coordinated with the sheet snap (PapMotion.Emphasized)
                // so the peek content and the sheet move as one piece.
                val incomingEngaged = targetState !is PeekState.Browse
                if (incomingEngaged) {
                    (slideInVertically(PapMotion.emphasized()) { it / 2 } + fadeIn(PapMotion.emphasized())) togetherWith
                        (slideOutVertically(PapMotion.emphasized()) { -it / 2 } + fadeOut(PapMotion.emphasized()))
                } else {
                    (slideInVertically(PapMotion.emphasized()) { -it / 2 } + fadeIn(PapMotion.emphasized())) togetherWith
                        (slideOutVertically(PapMotion.emphasized()) { it / 2 } + fadeOut(PapMotion.emphasized()))
                }
            },
            label = "peek_content",
        ) { target ->
            when (target) {
                is PeekState.SelectedSpot -> {
                    // Resolve live spot from the slice — PeekState only carries the id so
                    // AnimatedContent doesn't transition on Spot data refresh. [BUG-PEEK-JITTER-001]
                    val spot = slice.nearbySpots.firstOrNull { it.id == target.spotId }
                    if (spot != null) {
                        SpotPeekRow(
                            spot = spot,
                            userLocation = slice.userGpsPoint?.let { Pair(it.latitude, it.longitude) },
                            activeVehicle = slice.vehicles.firstOrNull { it.isActive },
                            onDismiss = onDismiss,
                            onNavigate = {
                                onNavigateExternal(spot.location.latitude, spot.location.longitude, false)
                            },
                            onAcceptSpot = onAcceptSpot,
                            onRejectSpot = onRejectSpot,
                            spotListExpanded = spotListExpanded,
                            onToggleSpotList = onToggleSpotList,
                        )
                    }
                }
                is PeekState.SelectedParking -> {
                    val parking = slice.activeSessions.firstOrNull { it.id == target.sessionId }
                    if (parking != null) {
                        ParkingPeekRow(
                            parking = parking,
                            vehicle = slice.vehicles.firstOrNull { it.id == parking.vehicleId },
                            stableRank = slice.parkedVehicles
                                .firstOrNull { it.vehicleId == parking.vehicleId }
                                ?.stableRank,
                            userLocation = slice.userGpsPoint?.let { Pair(it.latitude, it.longitude) },
                            onDismiss = onDismiss,
                            onRelease = onRelease,
                            onWalkToCar = {
                                onNavigateExternal(parking.location.latitude, parking.location.longitude, true)
                            },
                            onMoveLocation = onMoveParkingLocation,
                            onDeleteParking = onDeleteParking,
                        )
                    }
                }
                PeekState.Reporting -> ReportPeekRow(
                    slice = slice,
                    onCancel = onCancelReport,
                    onConfirm = onConfirmReport,
                    onSizeSelected = onReportSizeSelected,
                            )
                PeekState.AddingZone -> AddingZonePeekRow(
                    slice = slice,
                    onCancel = onCancelAddZone,
                    onConfirm = onConfirmAddZone,
                    onNameChange = onUpdateZoneName,
                    onIconChange = onUpdateZoneIcon,
                    onRadiusChange = onZoneRadiusChanged,
                    onIsPrivateToggled = onZoneIsPrivateToggled,
                            )
                is PeekState.AddingParking -> AddingParkingPeekRow(
                    slice = slice,
                    isEditing = target.isEditing,
                    onCancel = onCancelAddParking,
                    onConfirm = onConfirmAddParking,
                    onDelete = onDeleteParking,
                            )
                PeekState.Browse -> CameraLocationRow(
                    slice = slice,
                    freeCount = freeCount,
                    showZoneHeader = browseShowsZoneHeader,
                    onToggle = onToggle,
                )
            }
        }
    }
}

/**
 * Drives [AnimatedContent] in [HomePeekHandle]. Variants store **identity only**
 * (ids and other rarely-changing primitives) — never the underlying domain
 * object — so equality stays stable even when Firestore re-emits the same
 * spot/parking with cosmetic field changes (enRouteCount, expiresAt drift,
 * geocoded address arriving late, etc.). Without this discipline the
 * AnimatedContent would transition on every Spot/UserParking refresh and the
 * sheet would visibly thrash. The content lambda re-reads the live object from
 * the captured peek slice. [BUG-PEEK-JITTER-001]
 */
private sealed class PeekState {
    data class SelectedSpot(val spotId: String) : PeekState()
    data class SelectedParking(val sessionId: String) : PeekState()
    data object Reporting : PeekState()
    data object AddingZone : PeekState()
    data class AddingParking(val isEditing: Boolean) : PeekState()
    data object Browse : PeekState()
}

// ═════════════════════════════════════════════════════════════════════════════
// SpotPeekRow — plaza seleccionada (v1)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun SpotPeekRow(
    spot: Spot,
    userLocation: Pair<Double, Double>?,
    activeVehicle: Vehicle?,
    onDismiss: () -> Unit,
    onNavigate: () -> Unit,
    onAcceptSpot: () -> Unit,
    onRejectSpot: () -> Unit,
    spotListExpanded: Boolean = false,
    onToggleSpotList: () -> Unit = {},
) {
    val reliabilityLevel = spot.toReliabilityUiState()
    val palette = reliabilityLevel.peekPalette()
    val distM = userLocation?.let { (uLat, uLon) ->
        distanceMeters(uLat, uLon, spot.location.latitude, spot.location.longitude)
    }
    // Auto-switch to walking mode when the spot is close enough to walk.
    val travelMode = if (distM != null && distM < WALK_DISTANCE_THRESHOLD_M) TravelMode.WALKING else TravelMode.DRIVING
    val title = peekTitle(
        placeName = spot.placeInfo?.name,
        addressLine = spot.address?.displayLine,
        lat = spot.location.latitude,
        lon = spot.location.longitude,
    )
    // Live clock: re-reads on every whole-minute boundary so the TTL and age labels count down
    // on screen instead of freezing at the value captured on first composition. [SPOT-TTL-LIVE-001]
    val nowMs = rememberNowMinuteTick()
    val ttlMinutes = remainingMinutes(spot.expiresAt, nowMs)
    val spotAgeMin = ageMinutes(spot.location.timestamp, nowMs)

    PapSheet(
        lead = PapSheetLead.CommunitySpot(
            reliability = reliabilityLevel,
            enRouteCount = spot.enRouteCount,
        ),
        eyebrow = palette.label,
        // Reliability tint also rides the eyebrow; the lead puck itself now carries the
        // tier colour/ring, matching the map marker and list row. [HOME-PUCK-001]
        eyebrowColor = palette.badgeBg,
        title = title,
        onDismiss = onDismiss,
        meta = {
            SpotFitRow(spot = spot, vehicle = activeVehicle)
            DistanceRow(distanceM = distM, mode = travelMode, accentColor = palette.badgeBg)
            if (spotAgeMin != null) {
                SpotAgeRow(ageMinutes = spotAgeMin, accentColor = palette.badgeBg)
            }
            if (spot.enRouteCount > 0) {
                SpotEnRouteRow(count = spot.enRouteCount, accentColor = palette.badgeBg)
            }
        },
        content = {
            FiabilityIndicator(level = reliabilityLevel, expiresInMin = ttlMinutes)
            Spacer(Modifier.height(14.dp))
        },
        actions = {
            // Primary = get there before it expires — THE community-loop action here.
            PapFooterButton(
                label = stringResource(Res.string.home_navigate_to_spot),
                leadingIcon = Icons.Rounded.Navigation,
                onClick = onNavigate,
                style = PapFooterButtonStyle.Filled,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            // Signal pair — community feedback, low emphasis (tonal twins). "Still there?"
            // reinforces reliability and keeps the sheet open; "It's gone" rejects + dismisses.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PapFooterButton(
                    label = stringResource(Res.string.home_spot_still_there),
                    leadingIcon = Icons.Rounded.CheckCircle,
                    onClick = onAcceptSpot,
                    style = PapFooterButtonStyle.Tonal,
                    modifier = Modifier.weight(1f),
                )
                PapFooterButton(
                    label = stringResource(Res.string.home_spot_gone),
                    leadingIcon = Icons.Rounded.Block,
                    onClick = onRejectSpot,
                    style = PapFooterButtonStyle.Tonal,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            PapDivider()
            SpotListToggleRow(
                expanded = spotListExpanded,
                label = stringResource(Res.string.home_spot_peek_show_list),
                onClick = onToggleSpotList,
            )
        },
    )
}

@Composable
private fun SpotListToggleRow(
    expanded: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = PapMotion.medium(),
        label = "spot_list_chevron",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            style = PaparcarType.current.body,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = TOGGLE_ROW_ALPHA),
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = TOGGLE_ROW_ALPHA),
            modifier = Modifier.size(18.dp).rotate(rotation),
        )
    }
}

private enum class TravelMode { WALKING, DRIVING }

@Composable
private fun vehicleSummary(vehicle: Vehicle?): String? {
    if (vehicle == null) return null
    val fallback = stringResource(Res.string.home_vehicle_fallback_name)
    return vehicle.displayName(fallback = fallback).takeIf { it.isNotBlank() }
}

/**
 * Canonical peek meta row — accent icon + one SemiBold value line. The four
 * concrete rows (distance, spot age, en-route, parking duration) are thin
 * wrappers over this molde so their visuals can't drift apart. [HOME-VEH-REFINE-001]
 */
@Composable
private fun PeekMetaRow(icon: ImageVector, text: String, tint: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(META_ICON_DP.dp),
        )
        Text(
            text = text,
            // These meta rows ARE the card's primary info, standalone with the full width — the
            // DATA-role precondition (token competing for horizontal space) doesn't hold, so they
            // read in Inter, not condensed. [PEEK-META-INTER-001]
            style = PaparcarType.current.body,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = META_VALUE_ALPHA),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DistanceRow(distanceM: Float?, mode: TravelMode, accentColor: Color) {
    if (distanceM == null) return
    val icon = when (mode) {
        TravelMode.WALKING -> Icons.AutoMirrored.Rounded.DirectionsWalk
        TravelMode.DRIVING -> Icons.Rounded.Navigation
    }
    val timeText = when (mode) {
        TravelMode.WALKING -> walkTimeString(distanceM)
        TravelMode.DRIVING -> driveTimeString(distanceM)
    }
    PeekMetaRow(
        icon = icon,
        text = "${distanceString(distanceM)}$META_SEPARATOR$timeText",
        tint = accentColor,
    )
}

@Composable
private fun FiabilityIndicator(level: SpotReliabilityUiState, expiresInMin: Int?) {
    val cs = MaterialTheme.colorScheme
    val isExpiring = expiresInMin != null && expiresInMin < FIABILITY_EXPIRY_WARN_MIN

    // Label row: section title on the left, TTL text on the right when available.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PapSectionHeader(
            title = stringResource(Res.string.home_peek_spot_reliability_label),
            modifier = Modifier.weight(1f),
        )
        if (expiresInMin != null) {
            Text(
                text = stringResource(Res.string.home_peek_spot_expires, expiresInMin),
                style = PaparcarType.current.label,
                fontWeight = FontWeight.Medium,
                color = if (isExpiring) cs.secondary else cs.onSurface.copy(alpha = 0.55f),
            )
        }
    }
    Spacer(Modifier.height(5.dp))

    // Same canonical 5-segment meter as list/ficha, coloured by reliability tier
    // (verde/ámbar/rojo/azul) — no longer always-green. [IDENTITY-ICONS-001 D]
    ReliabilityMeter(
        level = level,
        fillWidth = true,
        barHeight = FIABILITY_SEG_HEIGHT_DP.dp,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SpotAgeRow(ageMinutes: Int, accentColor: Color) {
    val text = if (ageMinutes < 60)
        stringResource(Res.string.home_peek_spot_age_min, ageMinutes)
    else
        stringResource(Res.string.home_peek_spot_age_hour, ageMinutes / 60)
    PeekMetaRow(icon = Icons.Rounded.Schedule, text = text, tint = accentColor)
}

@Composable
private fun SpotEnRouteRow(count: Int, accentColor: Color) {
    PeekMetaRow(
        icon = Icons.Rounded.Group,
        text = stringResource(Res.string.home_peek_spot_en_route, count),
        tint = accentColor,
    )
}

/**
 * Emits the current epoch-millis and re-emits on every whole-minute boundary, so relative-time
 * labels ("Caduca en N min", "Publicada hace N min") count down live while the peek is visible
 * instead of freezing at the value captured on first composition. [SPOT-TTL-LIVE-001]
 */
@Composable
private fun rememberNowMinuteTick(): Long {
    val nowMs by produceState(initialValue = kotlin.time.Clock.System.now().toEpochMilliseconds()) {
        while (true) {
            val current = kotlin.time.Clock.System.now().toEpochMilliseconds()
            value = current
            // Wait until the next whole minute so the label flips exactly on the boundary.
            kotlinx.coroutines.delay(MS_PER_MINUTE - current % MS_PER_MINUTE)
        }
    }
    return nowMs
}

private fun ageMinutes(timestampMs: Long, nowMs: Long): Int? {
    if (timestampMs <= 0L) return null
    val ageMs = nowMs - timestampMs
    if (ageMs < 0L) return null
    val mins = (ageMs / MS_PER_MINUTE).toInt()
    return if (mins > 0) mins else null
}

private data class SpotPeekPalette(
    val badgeBg: Color,
    val badgeFg: Color,
    val label: String,
)

@Composable
private fun SpotReliabilityUiState.peekPalette(): SpotPeekPalette {
    val sc = stateColors()
    val label = when (this) {
        SpotReliabilityUiState.HIGH   -> stringResource(Res.string.home_peek_spot_high)
        SpotReliabilityUiState.MEDIUM -> stringResource(Res.string.home_peek_spot_medium)
        SpotReliabilityUiState.LOW    -> stringResource(Res.string.home_peek_spot_low)
        SpotReliabilityUiState.MANUAL -> stringResource(Res.string.home_peek_spot_manual)
    }
    return SpotPeekPalette(sc.bg, sc.on, label)
}

private fun remainingMinutes(expiresAtMs: Long, nowMs: Long): Int? {
    if (expiresAtMs <= 0L) return null
    val remaining = ((expiresAtMs - nowMs) / MS_PER_MINUTE).toInt()
    return if (remaining > 0) remaining else null
}

// ═════════════════════════════════════════════════════════════════════════════
// ParkingPeekRow — sesión activa (v1)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun ParkingPeekRow(
    parking: UserParking,
    vehicle: Vehicle?,
    userLocation: Pair<Double, Double>?,
    onDismiss: () -> Unit,
    onRelease: () -> Unit,
    onWalkToCar: () -> Unit,
    onMoveLocation: () -> Unit,
    onDeleteParking: () -> Unit,
    stableRank: Int? = null,
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
    val tone = io.apptolast.paparcar.ui.components.vehicleBadgeTone(
        isParked = true,
        isBluetoothPaired = vehicle?.bluetoothDeviceId != null,
        isActive = true,
    )
    val accentColor = io.apptolast.paparcar.ui.components.vehicleBadgeAccent(tone)
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
        onDismiss = onDismiss,
        meta = {
            DistanceRow(distanceM = distM, mode = TravelMode.WALKING, accentColor = accentColor)
            ParkingDurationRow(timestampMs = parking.location.timestamp, accentColor = accentColor)
        },
        // The edit icon-button jumps straight into the add-parking sheet in edit mode —
        // delete lives THERE as the destructive action. [UI-SHEET-004]
        metaAction = { PapSheetEditButton(onEdit = onMoveLocation) },
        actions = {
            // Primary = leaving IS the action that advances the community loop here:
            // it frees the spot (release dialog: publish / just delete).
            PapFooterButton(
                label = stringResource(Res.string.home_parking_leave_release),
                leadingIcon = Icons.AutoMirrored.Rounded.Logout,
                onClick = onRelease,
                style = PapFooterButtonStyle.Filled,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            // Navigate back to the car — relevant but external intent, so secondary.
            PapFooterButton(
                label = stringResource(Res.string.home_navigate_to_vehicle),
                leadingIcon = Icons.AutoMirrored.Rounded.DirectionsWalk,
                onClick = onWalkToCar,
                style = PapFooterButtonStyle.Outlined,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun ParkingDurationRow(timestampMs: Long, accentColor: Color) {
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


// ═════════════════════════════════════════════════════════════════════════════
// AddingParkingPeekRow — modo "Posicionar aparcamiento" (create + edit)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun AddingParkingPeekRow(
    slice: HomePeekSlice,
    isEditing: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onDelete: () -> Unit,
) {
    val primaryText = cameraTitleWhileSettling(slice)

    // Resolve which vehicle this AddingParking session is FOR so the header
    // shows e.g. "Toyota Corolla" instead of the generic mode label — the user
    // needs to recognise the car when they hit confirm. [MULTI-PARKING-001]
    //  - create: slice.addingParkingVehicleId set by the row tap
    //  - edit:   editingParkingId → activeSessions → vehicleId
    val targetVehicleId = if (isEditing) {
        slice.activeSessions.firstOrNull { it.id == slice.editingParkingId }?.vehicleId
    } else {
        slice.addingParkingVehicleId
    }
    val targetVehicle = targetVehicleId?.let { id -> slice.vehicles.firstOrNull { it.id == id } }
    val fallbackVehicleName = stringResource(Res.string.home_vehicle_fallback_name)
    val genericHeader = if (isEditing) {
        stringResource(Res.string.home_add_parking_header_label_edit)
    } else {
        stringResource(Res.string.home_add_parking_header_label_create)
    }
    val headerLabel = targetVehicle?.displayName(fallback = fallbackVehicleName) ?: genericHeader
    val helperPrimary = if (isEditing) {
        stringResource(Res.string.home_add_parking_helper_primary_edit)
    } else {
        stringResource(Res.string.home_add_parking_helper_primary_create)
    }
    val ctaLabel = if (isEditing) {
        stringResource(Res.string.home_add_parking_confirm_edit)
    } else {
        stringResource(Res.string.home_add_parking_confirm_create)
    }
    // Pre-load the cancel content-description so the IDE catches an
    // unreferenced-string regression early. PeekStateCard.onDismiss is the
    // close affordance — the CD is read by accessibility for that button.
    @Suppress("UnusedExpression") stringResource(Res.string.home_add_parking_cancel_cd)

    // Show the actual car (carbody glyph) being parked, not a generic DirectionsCar so the user
    // recognises the vehicle. The car stays full-colour/opaque regardless of monitoring state — that
    // state reads on its on-map marker border, not by dimming the glyph here. [INACTIVE-OPAQUE-001]
    PapSheet(
        lead = PapSheetLead.Vehicle(
            carbody = targetVehicle?.carbodyType,
            size = targetVehicle?.sizeCategory,
            color = targetVehicle?.color,
        ),
        eyebrow = headerLabel,
        eyebrowTone = PapSheetEyebrowTone.Action,
        title = primaryText,
        onDismiss = onCancel,
        banner = {
            PapSheetBanner(
                title = helperPrimary,
                subtitle = stringResource(Res.string.home_add_parking_helper_secondary),
            )
        },
        actions = {
            PapFooterButton(
                label = ctaLabel,
                leadingIcon = if (isEditing) Icons.Rounded.EditLocationAlt
                              else PaparcarIcons.VehicleCar,
                onClick = onConfirm,
                style = PapFooterButtonStyle.Filled,
                enabled = !slice.isSavingParking && !slice.isCameraMoving,
                isLoading = slice.isSavingParking,
                modifier = Modifier.fillMaxWidth(),
            )
            if (isEditing) {
                Spacer(Modifier.height(8.dp))
                // Editing an existing record also offers deleting it — the one
                // sanctioned destructive red. [UI-SHEET-004]
                PapFooterButton(
                    label = stringResource(Res.string.home_parking_menu_delete),
                    leadingIcon = Icons.Rounded.Delete,
                    onClick = onDelete,
                    style = PapFooterButtonStyle.Outlined,
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.error,
                    enabled = !slice.isSavingParking,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

// ═════════════════════════════════════════════════════════════════════════════
// ReportPeekRow — modo "Avisar plaza libre" (v1)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun ReportPeekRow(
    slice: HomePeekSlice,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onSizeSelected: (VehicleSize?) -> Unit,
) {
    val primaryText = cameraTitleWhileSettling(slice)

    PapSheet(
        lead = PapSheetLead.Announce,
        eyebrow = stringResource(Res.string.home_report_header_label),
        // Manual report = blue, mirroring the manual-spot tint on the map. [UI-SHEET-001]
        eyebrowTone = PapSheetEyebrowTone.Manual,
        title = primaryText,
        onDismiss = onCancel,
        banner = {
            PapSheetBanner(
                title = stringResource(Res.string.home_report_helper_primary),
                subtitle = stringResource(Res.string.home_report_helper_secondary),
            )
        },
        chips = {
            PapSectionHeader(title = stringResource(Res.string.home_report_size_section))
            Spacer(Modifier.height(6.dp))
            SizeChipRow(selected = slice.reportingSize, onSelect = onSizeSelected)
        },
        actions = {
            PapFooterButton(
                label = stringResource(Res.string.home_report_confirm_here),
                leadingIcon = Icons.Rounded.Campaign,
                onClick = onConfirm,
                style = PapFooterButtonStyle.Filled,
                enabled = !slice.isCameraMoving && !slice.isReporting,
                isLoading = slice.isReporting,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun SizeChipRow(selected: VehicleSize?, onSelect: (VehicleSize?) -> Unit) {
    val sizes = VehicleSize.entries

    // One canonical chip for filter bars AND this size selector — selection reads
    // as tinted container + primary accents, never a solid green fill. [UI-SHEET-001]
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "unknown") {
            PaparcarFilterChip(
                label = stringResource(Res.string.vehicle_size_unknown),
                selected = selected == null,
                onClick = { onSelect(null) },
            )
        }
        items(items = sizes, key = { it.name }) { size ->
            val label = stringResource(
                when (size) {
                    VehicleSize.MOTORCYCLE   -> Res.string.vehicle_size_moto
                    VehicleSize.MICRO_SMALL  -> Res.string.vehicle_size_small
                    VehicleSize.MEDIUM_SUV -> Res.string.vehicle_size_medium
                    VehicleSize.LARGE_SEDAN  -> Res.string.vehicle_size_large
                    VehicleSize.VAN_HIGH    -> Res.string.vehicle_size_van
                }
            )
            PaparcarFilterChip(
                label = label,
                selected = size == selected,
                onClick = { onSelect(size) },
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// AddingZonePeekRow — modo "Nueva zona habitual" (v1)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun AddingZonePeekRow(
    slice: HomePeekSlice,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onNameChange: (String) -> Unit,
    onIconChange: (String) -> Unit,
    onRadiusChange: (Float) -> Unit,
    onIsPrivateToggled: (Boolean) -> Unit = {},
) {
    val primaryText = cameraTitleWhileSettling(slice)

    val headerLabel = if (slice.editingZoneId != null) {
        stringResource(Res.string.home_zone_edit_header_label)
    } else {
        stringResource(Res.string.home_zone_header_label)
    }
    val focusManager = LocalFocusManager.current
    PapSheet(
        lead = PapSheetLead.GenericIcon(icon = zoneIconFor(slice.addingZoneIconKey)),
        eyebrow = headerLabel,
        eyebrowTone = PapSheetEyebrowTone.Neutral,
        title = primaryText,
        onDismiss = onCancel,
        content = {
            androidx.compose.material3.OutlinedTextField(
                value = slice.addingZoneName,
                onValueChange = onNameChange,
                placeholder = { Text(stringResource(Res.string.home_zone_name_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                leadingIcon = {
                    Icon(
                        imageVector = zoneIconFor(slice.addingZoneIconKey),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingIcon = if (slice.addingZoneName.isNotEmpty()) {
                    { PapClearIconButton(onClick = { onNameChange("") }) }
                } else null,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            PapSectionHeader(title = stringResource(Res.string.home_zone_icon_section))
            Spacer(Modifier.height(6.dp))
            ZoneIconPickerRow(
                selectedKey = slice.addingZoneIconKey,
                onSelect = onIconChange,
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PapSectionHeader(
                    title = stringResource(Res.string.home_zone_radius_section),
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = stringResource(Res.string.home_zone_radius_meters, slice.addingZoneRadius.roundToInt()),
                    style = PaparcarType.current.label,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = slice.addingZoneRadius,
                onValueChange = onRadiusChange,
                valueRange = Zone.MIN_RADIUS_METERS..Zone.MAX_RADIUS_METERS,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                    Column {
                        Text(
                            text = stringResource(Res.string.home_zone_private_label),
                            style = PaparcarType.current.body,
                        )
                        Text(
                            text = stringResource(Res.string.home_zone_private_hint),
                            style = PaparcarType.current.caption,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECTION_LABEL_ALPHA),
                        )
                    }
                }
                Switch(
                    checked = slice.addingZoneIsPrivate,
                    onCheckedChange = onIsPrivateToggled,
                )
            }
            Spacer(Modifier.height(14.dp))
        },
        actions = {
            PapFooterButton(
                label = stringResource(Res.string.home_zone_save_action),
                leadingIcon = Icons.Rounded.Bookmark,
                onClick = onConfirm,
                style = PapFooterButtonStyle.Filled,
                enabled = slice.addingZoneName.isNotBlank() && !slice.isSavingZone && !slice.isCameraMoving,
                isLoading = slice.isSavingZone,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun ZoneIconPickerRow(
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        // Aligned with the rest of the modal content (the PeekStateCard already
        // insets this row). On scroll the icons clip at the content box. [ZONE-AREA-001]
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = ZoneIcon.PRESETS, key = { it }) { key ->
            val isSelected = key == selectedKey
            Box(
                modifier = Modifier
                    .size(ZONE_ICON_CHIP_DP.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                    .clickable { onSelect(key) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = zoneIconFor(key),
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * Peek-friendly title resolver. Returns place name OR address line, **never**
 * concatenated — the peek/state cards have tight horizontal space and a long
 * "name · address" line truncates ugly mid-word.
 */
@Composable
internal fun peekTitle(
    placeName: String?,
    addressLine: String?,
    lat: Double,
    lon: Double,
): String = placeName?.takeIf { it.isNotBlank() }
    ?: addressLine?.takeIf { it.isNotBlank() }
    ?: io.apptolast.paparcar.presentation.util.formatCoords(lat, lon)

/**
 * Camera-anchored title resolver for the Reporting / AddingZone peek cards.
 * Returns the POI name when the camera sits on a place, the geocoded address
 * line otherwise, and a localized fallback when the camera has no usable
 * location info yet.
 */
@Composable
internal fun cameraTitleOrFallback(
    info: io.apptolast.paparcar.domain.model.AddressAndPlace?,
): String {
    val placeName = info?.placeInfo?.name?.takeIf { it.isNotBlank() }
    if (placeName != null) return placeName
    val addressLine = info?.address?.displayLine?.takeIf { it.isNotBlank() }
    if (addressLine != null) return addressLine
    return stringResource(Res.string.home_address_unknown)
}

/**
 * Like [cameraTitleOrFallback] but returns stale data or "…" while the
 * camera is moving or geocoding, so pin-mode peek cards never flash
 * "unknown address" mid-drag.
 */
@Composable
private fun cameraTitleWhileSettling(slice: HomePeekSlice): String =
    if (slice.isCameraMoving || slice.isCameraGeocoding) {
        slice.cameraAddressAndPlace?.let { info ->
            info.placeInfo?.name?.takeIf { it.isNotBlank() }
                ?: info.address?.displayLine?.takeIf { it.isNotBlank() }
        } ?: "…"
    } else {
        cameraTitleOrFallback(slice.cameraAddressAndPlace)
    }

// ═════════════════════════════════════════════════════════════════════════════
// Default browse row — location header with libres badge
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun CameraLocationRow(
    slice: HomePeekSlice,
    freeCount: Int,
    showZoneHeader: Boolean,
    onToggle: () -> Unit = {},
) {
    val parking = slice.userParking

    // ── Subject = the parked car (collapsed peek only) ────────────────────────
    // Title/sub come from THE SESSION — static. The camera must never drag your parked
    // car around ("one car parked in two places"). Expanded browse hands the header to
    // the zone below: the car's info lives in its TUS VEHÍCULOS card. [UI-SHEET-004]
    if (parking != null && !showZoneHeader) {
        val vehicle = slice.vehicles.firstOrNull { it.id == parking.vehicleId }
        val vehicleName = vehicleSummary(vehicle)
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
        val distM = slice.userGpsPoint?.let {
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
                carbody = vehicle?.carbodyType,
                size = vehicle?.sizeCategory,
                color = vehicle?.color,
            ),
            eyebrow = eyebrow,
            eyebrowTone = PapSheetEyebrowTone.Action,
            title = title,
            subtitle = subtitle,
            // No free-spots pill in the collapsed peek: the count already reads once the sheet is
            // expanded (spots section header), so a trailing pill here just duplicates it.
            trailing = null,
        )
        return
    }

    // ── Subject = the car being driven RIGHT NOW (monitored trip, no session yet) ─
    // Collapsed peek only. The live phase reads in the eyebrow — EN RUTA while driving,
    // APARCANDO… once it stops and the detector is confirming a spot — and the address follows the
    // moving car via the camera geocode. This is where the removed floating "monitoring" pill's
    // status now lives. [DET-STATUS-SHEET-001]
    val drivingMeta = slice.drivingMeta
    if (drivingMeta != null && !showZoneHeader) {
        val vehicle = slice.vehicles.firstOrNull { it.id == drivingMeta.vehicleId }
        val vehicleName = vehicleSummary(vehicle) ?: stringResource(Res.string.home_vehicle_fallback_name)
        val isCandidate = drivingMeta.phase == io.apptolast.paparcar.domain.detection.DetectionPhase.Candidate
        // Reuse the already-translated phase words (same as the old pill / vehicle chip) so the eyebrow
        // stays i18n-complete without new per-locale strings. [DET-STATUS-SHEET-001]
        val phaseWord = stringResource(
            if (isCandidate) Res.string.home_vehicle_chip_status_candidate
            else Res.string.home_det_monitoring,
        )
        val info = slice.cameraAddressAndPlace
        val title = info?.placeInfo?.name
            ?: info?.displayLine?.takeIf { it.isNotBlank() }
            ?: stringResource(Res.string.home_address_unknown)
        val secondaryLine = if (info?.placeInfo != null) {
            info.address.displayLine?.takeIf { it != info.placeInfo.name }
        } else {
            listOfNotNull(info?.address?.city, info?.address?.region)
                .joinToString(", ").takeIf { it.isNotEmpty() }
        }
        PapSheet(
            lead = PapSheetLead.Vehicle(
                carbody = vehicle?.carbodyType,
                size = vehicle?.sizeCategory,
                color = vehicle?.color,
            ),
            eyebrow = stringResource(Res.string.home_peek_vehicle_status, vehicleName, phaseWord),
            // En-route blue while driving, brand green once stopping (candidate) — mirrors the map language.
            eyebrowColor = if (isCandidate) MaterialTheme.colorScheme.primary else PapDriveBlue,
            title = title,
            subtitle = secondaryLine,
            // No free-spots pill here either — it duplicates the count shown in the expanded sheet.
            trailing = null,
        )
        return
    }

    // ── Subject = the zone (no parked car, no live trip, or expanded browse) ──
    val info = slice.cameraAddressAndPlace
    // Show skeleton when there is no displayable content — covers:
    //  • info still null (initial load before first geocode)
    //  • geocoding in flight with no previous content
    //  • geocoding finished but address + POI both empty (prevents "unknown address" flash)
    val hasContent = info != null && (!info.displayLine.isNullOrBlank() || info.placeInfo != null)
    if (!hasContent) {
        PeekLocationSkeleton(onToggle = onToggle)
        return
    }
    val title = if (info.placeInfo != null) info.placeInfo.name
                else info.displayLine ?: stringResource(Res.string.home_address_unknown)
    // Secondary address line keeps the three zone variants the same height, so the
    // resting peek never changes size under the divider. [BUG-PEEK-DIVIDER-ALIGN]
    val secondaryLine = if (info.placeInfo != null) {
        info.address.displayLine?.takeIf { it != info.placeInfo.name }
    } else {
        listOfNotNull(info.address.city, info.address.region)
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
private fun PeekLocationSkeleton(onToggle: () -> Unit = {}) {
    val transition = rememberInfiniteTransition(label = "peek_skeleton")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.40f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = PapMotion.Breathe, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton_pulse",
    )
    val skeletonColor = MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Mirrors CameraLocationRow's inset so the skeleton doesn't jump on load.
            .padding(horizontal = BROWSE_ROW_HORIZONTAL_PAD_DP.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(skeletonColor.copy(alpha = pulseAlpha)),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(skeletonColor.copy(alpha = pulseAlpha)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.38f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(skeletonColor.copy(alpha = pulseAlpha * 0.7f)),
            )
        }
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(skeletonColor.copy(alpha = pulseAlpha * 0.7f)),
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Tokens shared across peek states
// ═════════════════════════════════════════════════════════════════════════════

private const val ZONE_ICON_CHIP_DP = 40
private const val WALK_DISTANCE_THRESHOLD_M = 400f
private const val META_ICON_DP = 18
private const val FIABILITY_SEG_HEIGHT_DP = 4
private const val FIABILITY_EXPIRY_WARN_MIN = 5
private const val MS_PER_MINUTE = 60_000L
// Horizontal inset of the Browse address row + its loading skeleton — the 16dp sheet grid.
private const val BROWSE_ROW_HORIZONTAL_PAD_DP = 16
// Separator between data tokens on a meta line ("80 m  ·  1 min").
private const val META_SEPARATOR = "  ·  "

private const val SECTION_LABEL_ALPHA = 0.55f
private const val META_VALUE_ALPHA = 0.7f
private const val TOGGLE_ROW_ALPHA = 0.55f
