package com.rndeveloper.paparcar.presentation.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.stats.PeriodStats
import com.rndeveloper.paparcar.presentation.util.distanceString
import com.rndeveloper.paparcar.presentation.util.durationText
import com.rndeveloper.paparcar.ui.components.PapOutlinedCard
import com.rndeveloper.paparcar.ui.components.PapStatDelta
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.stats_recap_parkings
import paparcar.composeapp.generated.resources.stats_recap_title
import paparcar.composeapp.generated.resources.stats_tile_cost
import paparcar.composeapp.generated.resources.stats_tile_search_time
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The month that just closed — the only thing on this screen nobody asked for.
 * [STATS-PLUS-REMEMBERS-AND-REPORTS-001]
 *
 * ⛔ **It says its own scope in its own title.** Every other card here belongs to the period the
 * chip selects; this one does not, and a card that silently spanned a different month would be the
 * clock clash [STATS-UI-001] spent a whole ticket undoing. "Last month" is the smallest honest way
 * to say it — and unambiguous, because the card only exists during the first week of the new one.
 *
 * ⛔ **It needs no Plus gate, and that is not an oversight.** The free history window is seven days,
 * so a closed month cannot exist for a free account at all — the plan does the gating, and nothing
 * is hidden or veiled. [MONEY-001] [STATS-FREE-SEES-EVERY-FIGURE-FOR-7-DAYS-001]
 *
 * It disappears by itself after the first week of the new month: a summary read on the 27th is not
 * a summary, it is a memory sitting on top of everything that is actually happening.
 */
@Composable
internal fun StatsMonthRecapCard(stats: PeriodStats) {
    PapOutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(CARD_PAD_DP.dp)) {
            Text(
                // ⛔ "El mes pasado" y no "Septiembre": nombrar el mes exigiría doce nombres
                // largos en nueve idiomas —108 strings— para un título, y la card sólo se compone
                // durante la primera semana del mes nuevo, así que cuál es no tiene ambigüedad.
                text = stringResource(Res.string.stats_recap_title),
                style = PaparcarType.current.cardTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(TITLE_GAP_DP.dp))
            Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP_DP.dp)) {
                RecapRow(
                    label = pluralStringResource(
                        Res.plurals.stats_recap_parkings,
                        stats.parkings,
                        stats.parkings,
                    ),
                    value = stats.distance?.let { distanceString(it.value) }.orEmpty(),
                    delta = null,
                )
                stats.costEstimate?.let { cost ->
                    RecapRow(
                        label = stringResource(Res.string.stats_tile_cost),
                        value = "${formatRecapMoney(cost.amount)} ${cost.currency}",
                        // The comparison is against the month BEFORE the closed one, and it is the
                        // same arithmetic the tiles use — including its refusal to compare with a
                        // zero. [STATS-A-MEASURE-CARRIES-ITS-COVERAGE-001]
                        delta = recapDelta(cost.amount, stats.previous?.costEstimate?.amount),
                    )
                }
                stats.searchTime?.let { search ->
                    RecapRow(
                        label = stringResource(Res.string.stats_tile_search_time),
                        value = durationText(search.value),
                        delta = recapDelta(
                            search.value.toDouble(),
                            stats.previous?.searchTime?.value?.toDouble(),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecapRow(label: String, value: String, delta: PapStatDelta?) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = PaparcarType.current.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = delta?.let { "$value  ${if (it.isUp) UP else DOWN} ${it.amountText}" } ?: value,
            style = PaparcarType.current.counter,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
        )
    }
}

/** Movement against the month before the closed one. Same refusals as the tiles: nothing to compare
 *  against, a previous zero, or a move that rounds to nothing. */
private fun recapDelta(current: Double, previous: Double?): PapStatDelta? {
    if (previous == null || previous == 0.0) return null
    val change = ((current - previous) / previous * PERCENT).roundToInt()
    if (change == 0) return null
    return PapStatDelta(amountText = "${abs(change)}%", isUp = change > 0)
}

private fun formatRecapMoney(value: Double): String {
    val cents = (value * CENTS).roundToInt()
    return "${cents / CENTS.toInt()}.${(cents % CENTS.toInt()).toString().padStart(2, '0')}"
}

/** Direction is a SHAPE, never a colour — up is not "bad" and down is not "good".
 *  [UI-COLOR-DOCTRINE-001] */
private const val UP = "▲"
private const val DOWN = "▼"
private const val CARD_PAD_DP = 16
private const val TITLE_GAP_DP = 12
private const val ROW_GAP_DP = 10
private const val PERCENT = 100
private const val CENTS = 100.0
