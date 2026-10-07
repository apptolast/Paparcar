@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.presentation.home.sections.sheet.components.peek

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Adjust
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.SpotVotePolicy
import com.rndeveloper.paparcar.domain.model.SpotStatus
import com.rndeveloper.paparcar.domain.model.tone
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.presentation.home.HomeIntent
import com.rndeveloper.paparcar.presentation.home.PeekStep
import com.rndeveloper.paparcar.presentation.home.sections.sheet.HomeSheetAction
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheet
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheetLead
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheetStepper
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.SpotFitRow
import com.rndeveloper.paparcar.presentation.util.distanceMeters
import com.rndeveloper.paparcar.presentation.util.isManualReport
import com.rndeveloper.paparcar.presentation.util.freshness
import com.rndeveloper.paparcar.presentation.util.radiusString
import com.rndeveloper.paparcar.ui.components.PapFooterButton
import com.rndeveloper.paparcar.ui.components.PapFooterButtonStyle
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import com.rndeveloper.paparcar.ui.theme.PapGap
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.common_directions
import paparcar.composeapp.generated.resources.home_peek_spot_age_hour
import paparcar.composeapp.generated.resources.home_peek_spot_approximate
import paparcar.composeapp.generated.resources.home_peek_spot_age_min
import paparcar.composeapp.generated.resources.home_peek_spot_age_now
import paparcar.composeapp.generated.resources.home_peek_spot_en_route
import paparcar.composeapp.generated.resources.home_peek_step_next_spot
import paparcar.composeapp.generated.resources.home_peek_step_prev_spot
import paparcar.composeapp.generated.resources.home_spot_gone
import paparcar.composeapp.generated.resources.location_fallback_spot
import paparcar.composeapp.generated.resources.home_spot_retracted_action
import paparcar.composeapp.generated.resources.home_spot_retracted_eyebrow
import paparcar.composeapp.generated.resources.home_spot_releasing_note
import paparcar.composeapp.generated.resources.home_spot_retracted_note
import paparcar.composeapp.generated.resources.home_spot_still_there
import paparcar.composeapp.generated.resources.home_spot_unconfirmed_note

// ═════════════════════════════════════════════════════════════════════════════
// SpotPeek — selected community spot. [HOME-ATOMIZE-001 F3]
// ═════════════════════════════════════════════════════════════════════════════

private const val WALK_DISTANCE_THRESHOLD_M = 400f
private const val NOTE_ALPHA = 0.75f
private const val MINUTES_PER_HOUR = 60
private const val SPOT_APPROXIMATE_ROW_MAX_LINES = 3

