package com.rndeveloper.paparcar.presentation.vehicles.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.presentation.vehicles.StatsPeriod
import com.rndeveloper.paparcar.ui.components.chips.PaparcarFilterChip
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import com.rndeveloper.paparcar.ui.theme.PapGap
import com.rndeveloper.paparcar.ui.theme.PapControlHeight
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.history_filter_all
import paparcar.composeapp.generated.resources.history_filter_last_3_months
import paparcar.composeapp.generated.resources.history_filter_last_7_days
import paparcar.composeapp.generated.resources.history_filter_last_month
import paparcar.composeapp.generated.resources.history_filter_this_month
import paparcar.composeapp.generated.resources.history_filter_this_week
import paparcar.composeapp.generated.resources.history_filter_this_year
import paparcar.composeapp.generated.resources.history_filter_years

// Width of the right-edge scroll-hint fade on the filter bar. [VEHICLES-REDESIGN-001]
private const val FILTER_FADE_WIDTH_DP = 28
/** El aire que la barra de periodo quiere que se VEA por encima y por debajo de sus chips. */
private const val PERIOD_BAR_AIR_DP = 4

private val FILTER_ICONS = mapOf(
    StatsPeriod.All to Icons.Rounded.GridView,
    StatsPeriod.Last7Days to Icons.Rounded.Today,
    StatsPeriod.ThisWeek to Icons.Rounded.DateRange,
    StatsPeriod.ThisMonth to Icons.Rounded.CalendarMonth,
    StatsPeriod.LastMonth to Icons.Rounded.EventRepeat,
    StatsPeriod.Last3Months to Icons.Rounded.Schedule,
    StatsPeriod.ThisYear to Icons.Rounded.History,
)

/**
 * The period selector, over the periods this history can actually answer.
 * [STATS-A-FILTER-THAT-CANNOT-ANSWER-IS-NOT-OFFERED-001]
 *
 * ⛔ **The list is an input, not a constant.** It used to hold its own five, so it offered a quarter
 * to an account three weeks old and had nowhere to put a year to an account with three. What it may
 * show is a question about the DATA, which is `availablePeriods`' job; what this does is draw it.
 *
 * The closed years live behind ONE chip with a menu instead of one chip each: four years of driving
 * would push the periods about NOW off the edge of a row that already scrolls.
 *
 * ⛔ **It draws in the list's order** [STATS-FIRST-CHIP-ANSWERS-NOW-001]. It used to split the list
 * in two — the chips, then the years — which quietly made the BAR the last word on where a period
 * sits: "All" moved to the end of `availablePeriods` and still came out before the years, because
 * this function put it there. The run of closed years now collapses into its single chip AT the
 * place the first of them occupies, and nothing else is reordered here.
 */
@Composable
internal fun StatsPeriodBar(
    filters: List<StatsPeriod>,
    activePeriod: StatsPeriod,
    onFilterSelected: (StatsPeriod) -> Unit,
    /**
     * The colour of the screen BEHIND the bar.
     *
     * ⛔ No default, on purpose. The fade at the right edge has to die in the colour of whatever is
     * under it, and when that colour was assumed —`surfaceContainer`, the tone of the screen this
     * bar was born on— the same bar drew a full-width band across the stats panel, whose Scaffold
     * is `surface`. A default would let the third screen inherit the same bug in silence.
     */
    containerColor: Color,
    modifier: Modifier = Modifier,
) {
    val years = filters.filterIsInstance<StatsPeriod.Year>()
    val scrollState = rememberScrollState()
    // Right-edge fade to the bar background, signalling there are more filter chips off-screen — same
    // treatment as Home's size filter bar. Drawn BEFORE horizontalScroll so it stays pinned at the
    // viewport edge (not scrolled) and, being a draw modifier, never intercepts chip taps. Only shown
    // while there is more to scroll. [VEHICLES-REDESIGN-001]
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor)
            .drawWithContent {
                drawContent()
                if (scrollState.canScrollForward) {
                    val fadeW = FILTER_FADE_WIDTH_DP.dp.toPx()
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Transparent, containerColor),
                            startX = size.width - fadeW,
                            endX = size.width,
                        ),
                        topLeft = Offset(size.width - fadeW, 0f),
                        size = Size(fadeW, size.height),
                    )
                }
            }
            .horizontalScroll(scrollState)
            // ⛔ Sin padding vertical, y no por quitarlo: el chip ya trae **7 dp de aire invisible
            // por lado** porque el mínimo táctil de 48 dp lo infla desde sus ~34 pintados. Los 4 que
            // había encima se SUMABAN a eso y la fila se leía con 11 — el *«los chips parecen
            // excesivamente separados»* que abrió #134. El aire lo pone el área de toque; añadirle
            // más es contarlo dos veces. [UI-THE-SPACING-SWEEP-IS-SCREEN-BY-SCREEN-001]
            .padding(
                horizontal = 16.dp,
                vertical = PapGap.betweenControls(
                    visible = PERIOD_BAR_AIR_DP.dp,
                    controlHeight = PapControlHeight.chip,
                    sides = 1,
                ),
            ),
        horizontalArrangement = Arrangement.spacedBy(PapGap.chipRow),
    ) {
        filters.forEach { filter ->
            if (filter is StatsPeriod.Year) {
                // The whole run of years is one chip, drawn where the first of them stands.
                if (filter == years.first()) {
                    YearsChip(
                        years = years,
                        activePeriod = activePeriod,
                        onFilterSelected = onFilterSelected,
                    )
                }
            } else {
                PaparcarFilterChip(
                    label = filter.label(),
                    selected = activePeriod == filter,
                    onClick = { onFilterSelected(filter) },
                    leadingIcon = FILTER_ICONS[filter],
                )
            }
        }
    }
}

/**
 * One chip for every closed year, with the chosen one written on it.
 *
 * ⛔ The label becomes the YEAR once one is picked. A chip that kept saying "by year" while a year
 * was selected would be the only selected chip on the row that does not say what it selected — the
 * filter would be invisible everywhere except in the figures it changed.
 */
@Composable
private fun YearsChip(
    years: List<StatsPeriod.Year>,
    activePeriod: StatsPeriod,
    onFilterSelected: (StatsPeriod) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedYear = activePeriod as? StatsPeriod.Year
    Box {
        PaparcarFilterChip(
            label = selectedYear?.value?.toString() ?: stringResource(Res.string.history_filter_years),
            selected = selectedYear != null,
            onClick = { expanded = true },
            leadingIcon = Icons.Rounded.EventRepeat,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            years.forEach { year ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = year.value.toString(),
                            style = PaparcarType.current.rowTitle,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        expanded = false
                        onFilterSelected(year)
                    },
                )
            }
        }
    }
}

@Composable
private fun StatsPeriod.label(): String = when (this) {
    StatsPeriod.All -> stringResource(Res.string.history_filter_all)
    // Rolling seven days beside the calendar week — also the free plan's stats window.
    // [STATS-FREE-SEES-EVERY-FIGURE-FOR-7-DAYS-001]
    StatsPeriod.Last7Days -> stringResource(Res.string.history_filter_last_7_days)
    StatsPeriod.ThisWeek -> stringResource(Res.string.history_filter_this_week)
    StatsPeriod.ThisMonth -> stringResource(Res.string.history_filter_this_month)
    StatsPeriod.LastMonth -> stringResource(Res.string.history_filter_last_month)
    StatsPeriod.Last3Months -> stringResource(Res.string.history_filter_last_3_months)
    StatsPeriod.ThisYear -> stringResource(Res.string.history_filter_this_year)
    is StatsPeriod.Year -> value.toString()
}
