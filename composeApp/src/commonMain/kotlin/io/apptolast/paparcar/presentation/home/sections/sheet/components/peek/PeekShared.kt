@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.home.sections.sheet.components.peek

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.AddressAndPlace
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.domain.model.displayName
import io.apptolast.paparcar.presentation.home.sections.sheet.components.SheetTokens
import io.apptolast.paparcar.presentation.util.SpotReliabilityUiState
import io.apptolast.paparcar.presentation.util.distanceString
import io.apptolast.paparcar.presentation.util.driveTimeString
import io.apptolast.paparcar.presentation.util.walkTimeString
import io.apptolast.paparcar.ui.components.PapSectionHeader
import io.apptolast.paparcar.ui.components.ReliabilityMeter
import io.apptolast.paparcar.ui.theme.PaparcarType
import io.apptolast.paparcar.ui.theme.stateColors
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_address_unknown
import paparcar.composeapp.generated.resources.home_peek_spot_expires
import paparcar.composeapp.generated.resources.home_peek_spot_high
import paparcar.composeapp.generated.resources.home_peek_spot_low
import paparcar.composeapp.generated.resources.home_peek_spot_manual
import paparcar.composeapp.generated.resources.home_peek_spot_medium
import paparcar.composeapp.generated.resources.home_peek_spot_reliability_label
import paparcar.composeapp.generated.resources.home_vehicle_fallback_name

// ─────────────────────────────────────────────────────────────────────────────
// PeekShared — helpers common to the peek variants (meta rows, palettes,
// title resolvers, live minute clock). [HOME-ATOMIZE-001 F3]
// ─────────────────────────────────────────────────────────────────────────────

internal const val MS_PER_MINUTE = 60_000L
private const val META_ICON_DP = 18
private const val FIABILITY_SEG_HEIGHT_DP = 4
private const val FIABILITY_EXPIRY_WARN_MIN = 5

internal enum class TravelMode { WALKING, DRIVING }

/** The vehicle's display name, or null when it has none worth showing. */
@Composable
internal fun vehicleSummary(vehicle: Vehicle?): String? {
    if (vehicle == null) return null
    val fallback = stringResource(Res.string.home_vehicle_fallback_name)
    return vehicle.displayName(fallback = fallback).takeIf { it.isNotBlank() }
}

/**
 * Canonical peek meta row — accent icon + one SemiBold value line. The concrete
 * rows (distance, spot age, en-route, parking duration) are thin wrappers over
 * this molde so their visuals can't drift apart. [HOME-VEH-REFINE-001]
 */
@Composable
internal fun PeekMetaRow(icon: ImageVector, text: String, tint: Color) {
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
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = SheetTokens.META_VALUE_ALPHA),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun DistanceRow(distanceM: Float?, mode: TravelMode, accentColor: Color) {
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
        text = "${distanceString(distanceM)}${SheetTokens.META_SEPARATOR}$timeText",
        tint = accentColor,
    )
}

@Composable
internal fun FiabilityIndicator(level: SpotReliabilityUiState, expiresInMin: Int?) {
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

/**
 * Emits the current epoch-millis and re-emits on every whole-minute boundary, so relative-time
 * labels ("Caduca en N min", "Publicada hace N min") count down live while the peek is visible
 * instead of freezing at the value captured on first composition. [SPOT-TTL-LIVE-001]
 */
@Composable
internal fun rememberNowMinuteTick(): Long {
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

internal data class SpotPeekPalette(
    val badgeBg: Color,
    val badgeFg: Color,
    val label: String,
)

@Composable
internal fun SpotReliabilityUiState.peekPalette(): SpotPeekPalette {
    val sc = stateColors()
    val label = when (this) {
        SpotReliabilityUiState.HIGH   -> stringResource(Res.string.home_peek_spot_high)
        SpotReliabilityUiState.MEDIUM -> stringResource(Res.string.home_peek_spot_medium)
        SpotReliabilityUiState.LOW    -> stringResource(Res.string.home_peek_spot_low)
        SpotReliabilityUiState.MANUAL -> stringResource(Res.string.home_peek_spot_manual)
    }
    return SpotPeekPalette(sc.bg, sc.on, label)
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
 * Camera-anchored title resolver for the pin-mode peek cards. Returns the POI
 * name when the camera sits on a place, the geocoded address line otherwise,
 * and a localized fallback when the camera has no usable location info yet.
 */
@Composable
internal fun cameraTitleOrFallback(info: AddressAndPlace?): String {
    val placeName = info?.placeInfo?.name?.takeIf { it.isNotBlank() }
    if (placeName != null) return placeName
    val addressLine = info?.address?.displayLine?.takeIf { it.isNotBlank() }
    if (addressLine != null) return addressLine
    return stringResource(Res.string.home_address_unknown)
}

/**
 * Like [cameraTitleOrFallback] but returns stale data or "…" while the camera
 * is moving or geocoding ([isSettling]), so pin-mode peek cards never flash
 * "unknown address" mid-drag.
 */
@Composable
internal fun cameraTitleWhileSettling(info: AddressAndPlace?, isSettling: Boolean): String =
    if (isSettling) {
        info?.let {
            it.placeInfo?.name?.takeIf { n -> n.isNotBlank() }
                ?: it.address?.displayLine?.takeIf { l -> l.isNotBlank() }
        } ?: "…"
    } else {
        cameraTitleOrFallback(info)
    }
