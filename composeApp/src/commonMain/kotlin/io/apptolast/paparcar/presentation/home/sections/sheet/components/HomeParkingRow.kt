@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.home.sections.sheet.components

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
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.HorizontalDivider
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
import io.apptolast.paparcar.domain.model.UserParking
import io.apptolast.paparcar.domain.model.displayName
import io.apptolast.paparcar.domain.model.monitoringStatus
import io.apptolast.paparcar.presentation.home.VehicleCard
import io.apptolast.paparcar.presentation.util.distanceMeters
import io.apptolast.paparcar.presentation.util.distanceString
import io.apptolast.paparcar.presentation.util.relativeTimeText
import io.apptolast.paparcar.ui.components.DrivingRadarHalo
import io.apptolast.paparcar.ui.components.UnmarkedParkingIcon
import io.apptolast.paparcar.ui.components.VehicleGlyph
import io.apptolast.paparcar.ui.components.VehicleIdentityHeader
import io.apptolast.paparcar.ui.components.VehicleStatusLeadingIcon
import io.apptolast.paparcar.ui.components.vehicleStatusAccent
import io.apptolast.paparcar.ui.components.vehicleStatusBorderColor
import io.apptolast.paparcar.ui.theme.PapBorders
import io.apptolast.paparcar.ui.theme.PapDriveBlue
import io.apptolast.paparcar.ui.theme.PapShapes
import io.apptolast.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_peek_parked_label
import paparcar.composeapp.generated.resources.home_vehicle_card_parked_at
import paparcar.composeapp.generated.resources.home_vehicle_card_parked_meta
import paparcar.composeapp.generated.resources.home_vehicle_chip_mark_parking
import paparcar.composeapp.generated.resources.home_vehicle_chip_status_candidate
import paparcar.composeapp.generated.resources.home_vehicle_chip_status_driving
import paparcar.composeapp.generated.resources.home_vehicle_chip_unmarked
import paparcar.composeapp.generated.resources.home_vehicle_fallback_name

