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
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditLocationAlt
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.model.Zone
import io.apptolast.paparcar.domain.model.ZoneIcon
import io.apptolast.paparcar.domain.model.displayName
import io.apptolast.paparcar.presentation.home.HomeMode
import io.apptolast.paparcar.presentation.home.HomeState
import io.apptolast.paparcar.presentation.home.model.DetectionUiState
import io.apptolast.paparcar.presentation.util.SpotReliabilityUiState
import io.apptolast.paparcar.presentation.util.distanceMeters
import io.apptolast.paparcar.presentation.util.distanceString
import io.apptolast.paparcar.presentation.util.driveTimeString
import io.apptolast.paparcar.presentation.util.toReliabilityUiState
import io.apptolast.paparcar.presentation.util.walkTimeString
import io.apptolast.paparcar.presentation.util.zoneIconFor
import io.apptolast.paparcar.ui.components.PapClearIconButton
import io.apptolast.paparcar.ui.components.PapFooterButton
import io.apptolast.paparcar.ui.components.PapFooterButtonStyle
import io.apptolast.paparcar.ui.icons.PaparcarIcons
import io.apptolast.paparcar.ui.icons.icon
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
import paparcar.composeapp.generated.resources.home_navigate_to_spot
import paparcar.composeapp.generated.resources.home_navigate_to_vehicle
import paparcar.composeapp.generated.resources.home_parking_action_move_location
import paparcar.composeapp.generated.resources.home_parking_release
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
import paparcar.composeapp.generated.resources.home_peek_spot_occupied
import paparcar.composeapp.generated.resources.home_peek_spot_reliability_label
import paparcar.composeapp.generated.resources.home_peek_spot_size_unknown
import paparcar.composeapp.generated.resources.home_peek_vehicle_parked_label
import paparcar.composeapp.generated.resources.home_report_confirm_here
import paparcar.composeapp.generated.resources.home_report_header_label
import paparcar.composeapp.generated.resources.home_report_helper_primary
import paparcar.composeapp.generated.resources.home_report_helper_secondary
import paparcar.composeapp.generated.resources.home_report_size_section
import paparcar.composeapp.generated.resources.home_spot_peek_show_list
import paparcar.composeapp.generated.resources.home_stats_free_spots_badge
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
    state: HomeState,
    onDismiss: () -> Unit = {},
    onRelease: () -> Unit = {},
    onRejectSpot: () -> Unit = {},
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
    onZoneDismiss: () -> Unit = {},
    onEditZone: (zoneId: String) -> Unit = {},
    onDeleteZone: (zoneId: String) -> Unit = {},
    /** CORE/GPS blocker CTA — opens the permission flow focused on location. [DET-READY-001n] */
    onActivateLocation: () -> Unit = {},
) {
    val freeCount = state.filteredNearbySpots.size
    val isParkingSelected = state.isParkingSelected
    val selectedSpot = state.selectedSpot
    // Under multi-parking pick the *specific* selected session, not just the first active one,
    // so the peek's title, address and actions refer to the vehicle the user actually tapped.
    val parkingToShow = state.selectedSession

    val peekState: PeekState = when {
        state.mode is HomeMode.AddingParking ->
            PeekState.AddingParking(isEditing = state.editingParkingId != null)
        state.mode is HomeMode.Reporting -> PeekState.Reporting
        state.mode is HomeMode.AddingZone -> PeekState.AddingZone
        selectedSpot != null -> PeekState.SelectedSpot(selectedSpot.id)
        parkingToShow != null -> PeekState.SelectedParking(parkingToShow.id)
        state.selectedZone != null -> PeekState.SelectedZone(state.selectedZone!!.id)
        else -> PeekState.Browse
    }

    Column(modifier = Modifier.fillMaxWidth()) {

        // Drag pill — hidden in the CORE/GPS blocker, where the sheet is static (no drag). [DET-READY-001n]
        if (state.detectionUiState != DetectionUiState.BlockedCore) {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 8.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        CircleShape,
                    )
                    .align(Alignment.CenterHorizontally),
            )
        }

        if (state.detectionUiState == DetectionUiState.BlockedCore) {
            // Consumer Home can't work without location/GPS — take over the sheet with the full
            // blocker instead of a peek + small surface + redundant header. [DET-READY-001n]
            HomeLocationBlockedState(onActivate = onActivateLocation)
        } else AnimatedContent(
            targetState = peekState,
            transitionSpec = {
                val incomingEngaged = targetState !is PeekState.Browse
                if (incomingEngaged) {
                    (slideInVertically { it / 2 } + fadeIn()) togetherWith
                        (slideOutVertically { -it / 2 } + fadeOut())
                } else {
                    (slideInVertically { -it / 2 } + fadeIn()) togetherWith
                        (slideOutVertically { it / 2 } + fadeOut())
                }
            },
            label = "peek_content",
        ) { target ->
            when (target) {
                is PeekState.SelectedSpot -> {
                    // Resolve live spot from state — PeekState only carries the id so
                    // AnimatedContent doesn't transition on Spot data refresh. [BUG-PEEK-JITTER-001]
                    val spot = state.nearbySpots.firstOrNull { it.id == target.spotId }
                    if (spot != null) {
                        SpotPeekRow(
                            spot = spot,
                            userLocation = state.userGpsPoint?.let { Pair(it.latitude, it.longitude) },
                            activeVehicle = state.vehicles.firstOrNull { it.isActive },
                            onDismiss = onDismiss,
                            onNavigate = {
                                onNavigateExternal(spot.location.latitude, spot.location.longitude, false)
                            },
                            onRejectSpot = onRejectSpot,
                            spotListExpanded = spotListExpanded,
                            onToggleSpotList = onToggleSpotList,
                        )
                    }
                }
                is PeekState.SelectedParking -> {
                    val parking = state.activeSessions.firstOrNull { it.id == target.sessionId }
                    if (parking != null) {
                        ParkingPeekRow(
                            parking = parking,
                            vehicle = state.vehicles.firstOrNull { it.id == parking.vehicleId },
                            stableRank = state.parkedVehicles
                                .firstOrNull { it.vehicleId == parking.vehicleId }
                                ?.stableRank,
                            userLocation = state.userGpsPoint?.let { Pair(it.latitude, it.longitude) },
                            onDismiss = onDismiss,
                            onRelease = onRelease,
                            onWalkToCar = {
                                onNavigateExternal(parking.location.latitude, parking.location.longitude, true)
                            },
                            onMoveLocation = onMoveParkingLocation,
                        )
                    }
                }
                is PeekState.SelectedZone -> {
                    val zone = state.zones.firstOrNull { it.id == target.zoneId }
                    if (zone != null) {
                        ZonePeekRow(
                            zone = zone,
                            onDismiss = onZoneDismiss,
                            onEdit = { onEditZone(zone.id) },
                            onDelete = { onDeleteZone(zone.id) },
                        )
                    }
                }
                PeekState.Reporting -> ReportPeekRow(
                    state = state,
                    onCancel = onCancelReport,
                    onConfirm = onConfirmReport,
                    onSizeSelected = onReportSizeSelected,
                            )
                PeekState.AddingZone -> AddingZonePeekRow(
                    state = state,
                    onCancel = onCancelAddZone,
                    onConfirm = onConfirmAddZone,
                    onNameChange = onUpdateZoneName,
                    onIconChange = onUpdateZoneIcon,
                    onRadiusChange = onZoneRadiusChanged,
                    onIsPrivateToggled = onZoneIsPrivateToggled,
                            )
                is PeekState.AddingParking -> AddingParkingPeekRow(
                    state = state,
                    isEditing = target.isEditing,
                    onCancel = onCancelAddParking,
                    onConfirm = onConfirmAddParking,
                            )
                PeekState.Browse -> CameraLocationRow(state = state, freeCount = freeCount, onToggle = onToggle)
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
 * the captured `HomeState`. [BUG-PEEK-JITTER-001]
 */
private sealed class PeekState {
    data class SelectedSpot(val spotId: String) : PeekState()
    data class SelectedParking(val sessionId: String) : PeekState()
    data class SelectedZone(val zoneId: String) : PeekState()
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
    onRejectSpot: () -> Unit,
    spotListExpanded: Boolean = false,
    onToggleSpotList: () -> Unit = {},
) {
    val palette = spot.toReliabilityUiState().peekPalette()
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
    val ttlMinutes = remainingMinutes(spot.expiresAt)
    val filledSegments = (palette.fillRatio * FIABILITY_SEGMENTS).roundToInt().coerceIn(1, FIABILITY_SEGMENTS)
    val spotAgeMin = ageMinutes(spot.location.timestamp)

    PeekStateCard(
        headerLabel = palette.label,
        title = title,
        accentColor = palette.badgeBg,
        onDismiss = onDismiss,
        leading = { SpotReliabilityBadge(palette) },
        content = {
            SpotFitRow(spot = spot, vehicle = activeVehicle)
            Spacer(Modifier.height(8.dp))
            DistanceRow(distanceM = distM, mode = travelMode, accentColor = palette.badgeBg)
            if (spotAgeMin != null) {
                Spacer(Modifier.height(8.dp))
                SpotAgeRow(ageMinutes = spotAgeMin, accentColor = palette.badgeBg)
            }
            if (spot.enRouteCount > 0) {
                Spacer(Modifier.height(8.dp))
                SpotEnRouteRow(count = spot.enRouteCount, accentColor = palette.badgeBg)
            }
            Spacer(Modifier.height(12.dp))
            FiabilityIndicator(filledSegments = filledSegments, expiresInMin = ttlMinutes)
            Spacer(Modifier.height(14.dp))
        },
        actions = {
            PapFooterButton(
                label = stringResource(Res.string.home_navigate_to_spot),
                leadingIcon = Icons.Outlined.Navigation,
                onClick = onNavigate,
                style = PapFooterButtonStyle.Filled,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            PapFooterButton(
                label = stringResource(Res.string.home_peek_spot_occupied),
                leadingIcon = Icons.Outlined.Block,
                onClick = onRejectSpot,
                style = PapFooterButtonStyle.Outlined,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
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
        animationSpec = tween(200),
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
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = TOGGLE_ROW_ALPHA),
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = TOGGLE_ROW_ALPHA),
            modifier = Modifier.size(18.dp).rotate(rotation),
        )
    }
}

/**
 * Spot leading chip — same 44dp rounded-square molde as
 * [PeekHeaderIconChip], but with a "P" letter inside (in [palette.badgeFg])
 * and the chip background tinted by reliability palette.
 */
@Composable
private fun SpotReliabilityBadge(
    palette: SpotPeekPalette,
) {
    Box(
        modifier = Modifier
            .size(SPOT_BADGE_DP.dp)
            .clip(CircleShape)
            .background(palette.badgeBg),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.LocalParking,
            contentDescription = null,
            tint = palette.badgeFg,
            modifier = Modifier.size(SPOT_BADGE_ICON_DP.dp),
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

@Composable
private fun DistanceRow(distanceM: Float?, mode: TravelMode, accentColor: Color) {
    if (distanceM == null) return
    val icon = when (mode) {
        TravelMode.WALKING -> Icons.AutoMirrored.Outlined.DirectionsWalk
        TravelMode.DRIVING -> Icons.Outlined.Navigation
    }
    val timeText = when (mode) {
        TravelMode.WALKING -> walkTimeString(distanceM)
        TravelMode.DRIVING -> driveTimeString(distanceM)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(META_ICON_DP.dp),
        )
        Text(
            text = "${distanceString(distanceM)}  ·  $timeText",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = META_VALUE_ALPHA),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FiabilityIndicator(filledSegments: Int, expiresInMin: Int?) {
    val cs = MaterialTheme.colorScheme
    val isExpiring = expiresInMin != null && expiresInMin < FIABILITY_EXPIRY_WARN_MIN

    // Label row: section title on the left, TTL text on the right when available.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.home_peek_spot_reliability_label).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = SECTION_TRACKING_SP.sp,
            color = cs.onSurface.copy(alpha = SECTION_LABEL_ALPHA),
            modifier = Modifier.weight(1f),
        )
        if (expiresInMin != null) {
            Text(
                text = stringResource(Res.string.home_peek_spot_expires, expiresInMin),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = if (isExpiring) cs.secondary else cs.onSurface.copy(alpha = 0.55f),
            )
        }
    }
    Spacer(Modifier.height(5.dp))

    val normalColor = cs.primary
    val warnColor = cs.secondary
    val emptyColor = cs.onSurface.copy(alpha = FIABILITY_EMPTY_ALPHA)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FIABILITY_SEG_GAP_DP.dp),
    ) {
        for (i in 1..FIABILITY_SEGMENTS) {
            val filled = i <= filledSegments
            val isWarnSeg = isExpiring && filled && i > (filledSegments - FIABILITY_EXPIRY_AMBER_SEGS)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(FIABILITY_SEG_HEIGHT_DP.dp)
                    .clip(RoundedCornerShape(FIABILITY_SEG_RADIUS_DP.dp))
                    .background(
                        when {
                            !filled -> emptyColor
                            isWarnSeg -> warnColor
                            else -> normalColor
                        }
                    ),
            )
        }
    }
}

