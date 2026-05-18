package io.apptolast.paparcar.presentation.history.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.presentation.history.HistoryFilter
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.history_filter_all
import paparcar.composeapp.generated.resources.history_filter_last_3_months
import paparcar.composeapp.generated.resources.history_filter_this_month
import paparcar.composeapp.generated.resources.history_filter_this_week

/**
 * Filter bar (v1 redesign) — custom pill chips matching the Home pill style.
 * Active = 1px primary border + primaryContainer bg; inactive = outlineVariant
 * border + transparent bg.
 */
@Composable
internal fun HistoryFilterBar(
    activeFilter: HistoryFilter,
    onFilterSelected: (HistoryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filters = listOf(
        HistoryFilter.All to stringResource(Res.string.history_filter_all),
        HistoryFilter.ThisWeek to stringResource(Res.string.history_filter_this_week),
        HistoryFilter.ThisMonth to stringResource(Res.string.history_filter_this_month),
        HistoryFilter.Last3Months to stringResource(Res.string.history_filter_last_3_months),
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        filters.forEach { (filter, label) ->
            FilterPill(
                label = label,
                active = activeFilter == filter,
                onClick = { onFilterSelected(filter) },
            )
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (active) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.outlineVariant.copy(alpha = INACTIVE_BORDER_ALPHA)
    val bgColor = if (active) MaterialTheme.colorScheme.primaryContainer
                  else Color.Transparent
    val textColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = INACTIVE_TEXT_ALPHA)

    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(PILL_RADIUS_DP.dp))
            .background(bgColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(PILL_RADIUS_DP.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = textColor,
    )
}

private const val PILL_RADIUS_DP = 999
private const val INACTIVE_BORDER_ALPHA = 0.6f
private const val INACTIVE_TEXT_ALPHA = 0.6f
