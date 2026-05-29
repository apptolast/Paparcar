package io.apptolast.paparcar.presentation.vehicles.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.presentation.vehicles.HistoryFilter
import io.apptolast.paparcar.ui.components.chips.PaparcarFilterChip
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.history_filter_all
import paparcar.composeapp.generated.resources.history_filter_last_3_months
import paparcar.composeapp.generated.resources.history_filter_this_month
import paparcar.composeapp.generated.resources.history_filter_this_week

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
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        filters.forEach { (filter, label) ->
            PaparcarFilterChip(
                label = label,
                selected = activeFilter == filter,
                onClick = { onFilterSelected(filter) },
            )
        }
    }
}