@Composable
private fun SpotAgeRow(ageMinutes: Int, accentColor: Color) {
    val text = if (ageMinutes < 60)
        stringResource(Res.string.home_peek_spot_age_min, ageMinutes)
    else
        stringResource(Res.string.home_peek_spot_age_hour, ageMinutes / 60)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Schedule,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(META_ICON_DP.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = META_VALUE_ALPHA),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SpotEnRouteRow(count: Int, accentColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Group,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(META_ICON_DP.dp),
        )
        Text(
            text = stringResource(Res.string.home_peek_spot_en_route, count),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = META_VALUE_ALPHA),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun ageMinutes(timestampMs: Long): Int? {
    if (timestampMs <= 0L) return null
    val ageMs = kotlin.time.Clock.System.now().toEpochMilliseconds() - timestampMs
    if (ageMs < 0L) return null
    val mins = (ageMs / 60_000L).toInt()
    return if (mins > 0) mins else null
}

private data class SpotPeekPalette(
    val badgeBg: Color,
    val badgeFg: Color,
    val label: String,
    /** 0..1 — how much of the reliability bar to fill (encodes the level visually). */
    val fillRatio: Float,
)

@Composable
private fun SpotReliabilityUiState.peekPalette(): SpotPeekPalette {
    val sc = stateColors()
    val (label, fill) = when (this) {
        SpotReliabilityUiState.HIGH   -> stringResource(Res.string.home_peek_spot_high)   to FILL_HIGH
        SpotReliabilityUiState.MEDIUM -> stringResource(Res.string.home_peek_spot_medium) to FILL_MEDIUM
        SpotReliabilityUiState.LOW    -> stringResource(Res.string.home_peek_spot_low)    to FILL_LOW
        SpotReliabilityUiState.MANUAL -> stringResource(Res.string.home_peek_spot_manual) to FILL_MANUAL
    }
    return SpotPeekPalette(sc.bg, sc.on, label, fill)
}

private const val FILL_HIGH = 1.0f
private const val FILL_MEDIUM = 0.65f
private const val FILL_LOW = 0.35f
private const val FILL_MANUAL = 0.80f

private fun remainingMinutes(expiresAtMs: Long): Int? {
    if (expiresAtMs <= 0L) return null
    val nowMs = kotlin.time.Clock.System.now().toEpochMilliseconds()
    val remaining = ((expiresAtMs - nowMs) / 60_000L).toInt()
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
    val onAccentColor = io.apptolast.paparcar.ui.components.vehicleBadgeOnAccent(tone)
    val vehicleName = vehicleSummary(vehicle)
    val headerLabel = if (vehicleName != null) {
        stringResource(Res.string.home_peek_vehicle_parked_label, vehicleName)
    } else {
        stringResource(Res.string.home_peek_car_parked_label)
    }

    PeekStateCard(
        headerLabel = headerLabel,
        title = title,
        accentColor = accentColor,
        onDismiss = onDismiss,
        leading = {
            ParkedVehicleHeaderChip(
                carbody = vehicle?.carbodyType,
                size = vehicle?.sizeCategory,
                tone = tone,
            )
        },
        content = {
            DistanceRow(distanceM = distM, mode = TravelMode.WALKING, accentColor = accentColor)
            Spacer(Modifier.height(8.dp))
            ParkingDurationRow(timestampMs = parking.location.timestamp, accentColor = accentColor)
            Spacer(Modifier.height(14.dp))
        },
        actions = {
            PapFooterButton(
                label = stringResource(Res.string.home_navigate_to_vehicle),
                leadingIcon = Icons.AutoMirrored.Outlined.DirectionsWalk,
                onClick = onWalkToCar,
                style = PapFooterButtonStyle.Filled,
                containerColor = accentColor,
                contentColor = onAccentColor,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            // Move is a pin-tweak utility — the lightest of the three (outlined neutral) so it
            // recedes below the two real actions (navigate / release). [PEEK-ACTIONS-002]
            PapFooterButton(
                label = stringResource(Res.string.home_parking_action_move_location),
                leadingIcon = Icons.Outlined.EditLocationAlt,
                onClick = onMoveLocation,
                style = PapFooterButtonStyle.Outlined,
                containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = MOVE_OUTLINE_ALPHA),
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            // Releasing the spot is the product's core action — give it strong weight with a solid
            // neutral fill. inverseSurface flips with the theme (dark in light, light in dark) with
            // guaranteed-contrast text, so it's prominent yet clearly distinct from the green
            // primary. Bottom placement is the conventional slot for the "leave" action. [PEEK-ACTIONS-002]
            PapFooterButton(
                label = stringResource(Res.string.home_parking_release),
                leadingIcon = Icons.AutoMirrored.Outlined.Logout,
                onClick = onRelease,
                style = PapFooterButtonStyle.Filled,
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun ParkingDurationRow(timestampMs: Long, accentColor: Color) {
    if (timestampMs <= 0L) return
    val elapsedMin = ((kotlin.time.Clock.System.now().toEpochMilliseconds() - timestampMs) / 60_000L)
        .toInt().coerceAtLeast(0)
    val durationText = if (elapsedMin < 60) {
        stringResource(Res.string.home_peek_parking_duration_min, elapsedMin)
    } else {
        stringResource(Res.string.home_peek_parking_duration_hm, elapsedMin / 60, elapsedMin % 60)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Schedule,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(META_ICON_DP.dp),
        )
        Text(
            text = durationText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = META_VALUE_ALPHA),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// ZonePeekRow — saved zone selected (view)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun ZonePeekRow(
    zone: Zone,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val isPrivate = zone.isPrivate
    val accentColor = if (isPrivate) MaterialTheme.colorScheme.tertiary
                      else MaterialTheme.colorScheme.primary
    val accentOnColor = if (isPrivate) MaterialTheme.colorScheme.onTertiary
                        else MaterialTheme.colorScheme.onPrimary

    PeekStateCard(
        headerLabel = zone.name,
        title = stringResource(Res.string.home_zone_radius_meters, zone.radiusMeters.roundToInt()),
        accentColor = accentColor,
        onDismiss = onDismiss,
        leading = {
            PeekHeaderIconChip(
                icon = zoneIconFor(zone.iconKey),
                accentColor = accentColor,
                iconTint = accentOnColor,
            )
        },
        content = {
            if (isPrivate) {
                HelperRow(
                    icon = Icons.Outlined.Lock,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    primary = stringResource(Res.string.home_zone_private_badge),
                )
            }
            Spacer(Modifier.height(14.dp))
        },
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PapFooterButton(
                    label = stringResource(Res.string.home_zone_action_edit),
                    leadingIcon = Icons.Outlined.Edit,
                    onClick = onEdit,
                    style = PapFooterButtonStyle.Outlined,
                    modifier = Modifier.weight(1f),
                )
                PapFooterButton(
                    label = stringResource(Res.string.home_zone_action_delete),
                    leadingIcon = Icons.Outlined.Delete,
                    onClick = onDelete,
                    style = PapFooterButtonStyle.Outlined,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    )
}

// ═════════════════════════════════════════════════════════════════════════════
// AddingParkingPeekRow — modo "Posicionar aparcamiento" (create + edit)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun AddingParkingPeekRow(
    state: HomeState,
    isEditing: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val primaryText = cameraTitleWhileSettling(state)

    // Resolve which vehicle this AddingParking session is FOR so the header
    // shows e.g. "Toyota Corolla" instead of the generic mode label — the user
    // needs to recognise the car when they hit confirm. [MULTI-PARKING-001]
    //  - create: state.addingParkingVehicleId set by the row tap
    //  - edit:   editingParkingId → activeSessions → vehicleId
    val targetVehicleId = if (isEditing) {
        state.activeSessions.firstOrNull { it.id == state.editingParkingId }?.vehicleId
    } else {
        state.addingParkingVehicleId
    }
    val targetVehicle = targetVehicleId?.let { id -> state.vehicles.firstOrNull { it.id == id } }
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

    PeekStateCard(
        headerLabel = headerLabel,
        title = primaryText,
        onDismiss = onCancel,
        leading = { PeekHeaderIconChip(icon = PaparcarIcons.VehicleCar) },
        content = {
            HelperRow(
                icon = Icons.Outlined.Info,
                iconTint = MaterialTheme.colorScheme.secondary,
                primary = helperPrimary,
                secondary = stringResource(Res.string.home_add_parking_helper_secondary),
            )
            Spacer(Modifier.height(14.dp))
        },
        actions = {
            PapFooterButton(
                label = ctaLabel,
                leadingIcon = if (isEditing) Icons.Outlined.EditLocationAlt
                              else PaparcarIcons.VehicleCar,
                onClick = onConfirm,
                style = PapFooterButtonStyle.Filled,
                enabled = !state.isSavingParking && !state.isCameraMoving,
                isLoading = state.isSavingParking,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

// ═════════════════════════════════════════════════════════════════════════════
// ReportPeekRow — modo "Avisar plaza libre" (v1)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun ReportPeekRow(
    state: HomeState,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onSizeSelected: (VehicleSize?) -> Unit,
) {
    val primaryText = cameraTitleWhileSettling(state)

    PeekStateCard(
        headerLabel = stringResource(Res.string.home_report_header_label),
        title = primaryText,
        onDismiss = onCancel,
        leading = { PeekHeaderIconChip(icon = Icons.Outlined.Campaign) },
        content = {
            HelperRow(
                icon = Icons.Outlined.Info,
                iconTint = MaterialTheme.colorScheme.secondary,
                primary = stringResource(Res.string.home_report_helper_primary),
                secondary = stringResource(Res.string.home_report_helper_secondary),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.home_report_size_section).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = SECTION_TRACKING_SP.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECTION_LABEL_ALPHA),
            )
            Spacer(Modifier.height(6.dp))
            SizeChipRow(selected = state.reportingSize, onSelect = onSizeSelected)
            Spacer(Modifier.height(14.dp))
        },
        actions = {
            PapFooterButton(
                label = stringResource(Res.string.home_report_confirm_here),
                leadingIcon = Icons.Outlined.Campaign,
                onClick = onConfirm,
                style = PapFooterButtonStyle.Filled,
                enabled = !state.isCameraMoving && !state.isReporting,
                isLoading = state.isReporting,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun SizeChipRow(selected: VehicleSize?, onSelect: (VehicleSize?) -> Unit) {
    val sizes = VehicleSize.entries
    val cs = MaterialTheme.colorScheme

    @Composable
    fun Chip(label: String, isSelected: Boolean, onClick: () -> Unit) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(SIZE_CHIP_RADIUS_DP.dp))
                .background(
                    if (isSelected) cs.primary else cs.surfaceContainerHigh,
                )
                .clickable { onClick() }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) cs.onPrimary else cs.onSurfaceVariant,
            )
        }
    }

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "unknown") {
            Chip(
                label = stringResource(Res.string.vehicle_size_unknown),
                isSelected = selected == null,
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
            Chip(
                label = label,
                isSelected = size == selected,
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
    state: HomeState,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onNameChange: (String) -> Unit,
    onIconChange: (String) -> Unit,
    onRadiusChange: (Float) -> Unit,
    onIsPrivateToggled: (Boolean) -> Unit = {},
) {
    val primaryText = cameraTitleWhileSettling(state)

    val headerLabel = if (state.editingZoneId != null) {
        stringResource(Res.string.home_zone_edit_header_label)
    } else {
        stringResource(Res.string.home_zone_header_label)
    }
    val focusManager = LocalFocusManager.current
    PeekStateCard(
        headerLabel = headerLabel,
        title = primaryText,
        onDismiss = onCancel,
        leading = { PeekHeaderIconChip(icon = zoneIconFor(state.addingZoneIconKey)) },
        content = {
            androidx.compose.material3.OutlinedTextField(
                value = state.addingZoneName,
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
                        imageVector = zoneIconFor(state.addingZoneIconKey),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingIcon = if (state.addingZoneName.isNotEmpty()) {
                    { PapClearIconButton(onClick = { onNameChange("") }) }
                } else null,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.home_zone_icon_section).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = SECTION_TRACKING_SP.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECTION_LABEL_ALPHA),
            )
            Spacer(Modifier.height(6.dp))
            ZoneIconPickerRow(
                selectedKey = state.addingZoneIconKey,
                onSelect = onIconChange,
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.home_zone_radius_section).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = SECTION_TRACKING_SP.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECTION_LABEL_ALPHA),
                )
                Text(
                    text = stringResource(Res.string.home_zone_radius_meters, state.addingZoneRadius.roundToInt()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = state.addingZoneRadius,
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
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                    Column {
                        Text(
                            text = stringResource(Res.string.home_zone_private_label),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(Res.string.home_zone_private_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECTION_LABEL_ALPHA),
                        )
                    }
                }
                Switch(
                    checked = state.addingZoneIsPrivate,
                    onCheckedChange = onIsPrivateToggled,
                )
            }
            Spacer(Modifier.height(14.dp))
        },
        actions = {
            PapFooterButton(
                label = stringResource(Res.string.home_zone_save_action),
                leadingIcon = Icons.Outlined.Bookmark,
                onClick = onConfirm,
                style = PapFooterButtonStyle.Filled,
                enabled = state.addingZoneName.isNotBlank() && !state.isSavingZone && !state.isCameraMoving,
                isLoading = state.isSavingZone,
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

// ═════════════════════════════════════════════════════════════════════════════
// Helper row — icon + primary line + secondary line (used by Report mode)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
internal fun HelperRow(
    icon: ImageVector,
    primary: String,
    secondary: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HELPER_CORNER_DP.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = HELPER_BG_ALPHA))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = primary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!secondary.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = HELPER_SECONDARY_ALPHA),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
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
private fun cameraTitleWhileSettling(state: HomeState): String =
    if (state.isCameraMoving || state.isCameraGeocoding) {
        state.cameraAddressAndPlace?.let { info ->
            info.placeInfo?.name?.takeIf { it.isNotBlank() }
                ?: info.address?.displayLine?.takeIf { it.isNotBlank() }
        } ?: "…"
    } else {
        cameraTitleOrFallback(state.cameraAddressAndPlace)
    }

// ═════════════════════════════════════════════════════════════════════════════
// Default browse row — location header with libres badge
// ═════════════════════════════════════════════════════════════════════════════

@Composable
private fun CameraLocationRow(state: HomeState, freeCount: Int, onToggle: () -> Unit = {}) {
    val info = state.cameraAddressAndPlace
    // Show skeleton when there is no displayable content — covers:
    //  • info still null (initial load before first geocode)
    //  • geocoding in flight with no previous content
    //  • geocoding finished but address + POI both empty (prevents "unknown address" flash)
    val hasContent = info != null && (!info.displayLine.isNullOrBlank() || info.placeInfo != null)
    if (!hasContent) {
        PeekLocationSkeleton(onToggle = onToggle)
        return
    }

    // info is non-null and has at least one displayable field from here on.
    // Shimmer bars are declared unconditionally to satisfy Compose composition rules.
    val shimmerTransition = rememberInfiniteTransition(label = "addr_shimmer")
    val shimmerAlpha by shimmerTransition.animateFloat(
        initialValue = SHIMMER_ALPHA_MIN,
        targetValue = SHIMMER_ALPHA_MAX,
        animationSpec = infiniteRepeatable(
            animation = tween(SHIMMER_DURATION_MS, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "addr_shimmer_alpha",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = info.placeInfo?.category?.icon ?: Icons.Filled.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(30.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            val textAlpha = if (state.isCameraGeocoding) shimmerAlpha else 1f
            Text(
                text = if (info.placeInfo != null) info.placeInfo.name
                       else info.displayLine ?: stringResource(Res.string.home_address_unknown),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = textAlpha),
                maxLines = 1,
                modifier = Modifier.basicMarquee(),
            )
            val secondaryLine = if (info.placeInfo != null) {
                info.address.displayLine?.takeIf { it != info.placeInfo.name }
            } else {
                listOfNotNull(info.address.city, info.address.region)
                    .joinToString(", ").takeIf { it.isNotEmpty() }
            }
            if (secondaryLine != null) {
                Text(
                    text = secondaryLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECONDARY_ALPHA * textAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Surface(
            color = if (freeCount > 0) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (freeCount > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        ),
                )
                Text(
                    stringResource(Res.string.home_stats_free_spots_badge, freeCount),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (freeCount > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
private fun PeekLocationSkeleton(onToggle: () -> Unit = {}) {
    val transition = rememberInfiniteTransition(label = "peek_skeleton")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.40f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeleton_pulse",
    )
    val skeletonColor = MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
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

private const val SPOT_BADGE_DP = 44
private const val SPOT_BADGE_ICON_DP = 30
private const val ZONE_ICON_CHIP_DP = 40
private const val SIZE_CHIP_RADIUS_DP = 20
private const val WALK_DISTANCE_THRESHOLD_M = 400f
private const val HELPER_CORNER_DP = 10
private const val META_ICON_DP = 18
private const val FIABILITY_SEGMENTS = 5
private const val FIABILITY_SEG_HEIGHT_DP = 4
private const val FIABILITY_SEG_GAP_DP = 3
private const val FIABILITY_SEG_RADIUS_DP = 2
private const val FIABILITY_EXPIRY_WARN_MIN = 5
private const val FIABILITY_EXPIRY_AMBER_SEGS = 2
private const val COMPAT_CORNER_DP = 8
private const val COMPAT_ICON_DP = 16
private const val COMPAT_INCOMPAT_BG_ALPHA = 0.07f
private const val COMPAT_INCOMPAT_FG_ALPHA = 0.55f
private const val COMPAT_UNKNOWN_FG_ALPHA = 0.40f

private const val SECTION_TRACKING_SP = 0.8
private const val SECTION_LABEL_ALPHA = 0.55f
private const val META_VALUE_ALPHA = 0.7f
private const val FIABILITY_EMPTY_ALPHA = 0.25f
private const val COMPAT_BG_ALPHA = 0.12f
private const val HELPER_BG_ALPHA = 0.5f
private const val SECONDARY_ALPHA = 0.55f
private const val HELPER_SECONDARY_ALPHA = SECONDARY_ALPHA
private const val TOGGLE_ROW_ALPHA = 0.55f
// Border alpha for the outlined "Move location" utility action — a quiet neutral hairline. [PEEK-ACTIONS-002]
private const val MOVE_OUTLINE_ALPHA = 0.30f

private const val SHIMMER_ALPHA_MIN = 0.10f
private const val SHIMMER_ALPHA_MAX = 0.80f
private const val SHIMMER_DURATION_MS = 550
