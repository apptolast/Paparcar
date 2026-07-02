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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Schedule
import io.apptolast.paparcar.presentation.vehicles.HistoryFilter
import io.apptolast.paparcar.ui.components.chips.PaparcarFilterChip
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.history_filter_all
import paparcar.composeapp.generated.resources.history_filter_last_3_months
import paparcar.composeapp.generated.resources.history_filter_this_month
import paparcar.composeapp.generated.resources.history_filter_this_week

// Width of the right-edge scroll-hint fade on the filter bar. [VEHICLES-REDESIGN-001]
private const val FILTER_FADE_WIDTH_DP = 28

private val FILTER_ICONS = mapOf(
    HistoryFilter.All to Icons.Rounded.GridView,
    HistoryFilter.ThisWeek to Icons.Rounded.DateRange,
    HistoryFilter.ThisMonth to Icons.Rounded.CalendarMonth,
    HistoryFilter.Last3Months to Icons.Rounded.Schedule,
)

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

    val scrollState = rememberScrollState()
    // Right-edge fade to the bar background, signalling there are more filter chips off-screen — same
    // treatment as Home's size filter bar. Drawn BEFORE horizontalScroll so it stays pinned at the
    // viewport edge (not scrolled) and, being a draw modifier, never intercepts chip taps. Only shown
    // while there is more to scroll. [VEHICLES-REDESIGN-001]
    val fadeColor = MaterialTheme.colorScheme.surfaceContainer
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(fadeColor)
            .drawWithContent {
                drawContent()
                if (scrollState.canScrollForward) {
                    val fadeW = FILTER_FADE_WIDTH_DP.dp.toPx()
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Transparent, fadeColor),
                            startX = size.width - fadeW,
                            endX = size.width,
                        ),
                        topLeft = Offset(size.width - fadeW, 0f),
                        size = Size(fadeW, size.height),
                    )
                }
            }
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        filters.forEach { (filter, label) ->
            PaparcarFilterChip(
                label = label,
                selected = activeFilter == filter,
                onClick = { onFilterSelected(filter) },
                leadingIcon = FILTER_ICONS[filter],
            )
        }
    }
}
