@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.home.sections.sheet.components.peek

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.model.SpotStatus
import io.apptolast.paparcar.domain.model.Vehicle
import io.apptolast.paparcar.presentation.home.HomeIntent
import io.apptolast.paparcar.presentation.home.sections.sheet.HomeSheetAction
import io.apptolast.paparcar.presentation.home.sections.sheet.components.PapSheet
import io.apptolast.paparcar.presentation.home.sections.sheet.components.PapSheetLead
import io.apptolast.paparcar.presentation.home.sections.sheet.components.SpotFitRow
import io.apptolast.paparcar.presentation.util.distanceMeters
import io.apptolast.paparcar.presentation.util.isManualReport
import io.apptolast.paparcar.presentation.util.toReliabilityUiState
import io.apptolast.paparcar.ui.components.PapDivider
import io.apptolast.paparcar.ui.components.PapFooterButton
import io.apptolast.paparcar.ui.components.PapFooterButtonStyle
import io.apptolast.paparcar.ui.theme.PapMotion
import io.apptolast.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_navigate_to_spot
import paparcar.composeapp.generated.resources.home_peek_spot_age_hour
import paparcar.composeapp.generated.resources.home_peek_spot_age_min
import paparcar.composeapp.generated.resources.home_peek_spot_en_route
import paparcar.composeapp.generated.resources.home_spot_gone
import paparcar.composeapp.generated.resources.home_spot_peek_show_list
import paparcar.composeapp.generated.resources.home_spot_retracted_action
import paparcar.composeapp.generated.resources.home_spot_retracted_eyebrow
import paparcar.composeapp.generated.resources.home_spot_retracted_note
import paparcar.composeapp.generated.resources.home_spot_still_there
import paparcar.composeapp.generated.resources.home_spot_unconfirmed_note

// ═════════════════════════════════════════════════════════════════════════════
// SpotPeek — selected community spot. [HOME-ATOMIZE-001 F3]
// ═════════════════════════════════════════════════════════════════════════════

private const val WALK_DISTANCE_THRESHOLD_M = 400f
private const val TOGGLE_ROW_ALPHA = 0.55f
private const val NOTE_ALPHA = 0.75f

