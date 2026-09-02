@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.presentation.home.sections.sheet.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Adjust
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.ui.components.PapDivider
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.displayName
import com.rndeveloper.paparcar.domain.model.monitoringStatus
import com.rndeveloper.paparcar.presentation.home.VehicleCard
import com.rndeveloper.paparcar.presentation.util.distanceMeters
import com.rndeveloper.paparcar.presentation.util.distanceString
import com.rndeveloper.paparcar.presentation.util.relativeTimeText
import com.rndeveloper.paparcar.ui.components.DrivingRadarHalo
import com.rndeveloper.paparcar.ui.components.DrivingRouteGlyph
import com.rndeveloper.paparcar.ui.components.UnmarkedParkingIcon
import com.rndeveloper.paparcar.ui.components.VehicleGlyph
import com.rndeveloper.paparcar.ui.components.VehicleIdentityHeader
import com.rndeveloper.paparcar.ui.components.VehicleWatchLeadingIcon
import com.rndeveloper.paparcar.ui.components.rememberDrivingStatePulse
import com.rndeveloper.paparcar.ui.theme.vehicleChassisBorder
import com.rndeveloper.paparcar.ui.theme.vehicleIdentityColor
import com.rndeveloper.paparcar.ui.theme.watch
import com.rndeveloper.paparcar.ui.theme.PapShapes
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import com.rndeveloper.paparcar.ui.theme.PapColor
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_peek_parked_label
import paparcar.composeapp.generated.resources.home_vehicle_card_parked_at
import paparcar.composeapp.generated.resources.home_vehicle_card_parked_meta
import paparcar.composeapp.generated.resources.home_vehicle_chip_mark_parking
import paparcar.composeapp.generated.resources.home_det_monitoring
import paparcar.composeapp.generated.resources.home_vehicle_chip_status_candidate
import paparcar.composeapp.generated.resources.home_vehicle_chip_unmarked
import paparcar.composeapp.generated.resources.home_vehicle_fallback_name
import paparcar.composeapp.generated.resources.location_approximate_near

/**
 * Compact vehicle chip in the Home vehicles LazyRow (2+ vehicles). A vertical card: an identity row
 * (car glyph + **watch glyph + name in the identity colour** — green = active detection, blue = BT,
 * grey = unwatched) over a parking row. The parking row is the actionable fact: **parked →
 * location icon + address** (max 2 lines), **not marked → the "not marked" glyph**, **driving → a
 * self-drawing route glyph + the pulsing neutral state words**. No method label, no corner badge.
 * The border carries the same identity colour, dimmed. Tapping transforms the sheet to the
 * vehicle's state.
 * [HOME-VEH-REFINE-001] [HOME-CARDS-001] [UI-COLOR-DOCTRINE-001] [UI-CHIP-ROUTE-GLYPH-001]
 */
