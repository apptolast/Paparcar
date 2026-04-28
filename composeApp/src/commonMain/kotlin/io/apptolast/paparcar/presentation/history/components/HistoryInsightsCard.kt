package io.apptolast.paparcar.presentation.history.components

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.presentation.history.BodySmall
import io.apptolast.paparcar.presentation.history.DAY_SHORT_RES
import io.apptolast.paparcar.presentation.history.HistoryStatsData
import io.apptolast.paparcar.presentation.history.TitleBody
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.history_insights_avg_week
import paparcar.composeapp.generated.resources.history_insights_peak_day
import paparcar.composeapp.generated.resources.history_insights_reliability
import paparcar.composeapp.generated.resources.history_insights_title
import paparcar.composeapp.generated.resources.history_insights_top_street

@Composable
internal fun HistoryInsightsCard(
    stats: HistoryStatsData,
    modifier: Modifier = Modifier,
) {
    val dayLabels = DAY_SHORT_RES.map { stringResource(it) }
    val noData = "—"

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(Res.string.history_insights_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
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
                InsightChip(
                    label = stringResource(Res.string.history_insights_top_street),
                    value = stats.favoriteStreet,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        }
    }
}

@Composable
private fun InsightChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = TitleBody,
                color = MaterialTheme.colorScheme.primary,
                maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = BodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            )
        }
    }
}
