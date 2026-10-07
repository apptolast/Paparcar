@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.presentation.home.sections.sheet.components.peek

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Adjust
import androidx.compose.material.icons.rounded.ChevronRight
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
import com.rndeveloper.paparcar.domain.model.SpotTone
import com.rndeveloper.paparcar.domain.model.AddressAndPlace
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.displayName
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.SheetTokens
import com.rndeveloper.paparcar.domain.model.SpotFreshness
import com.rndeveloper.paparcar.presentation.util.distanceString
import com.rndeveloper.paparcar.presentation.util.radiusString
import com.rndeveloper.paparcar.presentation.util.driveTimeString
import com.rndeveloper.paparcar.presentation.util.placeLineText
import com.rndeveloper.paparcar.presentation.util.toPlaceLine
import com.rndeveloper.paparcar.presentation.util.walkTimeString
import com.rndeveloper.paparcar.ui.components.PapSectionHeader
import com.rndeveloper.paparcar.ui.components.ReliabilityMeter
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import com.rndeveloper.paparcar.ui.theme.stateColors
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.home_spot_releasing_eyebrow
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_address_unknown
import paparcar.composeapp.generated.resources.home_peek_parking_approximate
import paparcar.composeapp.generated.resources.home_peek_spot_fresh
import paparcar.composeapp.generated.resources.home_peek_spot_stale
import paparcar.composeapp.generated.resources.home_peek_spot_recent
import paparcar.composeapp.generated.resources.home_peek_spot_freshness_label
import paparcar.composeapp.generated.resources.home_vehicle_fallback_name
import com.rndeveloper.paparcar.ui.theme.PapAlpha
import com.rndeveloper.paparcar.ui.theme.PapIconSize

// ─────────────────────────────────────────────────────────────────────────────
// PeekShared — helpers common to the peek variants (meta rows, palettes,
// title resolvers, live minute clock). [HOME-ATOMIZE-001 F3]
// ─────────────────────────────────────────────────────────────────────────────

internal const val MS_PER_MINUTE = 60_000L
private const val FIABILITY_SEG_HEIGHT_DP = 4

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
internal fun PeekMetaRow(
    icon: ImageVector,
    text: String,
    tint: Color,
    maxLines: Int = 1,
    /** Non-null → the whole row is tappable and wears a trailing chevron. The row's own copy must
     *  already say what tapping does — the chevron only signals that it CAN be done.
     *  [SPOT-A-ZONE-PARK-FREES-A-ZONE-SPOT-001] */
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        // A one-liner centres against its icon; a wrapped explanation (the approximate-zone row)
        // has to hang from the top or the icon floats in the middle of the paragraph.
        verticalAlignment = if (maxLines == 1) Alignment.CenterVertically else Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(PapIconSize.withLabel),
        )
        Text(
            text = text,
            // These meta rows ARE the card's primary info, standalone with the full width — the
            // DATA-role precondition (token competing for horizontal space) doesn't hold, so they
            // read as LECTURA, not as CIFRA. [PEEK-META-INTER-001]
            style = PaparcarType.current.rowTitle,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = SheetTokens.META_VALUE_ALPHA),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = if (onClick != null) Modifier.weight(1f) else Modifier,
        )
        if (onClick != null) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(PapIconSize.withLabel),
            )
        }
    }
}

/**
 * The doubt row of an approximate session: the map draws the circle, this says what it means and
 * what to do about it. Only shown when the session really is an area. Copy is cause + consequence
 * + remedy with no internal mechanics — never "steps", "anchor" or "honest close".
 * [UI-APPROXIMATE-PARKING-DRAWS-ITS-DOUBT-001]
 */
@Composable
internal fun ApproximateZoneRow(
    zoneRadiusMeters: Float?,
    accentColor: Color,
    /** Non-null → the row honours its own copy ("Edit to place it exactly"): one tap opens the
     *  pin-positioning flow, instead of making the user hunt for the round edit button. Validating
     *  WHERE the car really is turns the zone back into an exact pin — which is also what lets the
     *  eventual freed spot publish exactly. [SPOT-A-ZONE-PARK-FREES-A-ZONE-SPOT-001] */
    onAdjust: (() -> Unit)? = null,
) {
    val radius = zoneRadiusMeters ?: return
    PeekMetaRow(
        icon = Icons.Rounded.Adjust,
        text = stringResource(Res.string.home_peek_parking_approximate, radiusString(radius)),
        tint = accentColor,
        maxLines = APPROXIMATE_ROW_MAX_LINES,
        onClick = onAdjust,
    )
}

private const val APPROXIMATE_ROW_MAX_LINES = 3

