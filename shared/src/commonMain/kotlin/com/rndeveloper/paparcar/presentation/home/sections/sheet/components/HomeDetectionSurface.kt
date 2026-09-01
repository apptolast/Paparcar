package com.rndeveloper.paparcar.presentation.home.sections.sheet.components

import com.rndeveloper.paparcar.ui.components.PapIconTile
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.LocationSearching
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.automirrored.rounded.NotListedLocation
import androidx.compose.material.icons.rounded.SensorsOff
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.presentation.home.model.DetectionStory
import com.rndeveloper.paparcar.presentation.home.model.ParkedWatchBadge
import com.rndeveloper.paparcar.presentation.util.formatClockTime
import com.rndeveloper.paparcar.ui.theme.PapBorders
import com.rndeveloper.paparcar.ui.theme.PapShapes
import com.rndeveloper.paparcar.ui.theme.VehicleWatch
import com.rndeveloper.paparcar.ui.theme.vehicleIdentityColor
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_det_awaiting_cta_primary
import paparcar.composeapp.generated.resources.home_det_awaiting_cta_secondary
import paparcar.composeapp.generated.resources.home_det_awaiting_sub
import paparcar.composeapp.generated.resources.home_det_awaiting_title
import paparcar.composeapp.generated.resources.home_det_ask_cta_no
import paparcar.composeapp.generated.resources.home_det_ask_cta_yes
import paparcar.composeapp.generated.resources.home_det_ask_sub_at
import paparcar.composeapp.generated.resources.home_det_ask_sub_at_place
import paparcar.composeapp.generated.resources.home_det_ask_title
import paparcar.composeapp.generated.resources.home_det_ask_title_vehicle
import paparcar.composeapp.generated.resources.home_det_candidate_sub
import paparcar.composeapp.generated.resources.home_det_candidate_title
import paparcar.composeapp.generated.resources.home_det_core_cta
import paparcar.composeapp.generated.resources.home_det_core_sub
import paparcar.composeapp.generated.resources.home_det_core_title
import paparcar.composeapp.generated.resources.home_det_driving_stop_cta
import paparcar.composeapp.generated.resources.home_det_driving_sub
import paparcar.composeapp.generated.resources.home_det_driving_title
import paparcar.composeapp.generated.resources.home_det_novehicle_cta
import paparcar.composeapp.generated.resources.home_det_novehicle_sub
import paparcar.composeapp.generated.resources.home_det_novehicle_title
import paparcar.composeapp.generated.resources.home_det_producer_cta
import paparcar.composeapp.generated.resources.home_det_producer_sub
import paparcar.composeapp.generated.resources.home_det_producer_title
import paparcar.composeapp.generated.resources.home_det_watch_interrupted_cta
import paparcar.composeapp.generated.resources.home_det_watch_interrupted_sub
import paparcar.composeapp.generated.resources.home_det_watch_interrupted_title
import paparcar.composeapp.generated.resources.home_det_watching_bt_sub
import paparcar.composeapp.generated.resources.home_det_watching_fortify_cta
import paparcar.composeapp.generated.resources.home_det_watching_fragile_sub
import paparcar.composeapp.generated.resources.home_det_watching_parked_sub
import paparcar.composeapp.generated.resources.home_det_watching_title
import paparcar.composeapp.generated.resources.home_nudge_cta
import paparcar.composeapp.generated.resources.home_nudge_dismiss
import paparcar.composeapp.generated.resources.home_nudge_sub
import paparcar.composeapp.generated.resources.home_nudge_title

/**
 * The Home **detection story surface** — the single voice that answers "what is the app doing
 * for me right now". [DET-READY-001h] [UX-DETECTION-STORY-001]
 *
 * Renders the one [DetectionStory] resolved by `resolveDetectionStory`: the action stories keep the
 * loud accent-bar row (severity-adaptive: error-toned CORE block, calm amber upsells, brand-green
 * questions and cold start); [DetectionStory.Driving] and [DetectionStory.Watching] render a
 * discreet one-line status — the happy path speaks instead of going mute. [DetectionStory.Hidden]
 * renders nothing.
 *
 * This composable renders; it does NOT arbitrate. Which story wins — including the two pending
 * questions, [DetectionStory.AwaitingAnswer] and [DetectionStory.PendingAsk] — is decided by the
 * pure projection, so the precedence lives in one testable place. [DET-ASK-STATE-001]
 */
