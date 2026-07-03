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
import androidx.compose.material.icons.rounded.SensorsOff
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
import io.apptolast.paparcar.presentation.home.model.DetectionUiState
import io.apptolast.paparcar.ui.theme.PapBorders
import io.apptolast.paparcar.ui.theme.PapShapes
import io.apptolast.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_det_awaiting_cta_primary
import paparcar.composeapp.generated.resources.home_det_awaiting_cta_secondary
import paparcar.composeapp.generated.resources.home_det_awaiting_sub
import paparcar.composeapp.generated.resources.home_det_awaiting_title
import paparcar.composeapp.generated.resources.home_det_core_cta
import paparcar.composeapp.generated.resources.home_det_core_sub
import paparcar.composeapp.generated.resources.home_det_core_title
import paparcar.composeapp.generated.resources.home_det_novehicle_cta
import paparcar.composeapp.generated.resources.home_det_novehicle_sub
import paparcar.composeapp.generated.resources.home_det_novehicle_title
import paparcar.composeapp.generated.resources.home_det_producer_cta
import paparcar.composeapp.generated.resources.home_det_producer_sub
import paparcar.composeapp.generated.resources.home_det_producer_title

/**
 * The Home **detection action surface** — a single accent-bar row that communicates the
 * automatic-detection state and offers the relevant action. [DET-READY-001h]
 *
 * Renders for the four action states only ([DetectionUiState.NoVehicle], [DetectionUiState.Inactive],
 * [DetectionUiState.BlockedCore], [DetectionUiState.AwaitingFirstPark]); [Parked] is owned by the existing parked-car card and
 * [Monitoring]/[Silent] render nothing here (Monitoring uses its own ephemeral pill).
 *
 * Prominence is **severity-adaptive**: a CORE block (the app barely works) is an error-toned,
 * filled, bordered row; the PRODUCER upsell and "add a car" are calmer amber tonal rows; the
 * cold-start prompt is an info-blue row with two actions. The single reusable row only changes
 * icon, copy, tone and CTAs by state.
 */
@Composable
internal fun HomeDetectionSurface(
    state: DetectionUiState,
    onAddVehicle: () -> Unit,
    onOpenPermissions: () -> Unit,
    onMarkSpot: () -> Unit,
    onStartDrivingDetection: () -> Unit,
    onActivateDetection: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Whether the cold-start row offers the secondary "I'm driving" action. Off until the manual
     * Coordinator arming (DET-G-01b) exists — there is no infra to honour it yet. [DET-READY-001h]
     */
    allowDrivingDetection: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    val amber = Tone(cs.secondary, cs.onSecondary, cs.secondaryContainer, cs.onSecondaryContainer, isError = false)
    val error = Tone(cs.error, cs.onError, cs.errorContainer, cs.onErrorContainer, isError = true)
    val info = Tone(cs.tertiary, cs.onTertiary, cs.tertiaryContainer, cs.onTertiaryContainer, isError = false)

    when (state) {
        DetectionUiState.NoVehicle -> ActionRow(
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
        DetectionUiState.Inactive -> ActionRow(
            tone = info,
            icon = Icons.Rounded.SensorsOff,
            title = stringResource(Res.string.home_det_producer_title),
            subtitle = stringResource(Res.string.home_det_producer_sub),
            primaryLabel = stringResource(Res.string.home_det_producer_cta),
            onPrimary = onActivateDetection,
            modifier = modifier,
        )

        DetectionUiState.BlockedCore -> ActionRow(
            tone = error,
            icon = Icons.Rounded.LocationOff,
            title = stringResource(Res.string.home_det_core_title),
            subtitle = stringResource(Res.string.home_det_core_sub),
            primaryLabel = stringResource(Res.string.home_det_core_cta),
            onPrimary = onOpenPermissions,
            modifier = modifier,
        )

        DetectionUiState.AwaitingFirstPark -> ActionRow(
            tone = info,
            icon = Icons.Rounded.AddLocationAlt,
            title = stringResource(Res.string.home_det_awaiting_title),
            subtitle = stringResource(Res.string.home_det_awaiting_sub),
            primaryLabel = stringResource(Res.string.home_det_awaiting_cta_primary),
            onPrimary = onMarkSpot,
            secondaryLabel = if (allowDrivingDetection) stringResource(Res.string.home_det_awaiting_cta_secondary) else null,
            onSecondary = onStartDrivingDetection,
            modifier = modifier,
        )

        // No surface: Parked is the existing parked-car card; Monitoring is the pill; Silent is nothing.
        DetectionUiState.Parked,
        DetectionUiState.Monitoring,
        DetectionUiState.Silent -> Unit
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
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {},
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
                            maxLines = 1,
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
                    if (secondaryLabel == null) {
                        CtaPill(
                            label = primaryLabel,
                            container = if (tone.isError) tone.accent else tone.container,
                            content = if (tone.isError) tone.onAccent else tone.onContainer,
                            onClick = onPrimary,
                        )
                    }
                }
                // Only the two-CTA cold-start stacks its actions full-width below (both need the room).
                if (secondaryLabel != null) {
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
