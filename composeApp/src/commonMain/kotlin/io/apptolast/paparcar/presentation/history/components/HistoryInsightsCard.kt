package io.apptolast.paparcar.presentation.history.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.presentation.history.BodySmall
import io.apptolast.paparcar.presentation.history.DAY_SHORT_RES
import io.apptolast.paparcar.presentation.history.HistoryStatsData
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.history_insights_avg_week
import paparcar.composeapp.generated.resources.history_insights_peak_day
import paparcar.composeapp.generated.resources.history_insights_reliability
import paparcar.composeapp.generated.resources.history_insights_title
import paparcar.composeapp.generated.resources.history_insights_top_street

/**
 * Insights card (v1 redesign) — "TU PATRÓN" header + 3 chips with primary
 * values + favorite-street featured row (primaryContainer-tinted).
 */
@Composable
internal fun HistoryInsightsCard(
    stats: HistoryStatsData,
    modifier: Modifier = Modifier,
) {
    val dayLabels = DAY_SHORT_RES.map { stringResource(it) }
    val noData = "—"

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(CARD_CORNER_DP.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = BORDER_ALPHA),
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(Res.string.history_insights_title),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = HEADER_ALPHA),
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InsightChip(
                    label = stringResource(Res.string.history_insights_avg_week),
                    value = stats.avgSessionsPerWeek?.let { "%.1f".format(it) } ?: noData,
                    modifier = Modifier.weight(1f),
                )
                InsightChip(
                    label = stringResource(Res.string.history_insights_peak_day),
                    value = stats.mostActiveDayOfWeek
                        ?.let { dayLabels.getOrNull(it - 1) } ?: noData,
                    modifier = Modifier.weight(1f),
                )
                InsightChip(
                    label = stringResource(Res.string.history_insights_reliability),
                    value = stats.avgReliabilityPct?.let { "$it%" } ?: noData,
                    modifier = Modifier.weight(1f),
                )
            }
            if (stats.favoriteStreet != null) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(STREET_CORNER_DP.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = STREET_BG_ALPHA),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(Res.string.history_insights_top_street),
                            style = BodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = STREET_LABEL_ALPHA),
                        )
                        Text(
                            text = stats.favoriteStreet,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = CHIP_BG_ALPHA),
        shape = RoundedCornerShape(CHIP_CORNER_DP.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = BodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = CHIP_LABEL_ALPHA),
            )
        }
    }
}

private const val CARD_CORNER_DP = 16
private const val CHIP_CORNER_DP = 10
private const val STREET_CORNER_DP = 10
private const val BORDER_ALPHA = 0.5f
private const val HEADER_ALPHA = 0.55f
private const val CHIP_BG_ALPHA = 0.5f
private const val CHIP_LABEL_ALPHA = 0.5f
private const val STREET_BG_ALPHA = 0.5f
private const val STREET_LABEL_ALPHA = 0.6f