@Composable
fun HomeDetectionSurface(
    story: DetectionStory,
    onAddVehicle: () -> Unit,
    onOpenPermissions: () -> Unit,
    onMarkSpot: () -> Unit,
    onStartDrivingDetection: () -> Unit,
    onActivateDetection: () -> Unit,
    modifier: Modifier = Modifier,
    /** [DET-STOP-BUTTON-001] "Parar detección" on the live-trip row — the user's way out of a trip
     *  they don't want followed. Default no-op so previews/callers that don't wire it don't break. */
    onStopDetection: () -> Unit = {},
    /**
     * Whether the cold-start row offers the secondary "I'm driving" action. Off until the manual
     * Coordinator arming (DET-G-01b) exists — there is no infra to honour it yet. [DET-READY-001h]
     */
    allowDrivingDetection: Boolean = false,
    /** [DET-NUDGE-PERSIST-001] Resolve the pending "where did you leave your car?" nudge. Whether
     *  the nudge row shows at all is decided by `resolveDetectionStory`, not here. */
    onMarkNudgeSpot: () -> Unit = {},
    onDismissNudge: () -> Unit = {},
    /** [DET-ASK-STATE-001] The two answers to the "did you park?" row — the same pair of commands
     *  the tray notification's buttons send. Default no-ops so previews/callers that don't wire them
     *  don't break. */
    onAnswerParked: () -> Unit = {},
    onAnswerStillDriving: () -> Unit = {},
    /** [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] Frame the place the question is about. */
    onFocusAsk: (GpsPoint) -> Unit = {},
    /** Fire the battery-optimization exemption request from the FRAGILE watch row — the setup whose
     *  problem the exemption actually solves. [DET-WATCH-HONEST-001] [DET-BATTERY-EXEMPTION-NUDGE-001] */
    onRequestBatteryExemption: () -> Unit = {},
    /** Rebuild the departure watcher from the INTERRUPTED watch row. Deliberately NOT the exemption
     *  request: this row means the watcher is dead, and a permission grant never restarts it.
     *  [DET-WATCH-REACTIVATE-001] */
    onResumeWatch: () -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    val amber = Tone(cs.secondary, cs.onSecondary, cs.secondaryContainer, cs.onSecondaryContainer, isError = false)
    val error = Tone(cs.error, cs.onError, cs.errorContainer, cs.onErrorContainer, isError = true)
    // Colour = WHO is watching. Set-up rows speak as the app → brand green. Rows about a specific
    // vehicle wear that vehicle's identity colour — read from THE single resolver
    // (`vehicleIdentityColor`), the same call the chip, garage and peek make, so this surface can
    // never drift from them again. This file used to keep its own copy of the method→colour switch
    // (raw `papCarBlue`/`papWatchGreen` + a Boolean that could not say "unwatched") and that copy is
    // exactly what went silently wrong when the greens split. Tonal container = accent at low
    // alpha, same language as the watch badge. [UI-COLOR-DOCTRINE-001]
    val brand = Tone(cs.primary, cs.onPrimary, cs.primary.copy(alpha = INFO_CONTAINER_ALPHA), cs.primary, isError = false)
    @Composable
    fun identityTone(watch: VehicleWatch): Tone {
        val accent = vehicleIdentityColor(watch)
        return Tone(accent, cs.surface, accent.copy(alpha = INFO_CONTAINER_ALPHA), accent, isError = false)
    }

    when (story) {
        // [DET-ASK-STATE-001] The question the app is waiting on, asked in-app with the SAME two
        // answers as the tray notification. Green, not amber: nothing has failed — detection is
        // doing its job and simply cannot tell on its own, which is the doctrine's asymmetric
        // failure working as designed. The wording comes from the notification that posted it, so
        // the two surfaces cannot drift.
        //
        // [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] The WATCHED green, not the brand's. This
        // row was left on `brand` by the sweep that fixed `methodTone` a few lines below — the same
        // miss, one branch of the `when` further down, and the rule that sweep wrote applies here
        // more plainly than anywhere: this row names a specific car in its own title and wears its
        // glyph. A fixed `VehicleWatch.Assisted` rather than the story's own watch because the
        // question is Coordinator-only — `NotifyParkingConfirmation` and `degradeToPrompt` are
        // wired nowhere else, and a Bluetooth-watched car confirms deterministically without ever
        // being asked. Painting it blue would be the one screen that proves the car is NOT on the
        // Bluetooth lane.
        is DetectionStory.AwaitingAnswer -> ActionRow(
            tone = identityTone(VehicleWatch.Assisted),
            // The car, not a parking "P": this row is about the vehicle Paparcar is following, and
            // the app's whole visual vocabulary for "your car" is the car glyph. A P would be the
            // first place in the app where parking is drawn as signage.
            icon = Icons.Rounded.DirectionsCar,
            title = story.window.vehicleName
                ?.let { stringResource(Res.string.home_det_ask_title_vehicle, it) }
                ?: stringResource(Res.string.home_det_ask_title),
            // [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] The subtitle answers WHEN. Absolute
            // ("a las 12:41"), never "hace N min": a relative age needs a ticker, and without one it
            // freezes at whatever it read when the row was composed — the trap already met with the
            // map markers. An absolute time is also literally what was asked for, and it never lies
            // as it ages. It says nothing about what happens on silence: of the three verdicts the
            // timeout can reach, only one plants a pin here, so any countdown would be a promise
            // the app cannot keep.
            subtitle = story.window.street?.let { street ->
                // The place first: it is what decides the answer. The hour qualifies it.
                stringResource(
                    Res.string.home_det_ask_sub_at_place,
                    street,
                    formatClockTime(story.window.shownAtMs),
                )
            } ?: stringResource(
                Res.string.home_det_ask_sub_at,
                formatClockTime(story.window.shownAtMs),
            ),
            primaryLabel = stringResource(Res.string.home_det_ask_cta_yes),
            onPrimary = onAnswerParked,
            secondaryLabel = stringResource(Res.string.home_det_ask_cta_no),
            onSecondary = onAnswerStillDriving,
            secondaryIsDecline = true,
            // Tapping the CARD frames the place on the map. Not a button: a third control in a
            // yes/no row would rank "ver" alongside two answers it is not equal to, and the badge
            // glyph is a ~24 dp target nobody finds. Looking is NOT answering — this must never
            // touch the window, exactly like the deliberately absent `setDeleteIntent`.
            onClick = story.window.candidate?.let { at -> { onFocusAsk(at) } },
            modifier = modifier,
        )

        // [DET-NUDGE-PERSIST-001] The older, deadline-less ask: a parking record we could not place.
        DetectionStory.PendingAsk -> ActionRow(
            tone = amber,
            icon = Icons.AutoMirrored.Rounded.NotListedLocation,
            title = stringResource(Res.string.home_nudge_title),
            subtitle = stringResource(Res.string.home_nudge_sub),
            primaryLabel = stringResource(Res.string.home_nudge_cta),
            onPrimary = onMarkNudgeSpot,
            secondaryLabel = stringResource(Res.string.home_nudge_dismiss),
            onSecondary = onDismissNudge,
            modifier = modifier,
        )

        DetectionStory.NoVehicle -> ActionRow(
            tone = amber,
            icon = Icons.Rounded.DirectionsCar,
            title = stringResource(Res.string.home_det_novehicle_title),
            subtitle = stringResource(Res.string.home_det_novehicle_sub),
            primaryLabel = stringResource(Res.string.home_det_novehicle_cta),
            onPrimary = onAddVehicle,
            modifier = modifier,
        )

        // One "activate detection" surface for both causes — Settings flag off OR producer
        // permissions missing. The single button asks for whatever is missing. [DET-TOGGLE-001]
        DetectionStory.Inactive -> ActionRow(
            tone = brand,
            icon = Icons.Rounded.SensorsOff,
            title = stringResource(Res.string.home_det_producer_title),
            subtitle = stringResource(Res.string.home_det_producer_sub),
            primaryLabel = stringResource(Res.string.home_det_producer_cta),
            onPrimary = onActivateDetection,
            modifier = modifier,
        )

        DetectionStory.BlockedCore -> ActionRow(
            tone = error,
            icon = Icons.Rounded.LocationOff,
            title = stringResource(Res.string.home_det_core_title),
            subtitle = stringResource(Res.string.home_det_core_sub),
            primaryLabel = stringResource(Res.string.home_det_core_cta),
            onPrimary = onOpenPermissions,
            modifier = modifier,
        )

        DetectionStory.AwaitingFirstPark -> ActionRow(
            tone = brand,
            icon = Icons.Rounded.AddLocationAlt,
            title = stringResource(Res.string.home_det_awaiting_title),
            subtitle = stringResource(Res.string.home_det_awaiting_sub),
            primaryLabel = stringResource(Res.string.home_det_awaiting_cta_primary),
            onPrimary = onMarkSpot,
            secondaryLabel = if (allowDrivingDetection) stringResource(Res.string.home_det_awaiting_cta_secondary) else null,
            onSecondary = onStartDrivingDetection,
            modifier = modifier,
        )

        // Happy-path story: same card skeleton as the action rows. The row wears the trip vehicle's
        // identity colour; motion is the radar halo on the chip, not a new hue.
        // [UX-DETECTION-STORY-001] [UI-COLOR-DOCTRINE-001]
        //
        // Its single CTA is the way OUT of a trip the user doesn't want followed — the only state
        // where stopping means anything, and the exact row that claims to be following them.
        // [DET-STOP-BUTTON-001]
        is DetectionStory.Driving -> ActionRow(
            tone = identityTone(story.watch),
            icon = if (story.isCandidate) Icons.Rounded.LocationSearching else Icons.Rounded.Navigation,
            title = stringResource(
                if (story.isCandidate) Res.string.home_det_candidate_title else Res.string.home_det_driving_title,
                story.vehicleName,
            ),
            subtitle = stringResource(
                if (story.isCandidate) Res.string.home_det_candidate_sub else Res.string.home_det_driving_sub,
            ),
            primaryLabel = stringResource(Res.string.home_det_driving_stop_cta),
            onPrimary = onStopDetection,
            // INLINE trailing pill, not stacked: a four-letter CTA does not earn a full-width band,
            // and the title it "steals width from" is allowed to wrap — two lines of car name cost
            // less than a whole button row. Measured across the 9 locales before flipping: the CTA
            // tops out at 9 chars (PL "Zatrzymaj") and the subtitle at 35, both fine at the reduced
            // width. The alert watch rows below KEEP stacking for a measured reason too: their
            // subtitles run to 78/126 chars and would not survive the inline cut.
            // [UI-DET-STOP-BELONGS-BESIDE-THE-STORY-001]
            modifier = modifier,
        )

        // Honest watch line: the vehicle's identity colour + no CTA only when the watch is genuinely
        // live; amber warning with an "activate" CTA when fragile; error "reactivate" when the OS
        // killed it. Each CTA pulls its OWN lever — the exemption fortifies a live-but-fragile watch,
        // the resume rebuilds a dead one; they are not interchangeable.
        // [DET-WATCH-HONEST-001] [DET-WATCH-REACTIVATE-001] [UI-COLOR-DOCTRINE-001]
        is DetectionStory.Watching -> when (story.watchBadge) {
            ParkedWatchBadge.WATCHING, ParkedWatchBadge.PARK_MY_VEHICLE -> ActionRow(
                tone = identityTone(story.watch),
                icon = Icons.Rounded.Visibility,
                title = stringResource(Res.string.home_det_watching_title, story.vehicleName),
                subtitle = stringResource(
                    if (story.isParked) Res.string.home_det_watching_parked_sub else Res.string.home_det_watching_bt_sub,
                ),
                primaryLabel = null,
                onPrimary = {},
                modifier = modifier,
            )

            ParkedWatchBadge.WATCHING_FRAGILE -> ActionRow(
                tone = amber,
                icon = Icons.Rounded.Visibility,
                title = stringResource(Res.string.home_det_watching_title, story.vehicleName),
                subtitle = stringResource(Res.string.home_det_watching_fragile_sub),
                primaryLabel = stringResource(Res.string.home_det_watching_fortify_cta),
                onPrimary = onRequestBatteryExemption,
                primaryStacksBelow = true,
                modifier = modifier,
            )

            ParkedWatchBadge.WATCH_INTERRUPTED -> ActionRow(
                tone = error,
                icon = Icons.Rounded.VisibilityOff,
                title = stringResource(Res.string.home_det_watch_interrupted_title, story.vehicleName),
                subtitle = stringResource(Res.string.home_det_watch_interrupted_sub),
                primaryLabel = stringResource(Res.string.home_det_watch_interrupted_cta),
                onPrimary = onResumeWatch,
                primaryStacksBelow = true,
                modifier = modifier,
            )
        }

        DetectionStory.Hidden -> Unit
    }
}