/**
 * Compact vehicle chip in the Home vehicles LazyRow (2+ vehicles). A vertical card: an identity row
 * (car glyph + **status icon before the name** — green = active, blue = Bluetooth, grey = inactive)
 * over a parking row. The parking row is the actionable fact: **parked → location icon + address**
 * (max 2 lines), **not marked → the "not marked" glyph**. No method label, no corner badge. The
 * border colour mirrors the parked state. Tapping transforms the sheet to the vehicle's state.
 * [HOME-VEH-REFINE-001] [HOME-CARDS-001]
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
    val monitoring = vehicle.monitoringStatus()
    val vehicleName = vehicle.displayName(fallback = stringResource(Res.string.home_vehicle_fallback_name))

    Surface(
        onClick = onClick,
        // Adaptive width so the name breathes without truncating in the horizontal strip.
        modifier = modifier.widthIn(min = CHIP_MIN_WIDTH_DP.dp, max = CHIP_MAX_WIDTH_DP.dp),
        shape = PapShapes.cardSmall,
        border = BorderStroke(
            if (isDriving) DRIVING_BORDER_DP.dp else BORDER_DP.dp,
            vehicleStatusBorderColor(monitoring, isDriving),
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
                    if (isDriving) DrivingRadarHalo(diameter = ICON_BOX_DP.dp)
                    VehicleGlyph(
                        carbody = vehicle.carbodyType,
                        size = vehicle.sizeCategory,
                        glyphSize = ICON_BOX_DP.dp,
                        color = vehicle.color,
                    )
                }
                VehicleStatusLeadingIcon(
                    status = monitoring,
                    tint = if (isDriving) PapDriveBlue else vehicleStatusAccent(monitoring),
                )
                Text(
                    vehicleName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(CHIP_FOOT_GAP_DP.dp))
            HorizontalDivider(color = cs.outline.copy(alpha = FOOT_DIVIDER_ALPHA))
            Spacer(Modifier.height(CHIP_FOOT_GAP_DP.dp))

            // Parking row — the actionable fact.
            Row(
                modifier = Modifier.heightIn(min = FOOT_MIN_HEIGHT_DP.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FOOT_GAP_DP.dp),
            ) {
                when {
                    isDriving -> Text(
                        text = stringResource(
                            if (isCandidate) Res.string.home_vehicle_chip_status_candidate
                            else Res.string.home_vehicle_chip_status_driving,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isCandidate) cs.primary else PapDriveBlue,
                        maxLines = 1,
                    )
                    session != null -> {
                        Icon(
                            imageVector = Icons.Rounded.LocationOn,
                            contentDescription = null,
                            tint = vehicleStatusAccent(monitoring),
                            modifier = Modifier.size(FOOT_ICON_DP.dp),
                        )
                        Text(
                            text = parkedAddressLine(session),
                            style = PaparcarType.current.metadata,
                            color = cs.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    else -> {
                        UnmarkedParkingIcon(tint = cs.onSurfaceVariant)
                        Text(
                            text = stringResource(Res.string.home_vehicle_chip_unmarked),
                            style = PaparcarType.current.metadata,
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
 * (location icon + "Parked at …" + relative time / distance + chevron), or a mark-parking CTA when
 * the vehicle has no active session. [HOME-VEH-REFINE-001] [HOME-CARDS-001]
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
    val monitoring = vehicle.monitoringStatus()
    val accent = if (isDriving) PapDriveBlue else vehicleStatusAccent(monitoring)

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = PapShapes.cardLarge,
        border = BorderStroke(
            if (isDriving) DRIVING_BORDER_DP.dp else BORDER_DP.dp,
            vehicleStatusBorderColor(monitoring, isDriving),
        ),
        color = cs.surfaceContainerHigh,
    ) {
        Column {
            // Shared identity anatomy — same composable as the Vehicles ficha, so both cards keep
            // identical tile/name/meta rhythm. [HOME-VEH-REFINE-001]
            VehicleIdentityHeader(
                vehicle = vehicle,
                isDriving = isDriving,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 15.dp, bottom = 13.dp),
            )

            HorizontalDivider(color = cs.outline.copy(alpha = FOOT_DIVIDER_ALPHA))

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
                    Box(contentAlignment = Alignment.Center) {
                        if (session != null || isDriving) {
                            Icon(
                                imageVector = Icons.Rounded.LocationOn,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(CARD_FOOT_ICON_DP.dp),
                            )
                        } else {
                            UnmarkedParkingIcon(tint = accent)
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    when {
                        isDriving -> Text(
                            text = stringResource(
                                if (isCandidate) Res.string.home_vehicle_chip_status_candidate
                                else Res.string.home_vehicle_chip_status_driving,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isCandidate) cs.primary else PapDriveBlue,
                            maxLines = 1,
                        )
                        session != null -> {
                            // The address title is the long line → condensed, so "Aparcado en {calle
                            // larga}" fits before ellipsizing. The time·distance subline is short and
                            // stays Inter. [HOME-VEH-REFINE-001]
                            Text(
                                text = parkedTitle(session),
                                style = PaparcarType.current.metadata.copy(fontWeight = FontWeight.SemiBold),
                                color = cs.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = parkedMeta(session, userLocation),
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        else -> Text(
                            text = stringResource(Res.string.home_vehicle_chip_mark_parking),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.primary,
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

/** Compact chip parking line: "address · 2 h ago" (max 2 lines), falling back to just the time. */
@Composable
private fun parkedAddressLine(session: UserParking): String {
    val time = relativeTimeText(session.location.timestamp)
    val name = parkedName(session)
    return if (name != null) "$name · $time"
    else "${stringResource(Res.string.home_peek_parked_label)} · $time"
}

/** Single-card footer title: "Parked at {address}", or a plain "Parked" before enrichment. */
@Composable
private fun parkedTitle(session: UserParking): String {
    val name = parkedName(session)
    return if (name != null) stringResource(Res.string.home_vehicle_card_parked_at, name)
    else stringResource(Res.string.home_peek_parked_label)
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
private const val FOOT_DIVIDER_ALPHA = PapBorders.HAIRLINE_DIVIDER_ALPHA
// Full-width single-vehicle card. [HOME-CARDS-001]
private const val CHEVRON_DP = 22
private const val CARD_FOOT_ICON_BOX_DP = 34
private const val CARD_FOOT_ICON_CORNER_DP = 10
private const val CARD_FOOT_ICON_DP = 18
private const val FOOT_ICON_BOX_ALPHA = 0.15f
private const val BORDER_DP = 1
private const val DRIVING_BORDER_DP = 1.5f // thicker live-blue border while driving [CHIP-DRIVING-001]