@Composable
internal fun HomeVehicleChip(
    card: VehicleCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDriving: Boolean = false,
    // Candidate phase: the trip stopped and the user is walking away — a distinct "parking…" hint.
    isCandidate: Boolean = false,
) {
    val vehicle = card.vehicle
    val session = card.session
    val cs = MaterialTheme.colorScheme
    val watch = vehicle.monitoringStatus().watch()
    // The vehicle's ONE colour: its watch method (green = active detection, blue = BT, grey = off).
    // The state below never re-colours it. [UI-COLOR-DOCTRINE-001]
    val accent = vehicleIdentityColor(watch)
    val vehicleName = vehicle.displayName(fallback = stringResource(Res.string.home_vehicle_fallback_name))

    Surface(
        onClick = onClick,
        // Adaptive width so the name breathes without truncating in the horizontal strip.
        modifier = modifier.widthIn(min = CHIP_MIN_WIDTH_DP.dp, max = CHIP_MAX_WIDTH_DP.dp),
        shape = PapShapes.cardSmall,
        // A trip in motion thickens the frame to the full identity colour — more energy, same hue.
        border = BorderStroke(
            if (isDriving) DRIVING_BORDER_DP.dp else BORDER_DP.dp,
            if (isDriving) accent else vehicleChassisBorder(watch),
        ),
        color = cs.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(CHIP_PADDING_DP.dp)) {
            // Identity row — glyph, then the status icon RIGHT BEFORE the name (never a corner badge,
            // which collides with the illustrative car glyph). [HOME-VEH-REFINE-001]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CHIP_TOP_GAP_DP.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isDriving) DrivingRadarHalo(diameter = ICON_BOX_DP.dp, color = accent)
                    VehicleGlyph(
                        carbody = vehicle.carbodyType,
                        size = vehicle.sizeCategory,
                        glyphSize = ICON_BOX_DP.dp,
                        color = vehicle.color,
                    )
                }
                // The watch glyph carries the identity colour; the name stays onSurface — tinting
                // both is over-information. [UI-COLOR-DOCTRINE-001]
                VehicleWatchLeadingIcon(watch = watch)
                Text(
                    vehicleName,
                    style = PaparcarType.current.rowName,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(CHIP_FOOT_GAP_DP.dp))
            PapDivider()
            Spacer(Modifier.height(CHIP_FOOT_GAP_DP.dp))

            // Parking row — the actionable fact.
            Row(
                modifier = Modifier.heightIn(min = FOOT_MIN_HEIGHT_DP.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FOOT_GAP_DP.dp),
            ) {
                when {
                    // A trip keeps the icon+text anatomy of the other two states, but the icon is a
                    // route drawing itself — never the location pin, which would claim a place the
                    // trip hasn't reached. State machine = neutral text; its liveness is the
                    // breathing pulse, not a hue. [UI-CHIP-ROUTE-GLYPH-001] [UI-COLOR-DOCTRINE-001]
                    isDriving -> {
                        DrivingRouteGlyph(color = accent, glyphSize = FOOT_ICON_DP.dp)
                        Text(
                            text = stringResource(
                                if (isCandidate) Res.string.home_vehicle_chip_status_candidate
                                else Res.string.home_det_monitoring,
                            ),
                            style = PaparcarType.current.label,
                            color = cs.onSurface.copy(alpha = rememberDrivingStatePulse()),
                            maxLines = 1,
                        )
                    }
                    session != null -> {
                        Icon(
                            // An AREA wears the zone glyph, never the pin — the pin claims a point
                            // the session refused to claim. [UI-APPROXIMATE-ZONE-IN-HISTORY-001]
                            imageVector = if (session.isApproximate) Icons.Rounded.Adjust else Icons.Rounded.LocationOn,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(FOOT_ICON_DP.dp),
                        )
                        Text(
                            text = parkedAddressLine(session),
                            // An address is a phrase you read → LECTURA (caption), same voice as the
                            // single-vehicle card's footer. CIFRA is for repeating data
                            // tokens, not prose — keeps 1-vehicle and 2+-vehicle Home consistent.
                            // [CARD-ONE-BADGE-001]
                            style = PaparcarType.current.caption,
                            color = cs.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    else -> {
                        UnmarkedParkingIcon(tint = accent)
                        Text(
                            text = stringResource(Res.string.home_vehicle_chip_unmarked),
                            style = PaparcarType.current.caption,
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Full-width single-vehicle card (exactly one registered vehicle). Roomier identity — big glyph,
 * name, a **text status pin + size chip** — over a footer that carries the **parked address**
 * (location icon + "Parked at …" + relative time / distance + chevron), the **self-drawing route
 * glyph** while a trip runs, or a mark-parking CTA when the vehicle has no active session.
 * [HOME-VEH-REFINE-001] [HOME-CARDS-001] [UI-CHIP-ROUTE-GLYPH-001]
 */
@Composable
internal fun HomeVehicleCard(
    card: VehicleCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDriving: Boolean = false,
    isCandidate: Boolean = false,
    // User position, to show the distance to the parked car ("2 h ago · 180 m away").
    userLocation: Pair<Double, Double>? = null,
) {
    val vehicle = card.vehicle
    val session = card.session
    val cs = MaterialTheme.colorScheme
    val watch = vehicle.monitoringStatus().watch()
    // Identity colour = watch method; the state below stays neutral. [UI-COLOR-DOCTRINE-001]
    val accent = vehicleIdentityColor(watch)

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = PapShapes.cardLarge,
        border = BorderStroke(
            if (isDriving) DRIVING_BORDER_DP.dp else BORDER_DP.dp,
            if (isDriving) accent else vehicleChassisBorder(watch),
        ),
        color = cs.surfaceContainerHigh,
    ) {
        Column {
            // Shared identity anatomy — same composable as the Vehicles ficha, so both cards keep
            // identical tile/name/meta rhythm. [HOME-VEH-REFINE-001]
            VehicleIdentityHeader(
                vehicle = vehicle,
                isDriving = isDriving,
                // Home is glanceable — size lives on the Vehicles ficha, not here. [CARD-META-POLISH-001]
                showSize = false,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 15.dp, bottom = 13.dp),
            )

            PapDivider()

            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(CARD_FOOT_ICON_BOX_DP.dp)
                        .clip(RoundedCornerShape(CARD_FOOT_ICON_CORNER_DP.dp))
                        .background(accent.copy(alpha = FOOT_ICON_BOX_ALPHA)),
                    contentAlignment = Alignment.Center,
                ) {
                    // Same priority order as the text beside it: a running trip outranks the parked
                    // session, so the pin (a place) yields to the route (a journey) while driving.
                    // [UI-CHIP-ROUTE-GLYPH-001]
                    Box(contentAlignment = Alignment.Center) {
                        when {
                            isDriving -> DrivingRouteGlyph(
                                color = accent,
                                glyphSize = CARD_FOOT_ICON_DP.dp,
                            )
                            session != null -> Icon(
                                // Same rule as the chip: an approximate session shows the zone
                                // glyph, not the pin. [UI-APPROXIMATE-ZONE-IN-HISTORY-001]
                                imageVector = if (session.isApproximate) Icons.Rounded.Adjust else Icons.Rounded.LocationOn,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(CARD_FOOT_ICON_DP.dp),
                            )
                            else -> UnmarkedParkingIcon(tint = accent)
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    when {
                        // Neutral state words with the driving breath. [UI-COLOR-DOCTRINE-001]
                        isDriving -> Text(
                            text = stringResource(
                                if (isCandidate) Res.string.home_vehicle_chip_status_candidate
                                else Res.string.home_det_monitoring,
                            ),
                            style = PaparcarType.current.rowTitle,
                            color = cs.onSurface.copy(alpha = rememberDrivingStatePulse()),
                            maxLines = 1,
                        )
                        session != null -> {
                            // The wide card has room, so the address reads as LECTURA (rowTitle) for
                            // legibility — condensed is reserved for the tight compact chip. [UI-METRICS-POLISH-001]
                            Text(
                                text = parkedTitle(session),
                                style = PaparcarType.current.rowTitle,
                                color = cs.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = parkedMeta(session, userLocation),
                                // Secondary subline under a LECTURA title — same voice as its title
                                // so the selected-car block doesn't mix voices. [PEEK-META-INTER-001]
                                style = PaparcarType.current.caption,
                                color = cs.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        else -> Text(
                            text = stringResource(Res.string.home_vehicle_chip_mark_parking),
                            style = PaparcarType.current.rowTitle,
                            color = PapColor.actionText,
                            maxLines = 1,
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(CHEVRON_DP.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Resolved address / place name for the parked session, or null before enrichment. */
private fun parkedName(session: UserParking): String? =
    session.placeInfo?.name?.takeIf { it.isNotBlank() }
        ?: session.address?.displayLine?.takeIf { it.isNotBlank() }

/**
 * [UI-APPROXIMATE-ZONE-IN-HISTORY-001] An approximate session (an AREA, not a point) says so at
 * card level with the same word the app already uses for borrowed locations: "Near X". The full
 * explanation (radius, cause, remedy) stays in the peek — a glanceable card only needs to stop
 * CLAIMING exactness it does not have.
 */
@Composable
private fun parkedPlaceLine(session: UserParking): String? {
    val name = parkedName(session) ?: return null
    return if (session.isApproximate) stringResource(Res.string.location_approximate_near, name) else name
}

/** Compact chip parking line: "address · 2 h ago" (max 2 lines), falling back to just the time. */
@Composable
private fun parkedAddressLine(session: UserParking): String {
    val time = relativeTimeText(session.location.timestamp)
    val name = parkedPlaceLine(session)
    return if (name != null) "$name · $time"
    else "${stringResource(Res.string.home_peek_parked_label)} · $time"
}

/** Single-card footer title: "Parked at {address}" — or "Near {address}" for an approximate
 *  session, or a plain "Parked" before enrichment. */
@Composable
private fun parkedTitle(session: UserParking): String {
    val name = parkedName(session)
    return when {
        name != null && session.isApproximate ->
            stringResource(Res.string.location_approximate_near, name)
        name != null -> stringResource(Res.string.home_vehicle_card_parked_at, name)
        else -> stringResource(Res.string.home_peek_parked_label)
    }
}

/** Single-card footer subline: "2 h ago · 180 m away", or just the time when position is unknown. */
@Composable
private fun parkedMeta(session: UserParking, userLocation: Pair<Double, Double>?): String {
    val time = relativeTimeText(session.location.timestamp)
    val distance = userLocation?.let { (uLat, uLon) ->
        distanceString(distanceMeters(uLat, uLon, session.location.latitude, session.location.longitude))
    }
    return if (distance != null) stringResource(Res.string.home_vehicle_card_parked_meta, time, distance)
    else time
}

// Adaptive width bounds — card grows to fit "Toyota Corolla" without truncating, capped so a long
// name never dominates the LazyRow. [HOME-CARDS-001]
private const val CHIP_MIN_WIDTH_DP = 150
private const val CHIP_MAX_WIDTH_DP = 200
private const val CHIP_PADDING_DP = 10
private const val CHIP_TOP_GAP_DP = 6
private const val CHIP_FOOT_GAP_DP = 8
private const val ICON_BOX_DP = 32
private const val FOOT_MIN_HEIGHT_DP = 34
private const val FOOT_GAP_DP = 7
private const val FOOT_ICON_DP = 15
// Full-width single-vehicle card. [HOME-CARDS-001]
private const val CHEVRON_DP = 22
private const val CARD_FOOT_ICON_BOX_DP = 34
private const val CARD_FOOT_ICON_CORNER_DP = 10
private const val CARD_FOOT_ICON_DP = 18
private const val FOOT_ICON_BOX_ALPHA = 0.15f
private const val BORDER_DP = 1
private const val DRIVING_BORDER_DP = 1.5f // thicker live-blue border while driving [CHIP-DRIVING-001]
