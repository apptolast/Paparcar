@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.presentation.util.SpotReliabilityUiState
import io.apptolast.paparcar.presentation.util.distanceString
import io.apptolast.paparcar.presentation.util.driveTimeString
import io.apptolast.paparcar.ui.theme.PaparcarSpacing
import kotlin.time.Clock
import org.jetbrains.compose.resources.stringResource
import io.apptolast.paparcar.ui.components.TTLIndicator
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_nav_button
import paparcar.composeapp.generated.resources.home_spot_freshness_hours
import paparcar.composeapp.generated.resources.home_spot_freshness_minutes
import paparcar.composeapp.generated.resources.home_spot_freshness_under_min
import paparcar.composeapp.generated.resources.spot_card_en_route
import paparcar.composeapp.generated.resources.spot_card_reliability_high
import paparcar.composeapp.generated.resources.spot_card_reliability_high_desc
import paparcar.composeapp.generated.resources.spot_card_reliability_low
import paparcar.composeapp.generated.resources.spot_card_reliability_low_desc
import paparcar.composeapp.generated.resources.spot_card_reliability_manual
import paparcar.composeapp.generated.resources.spot_card_reliability_manual_desc
import paparcar.composeapp.generated.resources.spot_card_reliability_medium
import paparcar.composeapp.generated.resources.spot_card_reliability_medium_desc
import paparcar.composeapp.generated.resources.spot_signal_gone
import paparcar.composeapp.generated.resources.spot_signal_still_there

// ─────────────────────────────────────────────────────────────────────────────
// Presentation model
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Presentation model consumed by [SpotCard].
 *
 * ViewModels are responsible for mapping the [Spot] domain model +
 * user location to this class before passing it to the composable.
 *
 * @param id             Unique spot identifier (for keys in LazyColumn).
 * @param displayLocation Human-readable address or POI label.
 * @param distanceMeters  Distance from the current user, or null if unknown.
 * @param reportedAtMs    Epoch-ms when the spot was reported (for freshness).
 * @param reliability     Confidence level of the auto-detection.
 * @param enRouteCount    Number of users currently heading to this spot.
 * @param expiresAt       Epoch-ms when this spot expires; 0 = no TTL set.
 */
data class SpotCardData(
    val id: String,
    val displayLocation: String,
    val distanceMeters: Float? = null,
    val reportedAtMs: Long,
    val reliability: SpotReliabilityUiState = SpotReliabilityUiState.HIGH,
    val enRouteCount: Int = 0,
    val expiresAt: Long = 0L,
)

// ─────────────────────────────────────────────────────────────────────────────
// SpotCard
// ─────────────────────────────────────────────────────────────────────────────

private val IconBoxSize   = 44.dp
private val IconBoxRadius = 13.dp

private const val FRESHNESS_VERY_FRESH_MINUTES = 5L
private const val FRESHNESS_FRESH_MINUTES      = 15L
private const val FRESHNESS_HOURS_THRESHOLD    = 60L
private const val DIVIDER_ALPHA                = 0.4f
private val       DIVIDER_THICKNESS            = 0.5.dp
private val       DISTANCE_TEXT_SPACER         = 2.dp

/**
 * Community parking spot card.
 *
 * Design rules:
 *  - Single CTA: "Navigate" button → [onNavigate] callback (no other inline actions).
 *  - Tapping the card body → [onSelect] (selects/highlights on map).
 *  - Reliability and en-route indicators are read-only — parents own all mutations.
 *
 * @param data      Pre-computed presentation model.
 * @param onNavigate Called when the user taps the "Navigate" button.
 *                   The parent is responsible for opening the navigation app
 *                   AND incrementing the en-route count on the spot.
 * @param onSelect  Called when the user taps the card body (not the button).
 *                   Typically centres the map on this spot.
 */
/**
 * Community signal row shown when the card is selected.
 * [onAccept] = "Still there"; [onReject] = "Gone".
 * Pass null for either to hide the corresponding button.
 */