@Composable
internal fun SpotPeek(
    spot: Spot,
    userLocation: Pair<Double, Double>?,
    activeVehicle: Vehicle?,
    /** True once this session has voted on this spot — the vote pair stops being offered.
     *  [SPOT-COMMUNITY-VOTES-NEED-A-CONSEQUENCE-001] */
    alreadyVoted: Boolean,
    /** Neighbours of this spot in the browse order — the footer ‹ / ›. [UI-PEEK-STEPS-BETWEEN-PINS-001] */
    step: PeekStep,
    onIntent: (HomeIntent) -> Unit,
    onAction: (HomeSheetAction) -> Unit,
) {
    // Live clock first: the freshness level is derived from it, so everything on this peek — puck,
    // eyebrow, meta accents, meter — moves together. [SPOT-FRESHNESS-IS-AGE-NOT-A-COUNTDOWN-001]
    val nowMs = rememberNowMinuteTick()
    // [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001] The tone, so a plaza still under its car cannot
    // borrow a rung of the freshness ramp — and so the puck here matches the one on the map.
    val freshnessLevel = spot.tone(nowMs)
    val palette = freshnessLevel.peekPalette()
    // [DET-HANDOFF-NOT-MANUAL-001 §B.3] The spot was withdrawn while the user had it open. The
    // marker is already gone from the map; this peek is the only place left that can say WHY, so
    // it drops the whole community loop (directions, "still there?", "it's gone") — every one of
    // those actions is about a space that exists — and says the one true thing instead.
    val isRetracted = spot.status == SpotStatus.RETRACTED
    // [ALERT-A-PROVISIONAL-SPOT-IS-PUSHED-BUT-NOT-SHOWN-001] El predicado vive en SpotStatus desde
    // que el aviso es su segundo consumidor: una superficie hedgeando y la otra prometiendo sobre
    // la misma plaza es lo que se evita teniéndolo en un solo sitio.
    val isUnconfirmed = spot.status.isUnconfirmedOffer
    // [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001] Nobody has left yet. The age line is suppressed
    // below for the same reason the row swaps its pill: "hace 0 min" would be the freshest reading
    // on the sheet, about the one plaza that still has a car in it.
    val isReleasing = spot.status == SpotStatus.RELEASING
    val distM = userLocation?.let { (uLat, uLon) ->
        distanceMeters(uLat, uLon, spot.location.latitude, spot.location.longitude)
    }
    // Auto-switch to walking mode when the spot is close enough to walk.
    val travelMode = if (distM != null && distM < WALK_DISTANCE_THRESHOLD_M) TravelMode.WALKING else TravelMode.DRIVING
    // Only a witness votes. The same rule the use case enforces, asked here so the buttons are
    // never offered where they would be refused. [SPOT-COMMUNITY-VOTES-NEED-A-CONSEQUENCE-001]
    val canVote = SpotVotePolicy.canVote(distM?.toDouble())
    val title = peekTitle(
        placeName = spot.placeInfo?.name,
        addressLine = spot.address?.displayLine,
        fallback = stringResource(Res.string.location_fallback_spot),
    )
    val spotAgeMin = if (isReleasing) null else ageMinutes(spot.location.timestamp, nowMs)
    val ageText = spotAgeMin?.let { minutes ->
        when {
            // A counter at zero says nothing — the first minute reads like a person would say it.
            // [UI-JUST-PARKED-READS-AS-NOW-001]
            minutes < 1 -> stringResource(Res.string.home_peek_spot_age_now)
            minutes < MINUTES_PER_HOUR -> stringResource(Res.string.home_peek_spot_age_min, minutes)
            else -> stringResource(Res.string.home_peek_spot_age_hour, minutes / MINUTES_PER_HOUR)
        }
    }

    PapSheet(
        lead = PapSheetLead.CommunitySpot(
            reliability = freshnessLevel,
            isManual = spot.isManualReport,
            unconfirmed = isUnconfirmed,
            enRouteCount = spot.enRouteCount,
        ),
        eyebrow = if (isRetracted) stringResource(Res.string.home_spot_retracted_eyebrow) else palette.label,
        // Freshness tint also rides the eyebrow; the lead puck itself now carries the
        // tier colour/ring, matching the map marker and list row. [HOME-PUCK-001]
        // A withdrawn spot has no freshness left to tint — the eyebrow states it in ink.
        // [UI-COLOR-DOCTRINE-001]
        eyebrowColor = if (isRetracted) MaterialTheme.colorScheme.onSurfaceVariant else palette.accent,
        title = title,
        // How old the offer is, on the header's third line — the same slot where the browse peek
        // puts "aparcado hace 1 h" for your own car. It is the fact that decides whether this spot
        // is worth driving to, so it reads with the address instead of queueing among the meta rows
        // (where it used to sit, and where it now no longer appears). [UI-SHEET-001]
        subtitle = ageText,
        onDismiss = { onIntent(HomeIntent.SelectItem(null)) },
        // Step to the neighbouring spot in the SAME order the sheet lists them. A withdrawn spot
        // has already dropped out of that order, so its step is empty and the header renders
        // untouched — no second condition to keep in sync. [UI-PEEK-STEPS-BETWEEN-PINS-001]
        stepper = PapSheetStepper(
            prevContentDescription = stringResource(Res.string.home_peek_step_prev_spot),
            nextContentDescription = stringResource(Res.string.home_peek_step_next_spot),
            onPrev = step.prevId?.let { id -> { onAction(HomeSheetAction.SelectSpot(id)) } },
            onNext = step.nextId?.let { id -> { onAction(HomeSheetAction.SelectSpot(id)) } },
        ),
        meta = {
            if (isRetracted) {
                // Fit, distance, age and en-route are all facts about a space to drive to. There
                // isn't one. The address in the title is enough to know WHICH report this was.
                DistanceRow(distanceM = distM, mode = travelMode, accentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                SpotFitRow(spot = spot, vehicle = activeVehicle)
                DistanceRow(distanceM = distM, mode = travelMode, accentColor = palette.accent)
                if (spot.enRouteCount > 0) {
                    SpotEnRouteRow(count = spot.enRouteCount, accentColor = palette.accent)
                }
                // Last row, and only when the spot is an AREA: the distance above is measured to
                // the centre of a circle nobody pinned, so the caveat has to follow it — the same
                // order the own-parking peek uses. [SPOT-A-ZONE-PARK-FREES-A-ZONE-SPOT-001]
                spot.zoneRadiusMeters?.let { radius ->
                    PeekMetaRow(
                        icon = Icons.Rounded.Adjust,
                        text = stringResource(Res.string.home_peek_spot_approximate, radiusString(radius)),
                        tint = palette.accent,
                        maxLines = SPOT_APPROXIMATE_ROW_MAX_LINES,
                    )
                }
            }
        },
        content = {
            when {
                isRetracted -> SpotNote(stringResource(Res.string.home_spot_retracted_note))
                else -> {
                    FiabilityIndicator(level = freshnessLevel)
                    // [DET-HANDOFF-NOT-MANUAL-001 §B.3] Unconfirmed spots keep the full community
                    // loop — they are real offers — but they say what they are, and the note points
                    // at the two buttons right below, which are how an unconfirmed spot becomes a
                    // known one.
                    if (isUnconfirmed) {
                        Spacer(Modifier.height(10.dp))
                        SpotNote(stringResource(Res.string.home_spot_unconfirmed_note))
                    }
                    // [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001] The community loop stays — driving
                    // over is exactly the right thing to do with this — but the note says the one
                    // fact the eyebrow cannot fit: the car has NOT left, so it may still be there.
                    // Cause, consequence and what to do, in that order.
                    if (isReleasing) {
                        Spacer(Modifier.height(10.dp))
                        SpotNote(stringResource(Res.string.home_spot_releasing_note))
                    }
                }
            }
            Spacer(Modifier.height(PapGap.block))
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
                    label = stringResource(Res.string.common_directions),
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
                // Signal pair — community feedback, low emphasis (tonal twins). "Still there"
                // refreshes the spot's age and keeps the sheet open; "It's gone" withdraws it and
                // dismisses. [SPOT-COMMUNITY-VOTES-NEED-A-CONSEQUENCE-001]
                //
                // Offered ONLY to a witness: within MAX_VOTE_DISTANCE_METERS of the spot, and only
                // once per session. A single tap now withdraws the spot for everyone, and that is
                // only safe because the person casting it is standing there. Too far, no fix, or
                // already voted → the pair is simply absent. A disabled button would invite the tap
                // and then refuse it; the buttons have nothing to say from across town.
                if (canVote && !alreadyVoted) {
                    Spacer(Modifier.height(8.dp))
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
                }
            }
        },
    )
}

/**
 * [DET-HANDOFF-NOT-MANUAL-001 §B.3] A plain sentence in the peek body: what happened, what it means
 * for you, what to do next. Prose, so LECTURA — and no tint, because it carries a state, not an
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
    // 0 is a real age — it renders as "posted just now", the freshest (most valuable) state.
    // null is reserved for invalid timestamps. [UI-SPOT-CLOCKS-NEVER-READ-ZERO-001]
    return (ageMs / MS_PER_MINUTE).toInt()
}