@Composable
internal fun SpotPeek(
    spot: Spot,
    userLocation: Pair<Double, Double>?,
    activeVehicle: Vehicle?,
    spotListExpanded: Boolean,
    onIntent: (HomeIntent) -> Unit,
    onAction: (HomeSheetAction) -> Unit,
) {
    val reliabilityLevel = spot.toReliabilityUiState()
    val palette = reliabilityLevel.peekPalette()
    // [DET-HANDOFF-NOT-MANUAL-001 §B.3] The spot was withdrawn while the user had it open. The
    // marker is already gone from the map; this peek is the only place left that can say WHY, so
    // it drops the whole community loop (directions, "still there?", "it's gone") — every one of
    // those actions is about a space that exists — and says the one true thing instead.
    val isRetracted = spot.status == SpotStatus.RETRACTED
    val isUnconfirmed = spot.status == SpotStatus.PROVISIONAL
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
            isManual = spot.isManualReport,
            enRouteCount = spot.enRouteCount,
        ),
        eyebrow = if (isRetracted) stringResource(Res.string.home_spot_retracted_eyebrow) else palette.label,
        // Reliability tint also rides the eyebrow; the lead puck itself now carries the
        // tier colour/ring, matching the map marker and list row. [HOME-PUCK-001]
        // A withdrawn spot has no reliability left to tint — the eyebrow states it in ink.
        // [UI-COLOR-DOCTRINE-001]
        eyebrowColor = if (isRetracted) MaterialTheme.colorScheme.onSurfaceVariant else palette.badgeBg,
        title = title,
        onDismiss = { onIntent(HomeIntent.SelectItem(null)) },
        meta = {
            if (isRetracted) {
                // Fit, distance, age and en-route are all facts about a space to drive to. There
                // isn't one. The address in the title is enough to know WHICH report this was.
                DistanceRow(distanceM = distM, mode = travelMode, accentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                SpotFitRow(spot = spot, vehicle = activeVehicle)
                DistanceRow(distanceM = distM, mode = travelMode, accentColor = palette.badgeBg)
                if (spotAgeMin != null) {
                    SpotAgeRow(ageMinutes = spotAgeMin, accentColor = palette.badgeBg)
                }
                if (spot.enRouteCount > 0) {
                    SpotEnRouteRow(count = spot.enRouteCount, accentColor = palette.badgeBg)
                }
            }
        },
        content = {
            when {
                isRetracted -> SpotNote(stringResource(Res.string.home_spot_retracted_note))
                else -> {
                    FiabilityIndicator(level = reliabilityLevel, expiresInMin = ttlMinutes)
                    // [DET-HANDOFF-NOT-MANUAL-001 §B.3] Unconfirmed spots keep the full community
                    // loop — they are real offers — but they say what they are, and the note points
                    // at the two buttons right below, which are how an unconfirmed spot becomes a
                    // known one.
                    if (isUnconfirmed) {
                        Spacer(Modifier.height(10.dp))
                        SpotNote(stringResource(Res.string.home_spot_unconfirmed_note))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        },
        actions = {
            if (isRetracted) {
                // The only useful thing left: get the user back to the spots that DO exist.
                PapFooterButton(
                    label = stringResource(Res.string.home_spot_retracted_action),
                    leadingIcon = Icons.Rounded.Search,
                    onClick = { onIntent(HomeIntent.SelectItem(null)) },
                    style = PapFooterButtonStyle.Filled,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                // Primary = get there before it expires — THE community-loop action here.
                PapFooterButton(
                    label = stringResource(Res.string.home_navigate_to_spot),
                    leadingIcon = Icons.Rounded.Navigation,
                    onClick = {
                        onAction(
                            HomeSheetAction.NavigateExternal(
                                lat = spot.location.latitude,
                                lon = spot.location.longitude,
                                walking = false,
                            ),
                        )
                    },
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
                        onClick = { onIntent(HomeIntent.SendSpotSignal(spot.id, accepted = true)) },
                        style = PapFooterButtonStyle.Tonal,
                        modifier = Modifier.weight(1f),
                    )
                    PapFooterButton(
                        label = stringResource(Res.string.home_spot_gone),
                        leadingIcon = Icons.Rounded.Block,
                        onClick = {
                            onIntent(HomeIntent.SendSpotSignal(spot.id, accepted = false))
                            onIntent(HomeIntent.SelectItem(null))
                        },
                        style = PapFooterButtonStyle.Tonal,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                PapDivider()
                SpotListToggleRow(
                    expanded = spotListExpanded,
                    label = stringResource(Res.string.home_spot_peek_show_list),
                    onClick = { onAction(HomeSheetAction.ToggleSpotList) },
                )
            }
        },
    )
}

/**
 * [DET-HANDOFF-NOT-MANUAL-001 §B.3] A plain sentence in the peek body: what happened, what it means
 * for you, what to do next. Prose, so Inter — and no tint, because it carries a state, not an
 * identity. [UI-COLOR-DOCTRINE-001]
 */
@Composable
private fun SpotNote(text: String) {
    Text(
        text = text,
        style = PaparcarType.current.body,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = NOTE_ALPHA),
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

private fun ageMinutes(timestampMs: Long, nowMs: Long): Int? {
    if (timestampMs <= 0L) return null
    val ageMs = nowMs - timestampMs
    if (ageMs < 0L) return null
    val mins = (ageMs / MS_PER_MINUTE).toInt()
    return if (mins > 0) mins else null
}

private fun remainingMinutes(expiresAtMs: Long, nowMs: Long): Int? {
    if (expiresAtMs <= 0L) return null
    val remaining = ((expiresAtMs - nowMs) / MS_PER_MINUTE).toInt()
    return if (remaining > 0) remaining else null
}
