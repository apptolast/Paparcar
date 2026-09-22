package com.rndeveloper.paparcar.presentation.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.stats.insights.InsightKind
import com.rndeveloper.paparcar.domain.stats.insights.StatsInsight
import com.rndeveloper.paparcar.presentation.util.durationText
import com.rndeveloper.paparcar.presentation.vehicles.DAY_FULL_RES
import com.rndeveloper.paparcar.ui.components.PapOutlinedCard
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.stats_insight_costliest_place
import paparcar.composeapp.generated.resources.stats_insight_easiest_time
import paparcar.composeapp.generated.resources.stats_insight_evidence
import paparcar.composeapp.generated.resources.stats_insight_place_better
import paparcar.composeapp.generated.resources.stats_insight_place_worse
import paparcar.composeapp.generated.resources.stats_insight_trend_down
import paparcar.composeapp.generated.resources.stats_insight_trend_up
import paparcar.composeapp.generated.resources.stats_insight_worst_time
import paparcar.composeapp.generated.resources.stats_insights_title

/**
 * What the app CONCLUDED about this period, with the evidence under each sentence.
 * [STATS-AN-INSIGHT-IS-A-VERDICT-WITH-ITS-EVIDENCE-001]
 *
 * First card of the panel on purpose: the conclusion goes before the proof, and everything below it
 * — the chart, the figures, the places, the grid — IS the proof. A person who only reads the top of
 * the screen still leaves with the finding.
 *
 * ⛔ It is not composed at all when there is nothing to say. A card reading "not enough data yet"
 * would take the place of the one that will have something, every time, for every period below the
 * thresholds — and the thresholds are most of the early life of an account.
 */
@Composable
internal fun StatsInsightsCard(insights: List<StatsInsight>) {
    // A trend sentence needs a DIRECTION, and the direction needs both sides measured. An insight
    // that lost its baseline on the way here is a producer bug — skipped, never rendered with an
    // invented arrow. [STATS-EVERY-NUMBER-CITES-ITS-EVIDENCE-001]
    val renderable = insights.filter {
        it.kind !in setOf(InsightKind.SEARCH_TIME_TREND, InsightKind.PLACE_TREND) ||
            it.values.isUp != null
    }
    if (renderable.isEmpty()) return
    PapOutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(CARD_PAD_DP.dp)) {
            Text(
                text = stringResource(Res.string.stats_insights_title),
                style = PaparcarType.current.cardTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(TITLE_GAP_DP.dp))
            Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP_DP.dp)) {
                renderable.forEach { InsightRow(it) }
            }
        }
    }
}

@Composable
private fun InsightRow(insight: StatsInsight) {
    Row(horizontalArrangement = Arrangement.spacedBy(ICON_GAP_DP.dp)) {
        Icon(
            imageVector = insight.icon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(ICON_DP.dp).padding(top = ICON_TOP_PAD_DP.dp),
        )
        Column {
            Text(
                text = insight.sentence(),
                style = PaparcarType.current.body,
                color = MaterialTheme.colorScheme.onSurface,
            )
            // ⛔ The n, always. A sentence about someone's week without its sample is a hunch
            // wearing a fact's clothes — the same rule that makes every measured sum show its
            // coverage. [STATS-A-MEASURE-CARRIES-ITS-COVERAGE-001]
            Text(
                text = pluralStringResource(
                    Res.plurals.stats_insight_evidence,
                    insight.samples,
                    insight.samples,
                ),
                style = PaparcarType.current.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The sentence. All the copy lives here and none of it in domain: the engine decides WHAT is true,
 * the surface decides how it is said — in nine languages.
 */
@Composable
private fun StatsInsight.sentence(): String {
    val days = DAY_FULL_RES.map { stringResource(it) }
    val value = values.valueMs?.let { durationText(it) }.orEmpty()
    val baseline = values.baselineMs?.let { durationText(it) }.orEmpty()
    return when (kind) {
        InsightKind.WORST_TIME_TO_PARK -> stringResource(
            Res.string.stats_insight_worst_time,
            days[values.dayIndex ?: 0],
            bandLabel(),
            value,
        )
        InsightKind.EASIEST_TIME_TO_PARK -> stringResource(
            Res.string.stats_insight_easiest_time,
            days[values.dayIndex ?: 0],
            bandLabel(),
            value,
        )
        InsightKind.COSTLIEST_PLACE -> stringResource(
            Res.string.stats_insight_costliest_place,
            values.placeName.orEmpty(),
            value,
            baseline,
        )
        // The direction is a different SENTENCE, not a sign in front of a number: "4 min more" and
        // "4 min less" are not one phrase with a switch, in any of the nine languages.
        InsightKind.SEARCH_TIME_TREND -> stringResource(
            if (values.isUp == true) Res.string.stats_insight_trend_up else Res.string.stats_insight_trend_down,
            values.deltaMs?.let { durationText(it) }.orEmpty(),
        )
        InsightKind.PLACE_TREND -> stringResource(
            if (values.isUp == true) {
                Res.string.stats_insight_place_worse
            } else {
                Res.string.stats_insight_place_better
            },
            values.placeName.orEmpty(),
            values.deltaMs?.let { durationText(it) }.orEmpty(),
        )
    }
}

/**
 * "16–20" — a clock reading, not copy anyone translates. Same call `formatClockTime` already made.
 *
 * ⛔ The last band ends at 24, not at 0. Wrapping it round the clock printed "20–0", which reads as
 * a typo: a RANGE runs to the end of the day, and "midnight" as an end is 24. (Seen on device.)
 */
private fun StatsInsight.bandLabel(): String {
    val start = values.bandStartHour ?: 0
    return "$start–${start + BAND_HOURS}"
}

/** The glyph says which KIND of finding it is — the app's existing vocabulary, nothing new. */
private fun StatsInsight.icon(): ImageVector = when (kind) {
    InsightKind.WORST_TIME_TO_PARK -> Icons.Rounded.Schedule
    InsightKind.EASIEST_TIME_TO_PARK -> Icons.Rounded.TravelExplore
    InsightKind.COSTLIEST_PLACE -> Icons.Rounded.Place
    InsightKind.SEARCH_TIME_TREND, InsightKind.PLACE_TREND ->
        if (values.isUp == true) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown
}

private const val CARD_PAD_DP = 16
private const val TITLE_GAP_DP = 12
private const val ROW_GAP_DP = 14
private const val ICON_GAP_DP = 10
private const val ICON_DP = 20
private const val ICON_TOP_PAD_DP = 2
private const val BAND_HOURS = 4