/** Semantic colour bundle for one severity tone, sourced from the [MaterialTheme] colour scheme. */
private data class Tone(
    val accent: Color,
    val onAccent: Color,
    val container: Color,
    val onContainer: Color,
    val isError: Boolean,
)

@Composable
private fun ActionRow(
    tone: Tone,
    icon: ImageVector,
    title: String,
    subtitle: String,
    /** Null = quiet story row: same card skeleton, no CTA. [UX-PARKED-STATE-001] */
    primaryLabel: String?,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {},
    /** Single-CTA stories that stack the button FULL-WIDTH below (title + subtitle keep the full row
     *  width, never truncate) — for the alert watch rows whose copy + car name won't fit inline.
     *  [DET-WATCH-HONEST-001] */
    primaryStacksBelow: Boolean = false,
    /**
     * The secondary CTA is the NEGATIVE ANSWER to the title's question, not a second action — so it
     * drops the accent tint and reads as the quiet way out. [DET-ASK-STATE-001]
     *
     * Off by default because the other two-CTA row (the cold start: "mark my spot" / "I'm driving")
     * offers two real actions, and there the shared tone is right — neither is a refusal of the
     * other. This flag names the SEMANTICS, not the colour: only a yes/no answer sets it.
     */
    secondaryIsDecline: Boolean = false,
    /**
     * [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] Optional whole-card tap, for rows whose content
     * is ABOUT a place the map can show. Null (the default) leaves the card inert, so no existing
     * story grows an invisible affordance by accident.
     */
    onClick: (() -> Unit)? = null,
) {
    val cardColor = if (tone.isError) tone.container else MaterialTheme.colorScheme.surfaceContainerHigh
    // Error: a stronger accent-tinted border (urgent). Otherwise the SAME neutral card border the
    // rest of the sheet sections use, so it reads as one card family. The colour stays on the accent
    // bar — a coloured border on top would be a third accent and over-saturate the row. [DET-READY-001h]
    val border = if (tone.isError) {
        BorderStroke(BORDER_DP.dp, tone.accent.copy(alpha = ERROR_BORDER_ALPHA))
    } else {
        BorderStroke(PapBorders.thin, MaterialTheme.colorScheme.outline.copy(alpha = PapBorders.DEFAULT_OUTLINE_ALPHA))
    }

    val cardModifier = modifier
        .fillMaxWidth()
        // The card is one target that CONTAINS two buttons, so it announces itself as such instead
        // of leaving a screen reader three peers with no hierarchy.
        .then(onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier)

    Surface(
        shape = PapShapes.card,
        color = cardColor,
        border = border,
        modifier = cardModifier,
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Accent bar spans the full row height.
            Box(
                modifier = Modifier
                    .width(ACCENT_BAR_DP.dp)
                    .fillMaxHeight()
                    .background(tone.accent),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = CONTENT_START_DP.dp,
                        end = CONTENT_END_DP.dp,
                        top = CONTENT_VERTICAL_DP.dp,
                        bottom = CONTENT_VERTICAL_DP.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(CTA_ROW_GAP_DP.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CONTENT_GAP_DP.dp),
                ) {
                    IconTile(icon = icon, tone = tone)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = PaparcarType.current.rowTitle,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = subtitle,
                            style = PaparcarType.current.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // Single-CTA states keep the button INLINE on the right (the subtitle wraps to two
                    // lines instead of truncating). Filled on error, tonal otherwise. [DET-READY-001h]
                    if (secondaryLabel == null && primaryLabel != null && !primaryStacksBelow) {
                        CtaPill(
                            label = primaryLabel,
                            container = if (tone.isError) tone.accent else tone.container,
                            content = if (tone.isError) tone.onAccent else tone.onContainer,
                            onClick = onPrimary,
                        )
                    }
                }
                // Single CTA stacked full-width below → title + subtitle got the full row width above.
                // For the alert watch rows (fragile / interrupted). [DET-WATCH-HONEST-001]
                if (secondaryLabel == null && primaryLabel != null && primaryStacksBelow) {
                    CtaPill(
                        label = primaryLabel,
                        container = if (tone.isError) tone.accent else tone.container,
                        content = if (tone.isError) tone.onAccent else tone.onContainer,
                        onClick = onPrimary,
                        modifier = Modifier.fillMaxWidth(),
                        fillWidth = true,
                    )
                }
                // Only the two-CTA cold-start stacks its actions full-width below (both need the room).
                if (secondaryLabel != null && primaryLabel != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CONTENT_GAP_SM_DP.dp),
                    ) {
                        CtaPill(
                            label = secondaryLabel,
                            // A refusal wears no brand colour: green is action, and declining is
                            // not the action we are offering. [UI-COLOR-DOCTRINE-001]
                            container = if (secondaryIsDecline) {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            } else {
                                tone.container
                            },
                            content = if (secondaryIsDecline) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                tone.onContainer
                            },
                            onClick = onSecondary,
                            modifier = Modifier.weight(1f),
                            fillWidth = true,
                        )
                        CtaPill(
                            label = primaryLabel,
                            container = tone.accent,
                            content = tone.onAccent,
                            onClick = onPrimary,
                            modifier = Modifier.weight(1f),
                            fillWidth = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IconTile(icon: ImageVector, tone: Tone) {
    // Error = filled (loud); otherwise a light tint so the accent bar carries the colour and the two
    // accent elements don't compete. Uses the shared PapIconTile with tone-driven colours.
    PapIconTile(
        icon = icon,
        size = TILE_DP.dp,
        shape = RoundedCornerShape(TILE_RADIUS_DP.dp),
        container = if (tone.isError) tone.accent else tone.accent.copy(alpha = TILE_TINT_ALPHA),
        tint = if (tone.isError) tone.onAccent else tone.accent,
        iconSize = TILE_ICON_DP.dp,
    )
}

@Composable
private fun CtaPill(
    label: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Fill the available width and centre the label — used by stacked (full-width) CTAs. */
    fillWidth: Boolean = false,
) {
    Surface(
        shape = PapShapes.chip,
        color = container,
        onClick = onClick,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                .height(CTA_HEIGHT_DP.dp)
                .padding(horizontal = CTA_PADDING_DP.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = PaparcarType.current.cta,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val ACCENT_BAR_DP = 4
private const val TILE_DP = 42
private const val TILE_RADIUS_DP = 13
private const val TILE_ICON_DP = 22
private const val TILE_TINT_ALPHA = 0.16f
private const val CONTENT_START_DP = 14
private const val CONTENT_END_DP = 14
private const val CONTENT_VERTICAL_DP = 12
private const val CONTENT_GAP_DP = 13
private const val CONTENT_GAP_SM_DP = 8
private const val CTA_ROW_GAP_DP = 12
private const val CTA_HEIGHT_DP = 40
private const val CTA_PADDING_DP = 16
private const val BORDER_DP = 1
private const val ERROR_BORDER_ALPHA = 0.6f
// Tonal container for the car-blue info tone — accent at low alpha, same language as the watch badge.
private const val INFO_CONTAINER_ALPHA = 0.16f
