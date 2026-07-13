package io.apptolast.paparcar.presentation.permissions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.DetectionTier
import io.apptolast.paparcar.ui.components.PapOutlinedCard
import io.apptolast.paparcar.ui.theme.PapBorders
import io.apptolast.paparcar.ui.theme.PaparcarSpacing
import io.apptolast.paparcar.ui.theme.PaparcarType
import io.apptolast.paparcar.ui.theme.outlineSubtle
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.permissions_tier_assisted_name
import paparcar.composeapp.generated.resources.permissions_tier_assisted_plus_name
import paparcar.composeapp.generated.resources.permissions_tier_assisted_plus_promise
import paparcar.composeapp.generated.resources.permissions_tier_assisted_promise
import paparcar.composeapp.generated.resources.permissions_tier_automatic_name
import paparcar.composeapp.generated.resources.permissions_tier_automatic_promise
import paparcar.composeapp.generated.resources.permissions_tier_status_eyebrow

private val DASH_WIDTH = 16.dp
private val DASH_HEIGHT = 6.dp
private const val TIER_COUNT = 3

/**
 * Status card at the top of the Permissions screen: the honest detection [DetectionTier] the user
 * is on RIGHT NOW — eyebrow + tier name + promise, with a 3-dash gauge on the right. [DET-TIERS-001]
 *
 * The card BORDER grades with the tier (brand green for Automatic, a softer green for Assisted+, a
 * neutral hairline for Assisted) so the level is legible at a glance. The tier name stays neutral —
 * Assisted is the honest baseline, NOT an alert, so no red/amber ever appears here; the green only
 * signals progress toward Automatic. Bluetooth is the only jump to Automatic.
 */
@Composable
internal fun DetectionTierStatusCard(
    tier: DetectionTier,
    modifier: Modifier = Modifier,
) {
    PapOutlinedCard(
        modifier = modifier.fillMaxWidth(),
        border = tier.border(),
    ) {
        Column(modifier = Modifier.padding(PaparcarSpacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.permissions_tier_status_eyebrow).uppercase(),
                        style = PaparcarType.current.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(PaparcarSpacing.xs))
                    Text(
                        text = stringResource(tier.nameRes()),
                        style = PaparcarType.current.cardTitle,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.width(PaparcarSpacing.md))
                TierGauge(rank = tier.rank())
            }
            Spacer(Modifier.height(PaparcarSpacing.sm))
            Text(
                text = stringResource(tier.promiseRes()),
                style = PaparcarType.current.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Three dashes on the right: filled = rank, in brand green; the empty ones stay muted. */
@Composable
private fun TierGauge(rank: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(PaparcarSpacing.xs)) {
        repeat(TIER_COUNT) { index ->
            Surface(
                shape = RoundedCornerShape(DASH_HEIGHT / 2),
                color = if (index < rank) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.width(DASH_WIDTH).height(DASH_HEIGHT),
            ) {}
        }
    }
}

// ── Tier → presentation mapping. Kept in the presentation layer (domain is Kotlin-pure, no colors
//    or string resources). [DET-TIERS-001] ──

/** Border grades with the tier: strong green (Automatic) → soft green (Assisted+) → neutral hairline
 *  (Assisted, the baseline). Green = progress, never a warning. */
@Composable
private fun DetectionTier.border(): BorderStroke = when (this) {
    DetectionTier.AUTOMATIC ->
        BorderStroke(PapBorders.medium, MaterialTheme.colorScheme.primary.copy(alpha = AUTOMATIC_BORDER_ALPHA))
    DetectionTier.ASSISTED_PLUS ->
        BorderStroke(PapBorders.medium, MaterialTheme.colorScheme.primary.copy(alpha = ASSISTED_PLUS_BORDER_ALPHA))
    DetectionTier.ASSISTED -> outlineSubtle
}

private fun DetectionTier.rank(): Int = when (this) {
    DetectionTier.ASSISTED -> 1
    DetectionTier.ASSISTED_PLUS -> 2
    DetectionTier.AUTOMATIC -> 3
}

private fun DetectionTier.nameRes() = when (this) {
    DetectionTier.AUTOMATIC -> Res.string.permissions_tier_automatic_name
    DetectionTier.ASSISTED_PLUS -> Res.string.permissions_tier_assisted_plus_name
    DetectionTier.ASSISTED -> Res.string.permissions_tier_assisted_name
}

private fun DetectionTier.promiseRes() = when (this) {
    DetectionTier.AUTOMATIC -> Res.string.permissions_tier_automatic_promise
    DetectionTier.ASSISTED_PLUS -> Res.string.permissions_tier_assisted_plus_promise
    DetectionTier.ASSISTED -> Res.string.permissions_tier_assisted_promise
}

private const val AUTOMATIC_BORDER_ALPHA = 0.6f
private const val ASSISTED_PLUS_BORDER_ALPHA = 0.35f
