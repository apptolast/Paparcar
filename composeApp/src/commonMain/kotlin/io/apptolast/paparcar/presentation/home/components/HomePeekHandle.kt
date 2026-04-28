package io.apptolast.paparcar.presentation.home.components

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
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import io.apptolast.paparcar.presentation.home.HomeState
import io.apptolast.paparcar.presentation.util.distanceMeters
import io.apptolast.paparcar.presentation.util.driveTimeString
import io.apptolast.paparcar.presentation.util.distanceString
import io.apptolast.paparcar.presentation.util.locationDisplayText
import io.apptolast.paparcar.presentation.util.relativeTimeText
import io.apptolast.paparcar.presentation.util.walkTimeString
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_address_unknown
import paparcar.composeapp.generated.resources.home_parking_release
import paparcar.composeapp.generated.resources.home_peek_dismiss_cd
import paparcar.composeapp.generated.resources.home_stats_free_spots_badge

@Composable
internal fun HomePeekHandle(
    state: HomeState,
    onDismiss: () -> Unit = {},
    onRelease: () -> Unit = {},
) {
    val freeCount = state.nearbySpots.size
    val isParkingSelected = state.selectedItemId == HomeState.PARKING_ITEM_ID
    val selectedSpot = state.selectedItemId
        ?.takeIf { !isParkingSelected }
        ?.let { id -> state.nearbySpots.find { it.id == id } }
    val parkingToShow = if (isParkingSelected) state.userParking else null

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

        // 3-state animated switch: selected spot ↔ selected parking ↔ camera location
        AnimatedContent(
            targetState = Pair(selectedSpot, parkingToShow),
            transitionSpec = {
                if (targetState.first != null) {
                    (slideInVertically { it / 2 } + fadeIn()) togetherWith
                        (slideOutVertically { -it / 2 } + fadeOut())
                } else {
                    (slideInVertically { -it / 2 } + fadeIn()) togetherWith
                        (slideOutVertically { it / 2 } + fadeOut())
                }
            },
            label = "peek_content",
        ) { (spot, parking) ->
            when {
                spot != null -> SpotPeekRow(
                    spot = spot,
                    userLocation = state.userGpsPoint?.let { Pair(it.latitude, it.longitude) },
                    onDismiss = onDismiss,
                )
                parking != null -> ParkingPeekRow(
                    parking = parking,
                    userLocation = state.userGpsPoint?.let { Pair(it.latitude, it.longitude) },
                    onDismiss = onDismiss,
                    onRelease = onRelease,
                )
                else -> CameraLocationRow(state = state, freeCount = freeCount)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Spot selected row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SpotPeekRow(
    spot: Spot,
    userLocation: Pair<Double, Double>?,
    onDismiss: () -> Unit,
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val placeEmoji = spot.placeInfo?.category?.emoji
        if (placeEmoji != null) {
            Text(
                text = placeEmoji,
                fontSize = 22.sp,
            )
        } else {
            Icon(
                Icons.Outlined.LocalParking,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
        }

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

        // ✕ dismiss — deselects the current spot
        Surface(
            onClick = onDismiss,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = stringResource(Res.string.home_peek_dismiss_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(6.dp)
                    .size(16.dp),
            )
        }
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

            // ✕ dismiss — deselects parking
            Surface(
                onClick = onDismiss,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = stringResource(Res.string.home_peek_dismiss_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(6.dp)
                        .size(16.dp),
                )
            }
        }

        // ── Release action — destructive, prominent in peek detail layer ──────
        Button(
            onClick = onRelease,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) {
            Text(
                stringResource(Res.string.home_parking_release),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

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
        val cameraPlaceEmoji = info.placeInfo?.category?.emoji
        if (cameraPlaceEmoji != null) {
            Text(
                text = cameraPlaceEmoji,
                fontSize = 22.sp,
            )
        } else {
            Icon(
                Icons.Outlined.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
        }
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
