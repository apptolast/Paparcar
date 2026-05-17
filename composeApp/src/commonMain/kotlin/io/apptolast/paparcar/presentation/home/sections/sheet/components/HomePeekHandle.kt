package io.apptolast.paparcar.presentation.home.sections.sheet.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.PinDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import io.apptolast.paparcar.ui.components.PapFooterButton
import io.apptolast.paparcar.ui.components.PapFooterButtonStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.ZoneIcon
import io.apptolast.paparcar.presentation.home.HomeMode
import io.apptolast.paparcar.presentation.home.HomeState
import io.apptolast.paparcar.presentation.util.zoneIconFor
import io.apptolast.paparcar.presentation.util.distanceMeters
import io.apptolast.paparcar.presentation.util.driveTimeString
import io.apptolast.paparcar.presentation.util.distanceString
import io.apptolast.paparcar.presentation.util.locationDisplayText
import io.apptolast.paparcar.presentation.util.relativeTimeText
import io.apptolast.paparcar.presentation.util.walkTimeString
import io.apptolast.paparcar.ui.icons.icon
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_address_unknown
import paparcar.composeapp.generated.resources.home_report_action
import paparcar.composeapp.generated.resources.home_report_subtitle
import paparcar.composeapp.generated.resources.home_navigate_to_spot
import paparcar.composeapp.generated.resources.home_parking_release
import paparcar.composeapp.generated.resources.home_peek_dismiss_cd
import paparcar.composeapp.generated.resources.home_stats_free_spots_badge
import paparcar.composeapp.generated.resources.home_navigate_to_vehicle
import paparcar.composeapp.generated.resources.home_zone_cancel_cd
import paparcar.composeapp.generated.resources.home_zone_icon_picker_cd
import paparcar.composeapp.generated.resources.home_zone_name_placeholder
import paparcar.composeapp.generated.resources.home_zone_save_action
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
internal fun HomePeekHandle(
    state: HomeState,
    onDismiss: () -> Unit = {},
    onRelease: () -> Unit = {},
    onNavigateExternal: (lat: Double, lon: Double, walking: Boolean) -> Unit = { _, _, _ -> },
    onCancelReport: () -> Unit = {},
    onConfirmReport: () -> Unit = {},
    onCancelAddZone: () -> Unit = {},
    onConfirmAddZone: () -> Unit = {},
    onUpdateZoneName: (String) -> Unit = {},
    onUpdateZoneIcon: (String) -> Unit = {},
) {
    val freeCount = state.nearbySpots.size
    val isParkingSelected = state.selectedItemId == HomeState.PARKING_ITEM_ID
    val selectedSpot = state.selectedItemId
        ?.takeIf { !isParkingSelected }
        ?.let { id -> state.nearbySpots.find { it.id == id } }
    val parkingToShow = if (isParkingSelected) state.userParking else null

    // Encode the five mutually exclusive peek states as a typed target so the
    // AnimatedContent transitions between them cleanly without piling triples
    // of nullable fields.
    val peekState: PeekState = when {
        state.mode is HomeMode.Reporting -> PeekState.Reporting
        state.mode is HomeMode.AddingZone -> PeekState.AddingZone
        selectedSpot != null -> PeekState.SelectedSpot(selectedSpot)
        parkingToShow != null -> PeekState.SelectedParking(parkingToShow)
        else -> PeekState.Browse
    }

    Column(modifier = Modifier.fillMaxWidth()) {

        // Drag pill
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

        // 4-state animated switch: spot ↔ parking ↔ reporting ↔ browse.
        AnimatedContent(
            targetState = peekState,
            transitionSpec = {
                // "Engaged" states (any state row with a primary action) slide
                // up; the default browse row slides down. Reporting is treated
                // as engaged because it has the same dismiss + action affordances
                // as a selected item.
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
                is PeekState.SelectedSpot -> SpotPeekRow(
                    spot = target.spot,
                    userLocation = state.userGpsPoint?.let { Pair(it.latitude, it.longitude) },
                    onDismiss = onDismiss,
                    onNavigate = {
                        onNavigateExternal(
                            target.spot.location.latitude,
                            target.spot.location.longitude,
                            false,
                        )
                    },
                )
                is PeekState.SelectedParking -> ParkingPeekRow(
                    parking = target.parking,
                    userLocation = state.userGpsPoint?.let { Pair(it.latitude, it.longitude) },
                    onDismiss = onDismiss,
                    onRelease = onRelease,
                    onWalkToCar = {
                        onNavigateExternal(
                            target.parking.location.latitude,
                            target.parking.location.longitude,
                            true,
                        )
                    },
                )
                PeekState.Reporting -> ReportPeekRow(
                    state = state,
                    onCancel = onCancelReport,
                    onConfirm = onConfirmReport,
                )
                PeekState.AddingZone -> AddingZonePeekRow(
                    state = state,
                    onCancel = onCancelAddZone,
                    onConfirm = onConfirmAddZone,
                    onNameChange = onUpdateZoneName,
                    onIconChange = onUpdateZoneIcon,
                )
                PeekState.Browse -> CameraLocationRow(state = state, freeCount = freeCount)
            }
        }
    }
}

