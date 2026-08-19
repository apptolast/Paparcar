package io.apptolast.paparcar.presentation.home.sections.sheet.components

import io.apptolast.paparcar.ui.components.PapIconTile
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.NotListedLocation
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
import io.apptolast.paparcar.presentation.home.model.DetectionStory
import io.apptolast.paparcar.presentation.home.model.ParkedWatchBadge
import io.apptolast.paparcar.ui.theme.PapBorders
import io.apptolast.paparcar.ui.theme.papCarBlue
import io.apptolast.paparcar.ui.theme.PapShapes
import io.apptolast.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_det_awaiting_cta_primary
import paparcar.composeapp.generated.resources.home_det_awaiting_cta_secondary
import paparcar.composeapp.generated.resources.home_det_awaiting_sub
import paparcar.composeapp.generated.resources.home_det_awaiting_title
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
 * Renders the one [DetectionStory] resolved by `resolveDetectionStory`: the four action stories
 * keep the loud accent-bar row (severity-adaptive: error-toned CORE block, calm amber upsells,
 * info-blue cold start); [DetectionStory.Driving] and [DetectionStory.Watching] render a discreet
 * one-line status — the happy path speaks instead of going mute. [DetectionStory.Hidden] renders
 * nothing.
 */
@Composable
internal fun HomeDetectionSurface(
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
    /**
     * [DET-NUDGE-PERSIST-001] An unanswered "where did you leave your car?" nudge is pending —
     * render its row INSTEAD of the regular state row (a lost parking record outranks upsells;
     * only a CORE permission block, where the app barely works, still wins).
     */
    showParkNudge: Boolean = false,
    onMarkNudgeSpot: () -> Unit = {},
    onDismissNudge: () -> Unit = {},
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
    // vehicle wear that vehicle's identity colour (green = active detection, blue = Bluetooth) —
    // the same colour its name wears in the chip and the garage. Tonal container = accent at low
    // alpha, same language as the watch badge. [UI-COLOR-DOCTRINE-001]
    val brand = Tone(cs.primary, cs.onPrimary, cs.primary.copy(alpha = INFO_CONTAINER_ALPHA), cs.primary, isError = false)
    val carBlue = papCarBlue
    val bluetooth = Tone(carBlue, cs.surface, carBlue.copy(alpha = INFO_CONTAINER_ALPHA), carBlue, isError = false)
    fun methodTone(viaBluetooth: Boolean) = if (viaBluetooth) bluetooth else brand

    if (showParkNudge && story != DetectionStory.BlockedCore) {
        ActionRow(
            tone = amber,
            icon = Icons.Rounded.NotListedLocation,
            title = stringResource(Res.string.home_nudge_title),
            subtitle = stringResource(Res.string.home_nudge_sub),
            primaryLabel = stringResource(Res.string.home_nudge_cta),
            onPrimary = onMarkNudgeSpot,
            secondaryLabel = stringResource(Res.string.home_nudge_dismiss),
            onSecondary = onDismissNudge,
            modifier = modifier,
        )
        return
    }

    when (story) {
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
            tone = methodTone(story.viaBluetooth),
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
            // Stacked full-width, not inline: this row's copy carries the CAR NAME, so an inline
            // pill steals the width the title needs and wraps "Conduciendo tu Toyota Corolla" onto
            // two lines (longer names then truncate). Same reason the alert watch rows stack.
            // [DET-WATCH-HONEST-001]
            primaryStacksBelow = true,
            modifier = modifier,
        )

        // Honest watch line: the vehicle's identity colour + no CTA only when the watch is genuinely
        // live; amber warning with an "activate" CTA when fragile; error "reactivate" when the OS
        // killed it. Each CTA pulls its OWN lever — the exemption fortifies a live-but-fragile watch,
        // the resume rebuilds a dead one; they are not interchangeable.
        // [DET-WATCH-HONEST-001] [DET-WATCH-REACTIVATE-001] [UI-COLOR-DOCTRINE-001]
        is DetectionStory.Watching -> when (story.watchBadge) {
            ParkedWatchBadge.WATCHING, ParkedWatchBadge.PARK_MY_VEHICLE -> ActionRow(
                tone = methodTone(story.viaBluetooth),
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

    Surface(
        shape = PapShapes.card,
        color = cardColor,
        border = border,
        modifier = modifier.fillMaxWidth(),
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
                            fontWeight = FontWeight.SemiBold,
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
                            container = tone.container,
                            content = tone.onContainer,
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
                fontWeight = FontWeight.SemiBold,
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