@Composable
fun SpotCard(
    data: SpotCardData,
    onNavigate: () -> Unit,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit = {},
    isSelected: Boolean = false,
    onAccept: (() -> Unit)? = null,
    onReject: (() -> Unit)? = null,
) {
    val ageMinutes = remember(data.reportedAtMs) {
        (Clock.System.now().toEpochMilliseconds() - data.reportedAtMs) / MS_PER_MINUTE
    }

    PapClickableCard(
        onClick = onSelect,
        modifier = modifier.fillMaxWidth(),
        containerColor = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
        else
            MaterialTheme.colorScheme.surface,
        padding = 0.dp,
    ) {
        // ── Top row: icon | location + distance | freshness ──────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PaparcarSpacing.lg, vertical = PaparcarSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PaparcarSpacing.md),
        ) {
            SpotIconBox(isSelected = isSelected)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = data.displayLocation,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (data.distanceMeters != null) {
                    Spacer(Modifier.height(DISTANCE_TEXT_SPACER))
                    Text(
                        text = "${distanceString(data.distanceMeters)}  ·  ${driveTimeString(data.distanceMeters)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (data.expiresAt > 0L) {
                TTLIndicator(expiresAtMs = data.expiresAt)
            } else {
                SpotFreshnessLabel(ageMinutes = ageMinutes)
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = DIVIDER_ALPHA),
            thickness = DIVIDER_THICKNESS,
        )

        // ── Bottom row: reliability badge | en-route | Navigate CTA ──────────
        var reliabilityExpanded by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PaparcarSpacing.lg)
                .padding(top = PaparcarSpacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpotReliabilityBadge(
                    reliability = data.reliability,
                    expanded = reliabilityExpanded,
                    onToggle = { reliabilityExpanded = !reliabilityExpanded },
                )

                if (data.enRouteCount > 0) {
                    Spacer(Modifier.width(PaparcarSpacing.sm))
                    Text(
                        text = stringResource(Res.string.spot_card_en_route, data.enRouteCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }

                Spacer(Modifier.weight(1f))

                PapPrimaryButton(
                    label = stringResource(Res.string.home_nav_button),
                    onClick = onNavigate,
                )
            }

            AnimatedVisibility(
                visible = reliabilityExpanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Text(
                    text = reliabilityDescription(data.reliability),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = PaparcarSpacing.xs, bottom = PaparcarSpacing.sm),
                )
            }

            // Community signal buttons — visible only when the card is selected
            AnimatedVisibility(
                visible = isSelected && (onAccept != null || onReject != null),
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                SpotSignalRow(
                    onAccept = onAccept,
                    onReject = onReject,
                    modifier = Modifier.padding(bottom = PaparcarSpacing.sm),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Private sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SpotIconBox(isSelected: Boolean) {
    val bgColor = if (isSelected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    val textColor = if (isSelected)
        MaterialTheme.colorScheme.onPrimary
    else
        MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .size(IconBoxSize)
            .clip(RoundedCornerShape(IconBoxRadius))
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "P",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = textColor,
        )
    }
}

@Composable
private fun SpotFreshnessLabel(ageMinutes: Long) {
    val label = when {
        ageMinutes < 1L                          -> stringResource(Res.string.home_spot_freshness_under_min)
        ageMinutes < FRESHNESS_HOURS_THRESHOLD   -> stringResource(Res.string.home_spot_freshness_minutes, ageMinutes.toInt())
        else                                     -> stringResource(Res.string.home_spot_freshness_hours, (ageMinutes / FRESHNESS_HOURS_THRESHOLD).toInt())
    }
    val containerColor = when {
        ageMinutes < FRESHNESS_VERY_FRESH_MINUTES -> MaterialTheme.colorScheme.primaryContainer
        ageMinutes < FRESHNESS_FRESH_MINUTES      -> MaterialTheme.colorScheme.secondaryContainer
        else                                      -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        ageMinutes < FRESHNESS_VERY_FRESH_MINUTES -> MaterialTheme.colorScheme.primary
        ageMinutes < FRESHNESS_FRESH_MINUTES      -> MaterialTheme.colorScheme.secondary
        else                                      -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    }
    PapBadge(
        label = label,
        containerColor = containerColor,
        contentColor = contentColor,
    )
}

@Composable
private fun reliabilityDescription(reliability: SpotReliabilityUiState): String = when (reliability) {
    SpotReliabilityUiState.HIGH   -> stringResource(Res.string.spot_card_reliability_high_desc)
    SpotReliabilityUiState.MEDIUM -> stringResource(Res.string.spot_card_reliability_medium_desc)
    SpotReliabilityUiState.LOW    -> stringResource(Res.string.spot_card_reliability_low_desc)
    SpotReliabilityUiState.MANUAL -> stringResource(Res.string.spot_card_reliability_manual_desc)
}

@Composable
private fun SpotReliabilityBadge(
    reliability: SpotReliabilityUiState,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val toggleModifier = Modifier
        .clip(MaterialTheme.shapes.extraSmall)
        .clickable(onClick = onToggle)
        .semantics { role = Role.Button }

    when (reliability) {
        SpotReliabilityUiState.HIGH   -> PapHighReliabilityBadge(
            label = stringResource(Res.string.spot_card_reliability_high),
            modifier = toggleModifier,
        )
        SpotReliabilityUiState.MEDIUM -> PapMediumReliabilityBadge(
            label = stringResource(Res.string.spot_card_reliability_medium),
            modifier = toggleModifier,
        )
        SpotReliabilityUiState.LOW    -> PapLowReliabilityBadge(
            label = stringResource(Res.string.spot_card_reliability_low),
            modifier = toggleModifier,
        )
        SpotReliabilityUiState.MANUAL -> PapManualReportBadge(
            label = stringResource(Res.string.spot_card_reliability_manual),
            modifier = toggleModifier,
        )
    }
}

@Composable
private fun SpotSignalRow(
    onAccept: (() -> Unit)?,
    onReject: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PaparcarSpacing.sm),
    ) {
        if (onAccept != null) {
            PapSecondaryButton(
                label = stringResource(Res.string.spot_signal_still_there),
                onClick = onAccept,
                modifier = Modifier.weight(1f),
            )
        }
        if (onReject != null) {
            PapSecondaryButton(
                label = stringResource(Res.string.spot_signal_gone),
                onClick = onReject,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