/**
 * Typed state for the peek's [AnimatedContent]. Mutually exclusive — the
 * peek can only ever be in one of these at a time.
 */
private sealed class PeekState {
    data class SelectedSpot(val spot: Spot) : PeekState()
    data class SelectedParking(val parking: UserParking) : PeekState()
    data object Reporting : PeekState()
    data object AddingZone : PeekState()
    data object Browse : PeekState()
}

// ─────────────────────────────────────────────────────────────────────────────
// Spot selected row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SpotPeekRow(
    spot: Spot,
    userLocation: Pair<Double, Double>?,
    onDismiss: () -> Unit,
    onNavigate: () -> Unit,
) {
    val distM = userLocation?.let { (uLat, uLon) ->
        distanceMeters(uLat, uLon, spot.location.latitude, spot.location.longitude)
    }
    val displayText = locationDisplayText(
        placeInfo = spot.placeInfo,
        address = spot.address,
        lat = spot.location.latitude,
        lon = spot.location.longitude,
        showEmoji = false,
    )

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val placeIcon = spot.placeInfo?.category?.icon
            Icon(
                imageVector = placeIcon ?: Icons.Outlined.LocalParking,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(),
                )
                if (distM != null) {
                    Text(
                        text = "${distanceString(distM)}  ·  ${driveTimeString(distM)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            PeekDismissButton(onDismiss = onDismiss)
        }

        PapFooterButton(
            label = stringResource(Res.string.home_navigate_to_spot),
            leadingIcon = Icons.Outlined.Navigation,
            onClick = onNavigate,
            style = PapFooterButtonStyle.Filled,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Active parking row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ParkingPeekRow(
    parking: UserParking,
    userLocation: Pair<Double, Double>?,
    onDismiss: () -> Unit,
    onRelease: () -> Unit,
    onWalkToCar: () -> Unit,
) {
    val distM = userLocation?.let { (uLat, uLon) ->
        distanceMeters(uLat, uLon, parking.location.latitude, parking.location.longitude)
    }
    val displayText = locationDisplayText(
        placeInfo = parking.placeInfo,
        address = parking.address,
        lat = parking.location.latitude,
        lon = parking.location.longitude,
    )
    val timeAgo = relativeTimeText(parking.location.timestamp)

    Column {
        // ── Info row ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Filled.DirectionsCar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(),
                )
                if (distM != null) {
                    Text(
                        text = "${distanceString(distM)}  ·  ${walkTimeString(distM)}  ·  $timeAgo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        text = timeAgo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    )
                }
            }

            PeekDismissButton(onDismiss = onDismiss)
        }

        // Two equally-weighted footer actions. Outlined "Walk to car" is the
        // secondary affordance; filled "Release spot" is now also green (the
        // release action is conceptually positive — sharing with the
        // community — and the destructive choice lives behind the dialog
        // shown on tap). [PEEK-ACTIONS-001]
        PapFooterButton(
            label = stringResource(Res.string.home_navigate_to_vehicle),
            leadingIcon = Icons.AutoMirrored.Outlined.DirectionsWalk,
            onClick = onWalkToCar,
            style = PapFooterButtonStyle.Outlined,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        PapFooterButton(
            label = stringResource(Res.string.home_parking_release),
            leadingIcon = Icons.AutoMirrored.Outlined.Logout,
            onClick = onRelease,
            style = PapFooterButtonStyle.Filled,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reporting row — manual "Avisar plaza" flow
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReportPeekRow(
    state: HomeState,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val info = state.cameraLocationInfo
    val primaryText = when {
        info?.placeInfo != null -> info.placeInfo.name
        info?.address?.displayLine != null -> info.address.displayLine!!
        else -> stringResource(Res.string.home_address_unknown)
    }
    val placeIcon = info?.placeInfo?.category?.icon

    Column {
        // Info row — mirrors SpotPeekRow / ParkingPeekRow: place icon + content
        // column + dismiss button on the right.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = placeIcon ?: Icons.Outlined.PinDrop,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = primaryText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(),
                )
                Text(
                    text = stringResource(Res.string.home_report_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            PeekDismissButton(onDismiss = onCancel)
        }

        // Primary action — same `PapFooterButton` style as the navigate /
        // release CTAs in the other peek rows, so the affordance reads as
        // "the action for this state" across all four state variants.
        PapFooterButton(
            label = stringResource(Res.string.home_report_action),
            leadingIcon = Icons.Outlined.Campaign,
            onClick = onConfirm,
            style = PapFooterButtonStyle.Filled,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Adding-zone row — manual "Guardar zona" flow
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AddingZonePeekRow(
    state: HomeState,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onNameChange: (String) -> Unit,
    onIconChange: (String) -> Unit,
) {
    val info = state.cameraLocationInfo
    val primaryText = when {
        info?.placeInfo != null -> info.placeInfo.name
        info?.address?.displayLine != null -> info.address.displayLine!!
        else -> stringResource(Res.string.home_address_unknown)
    }
    val placeIcon = info?.placeInfo?.category?.icon

    Column {
        // Info row — same molde as Report/Spot/Parking: place icon + content
        // column + dismiss button on the right.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = placeIcon ?: zoneIconFor(state.addingZoneIconKey),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
            androidx.compose.material3.OutlinedTextField(
                value = state.addingZoneName,
                onValueChange = onNameChange,
                placeholder = { Text(stringResource(Res.string.home_zone_name_placeholder)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            PeekDismissButton(onDismiss = onCancel)
        }

        // Address line so the user can verify what they're saving — same
        // address surfaced by Browse's CameraLocationRow, intentionally
        // duplicated here because the input field already occupies the slot
        // that normally hosts it.
        Text(
            text = primaryText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .basicMarquee()
                .padding(horizontal = 20.dp),
        )

        // Icon picker — horizontal scrollable row of preset chips, one
        // tap-able per ZoneIcon entry. Selected chip is highlighted with
        // primaryContainer so the choice is visible at a glance.
        ZoneIconPickerRow(
            selectedKey = state.addingZoneIconKey,
            onSelect = onIconChange,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )

        PapFooterButton(
            label = stringResource(Res.string.home_zone_save_action),
            leadingIcon = Icons.Outlined.Bookmark,
            onClick = onConfirm,
            style = PapFooterButtonStyle.Filled,
            enabled = state.addingZoneName.isNotBlank() && !state.isSavingZone,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
    }
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
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        items(items = ZoneIcon.PRESETS, key = { it }) { key ->
            val isSelected = key == selectedKey
            Box(
                modifier = Modifier
                    .size(40.dp)
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

// ─────────────────────────────────────────────────────────────────────────────
// Shared peek widgets
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Visually de-emphasised dismiss button. Kept visible (the user needs to
 * recognise that the peek modal is a *state* they can clear to keep
 * exploring the map) but stripped of background and ink so the primary
 * action below it wins the visual hierarchy. [PEEK-ACTIONS-001]
 */
@Composable
private fun PeekDismissButton(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .size(DISMISS_HIT_TARGET)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Close,
            contentDescription = stringResource(Res.string.home_peek_dismiss_cd),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISMISS_ICON_ALPHA),
            modifier = Modifier.size(DISMISS_ICON_SIZE),
        )
    }
}

private val DISMISS_HIT_TARGET     = 40.dp   // a11y minimum hit target
private val DISMISS_ICON_SIZE      = 18.dp
private const val DISMISS_ICON_ALPHA = 0.45f

// ─────────────────────────────────────────────────────────────────────────────
// Default location row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CameraLocationRow(state: HomeState, freeCount: Int) {
    val info = state.cameraLocationInfo
    if (info == null || (state.isCameraGeocoding && info.displayLine == null && info.placeInfo == null)) {
        PeekLocationSkeleton()
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val cameraPlaceIcon = info.placeInfo?.category?.icon
        Icon(
            imageVector = cameraPlaceIcon ?: Icons.Outlined.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (info.placeInfo != null) info.placeInfo.name
                       else info.displayLine ?: stringResource(Res.string.home_address_unknown),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
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
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Free spots badge
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

// ─────────────────────────────────────────────────────────────────────────────
// Loading skeleton for the peek handle (shown while cameraLocationInfo == null)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PeekLocationSkeleton() {
    val transition = rememberInfiniteTransition(label = "peek_skeleton")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
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
        // Icon placeholder
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(skeletonColor.copy(alpha = pulseAlpha)),
        )
        // Text lines placeholder
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
        // Badge placeholder
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(skeletonColor.copy(alpha = pulseAlpha * 0.7f)),
        )
    }
}