@Composable
internal fun DistanceRow(distanceM: Float?, mode: TravelMode, accentColor: Color) {
    if (distanceM == null) return
    val icon = when (mode) {
        TravelMode.WALKING -> Icons.AutoMirrored.Rounded.DirectionsWalk
        TravelMode.DRIVING -> Icons.Rounded.Navigation
    }
    // Null = no hay tiempo que sostener a esa distancia, y entonces la fila es SÓLO la distancia:
    // ni tiempo ni el separador que lo anunciaba.
    // [COPY-A-DURATION-ROLLS-INTO-HOURS-AND-A-CAPACITY-IS-NOT-FREE-SPACES-001]
    val timeText = when (mode) {
        TravelMode.WALKING -> walkTimeString(distanceM)
        TravelMode.DRIVING -> driveTimeString(distanceM)
    }
    PeekMetaRow(
        icon = icon,
        text = distanceString(distanceM) + timeText?.let { "${SheetTokens.META_SEPARATOR}$it" }.orEmpty(),
        tint = accentColor,
    )
}

@Composable
internal fun FiabilityIndicator(level: SpotTone) {
    // [SPOT-FRESHNESS-IS-AGE-NOT-A-COUNTDOWN-001] The right-hand "expires in N min" text is gone.
    // It was a countdown to the sweep that deletes the document, presented as if it said something
    // about the parking space — and it sat directly above a meter that had already been told the
    // spot was stale. How old the offer is now reads once, in the header's subtitle, in words.
    PapSectionHeader(
        title = stringResource(Res.string.home_peek_spot_freshness_label),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(5.dp))

    // Same canonical 5-segment meter as list/ficha, coloured by freshness tier
    // (verde/ámbar/rojo) — no longer always-green. [IDENTITY-ICONS-001 D]
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

/**
 * What the peek says and what colour it says it in, for one freshness tier.
 *
 * The fields were `badgeBg`/`badgeFg` from when this tier rendered as a filled badge. It has not
 * been one for a long time: everything that reads this — the eyebrow, the distance row, the
 * en-route row — paints TEXT, so there is one colour and its name says the job. `badgeFg` had no
 * reader at all. [UI-COLOR-THE-RAMP-HAS-ONE-RESOLVER-001]
 */
internal data class SpotPeekPalette(
    val accent: Color,
    val label: String,
)

@Composable
internal fun SpotTone.peekPalette(): SpotPeekPalette {
    val label = when (this) {
        SpotTone.FRESH  -> stringResource(Res.string.home_peek_spot_fresh)
        SpotTone.RECENT -> stringResource(Res.string.home_peek_spot_recent)
        SpotTone.STALE  -> stringResource(Res.string.home_peek_spot_stale)
        // [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001] The other three name how FRESH the space is.
        // This one has to name something else entirely, because there is no space yet.
        SpotTone.RELEASING -> stringResource(Res.string.home_spot_releasing_eyebrow)
    }
    // Painted as TEXT: the peek eyebrow and the accent rows. Readable leg, not the fill.
    // [UI-COLOR-GREEN-TEXT-EARNS-ITS-CONTRAST-001]
    return SpotPeekPalette(stateColors().text, label)
}

/**
 * Peek-friendly title resolver. Returns place name OR address line, **never**
 * concatenated — the peek/state cards have tight horizontal space and a long
 * "name · address" line truncates ugly mid-word. When neither exists the caller's
 * [fallback] speaks instead: the label names what the pin IS (a spot, your
 * parking), never its coordinates. [UI-LOCATION-FALLBACK-SPEAKS-HUMAN-001]
 */
internal fun peekTitle(
    placeName: String?,
    addressLine: String?,
    fallback: String,
): String = placeName?.takeIf { it.isNotBlank() }
    ?: addressLine?.takeIf { it.isNotBlank() }
    ?: fallback

/**
 * Camera-anchored title resolver for the pin-mode peek cards. Returns the POI
 * name when the camera sits on a place, the geocoded address line otherwise,
 * and a localized fallback when the camera has no usable location info yet.
 * A borrowed-neighbour answer declares itself as "Near X" — the approximation
 * is never passed off as exact. [GEO-CACHE-ANSWERS-NEARBY-001]
 */
@Composable
internal fun cameraTitleOrFallback(info: AddressAndPlace?): String =
    cameraLine(info) ?: stringResource(Res.string.home_address_unknown)

/**
 * Like [cameraTitleOrFallback] but returns stale data or "…" while the camera
 * is moving or geocoding ([isSettling]), so pin-mode peek cards never flash
 * "unknown address" mid-drag.
 */
@Composable
internal fun cameraTitleWhileSettling(info: AddressAndPlace?, isSettling: Boolean): String =
    if (isSettling) {
        cameraLine(info) ?: "…"
    } else {
        cameraTitleOrFallback(info)
    }

/**
 * POI name > address line, wrapped in "Near X" when the answer is approximate.
 *
 * Delegates to the one resolver every place-naming surface shares — the peek titles here, and the
 * vehicle chip / card footers, which name the SAME point during a trip. This used to be its own
 * copy of the fallback chain, which is how two surfaces get to disagree about one place.
 * [UI-A-LIVE-TRIP-NAMES-ITS-PLACE-001]
 */
@Composable
private fun cameraLine(info: AddressAndPlace?): String? = placeLineText(info.toPlaceLine())
