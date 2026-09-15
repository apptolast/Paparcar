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
import com.rndeveloper.paparcar.ui.components.PapOutlinedCard
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.stats_pace_basis
import paparcar.composeapp.generated.resources.stats_pace_title

/**
 * Where this period is heading, if the rest of it looks like the part that has happened.
 * [STATS-AN-ESTIMATE-SAYS-IT-IS-ONE-001]
 *
 * ⛔ **Its own card, and its own tense.** Everything above it on this screen is measured — money
 * that was spent, minutes that were lost — and this is not. Mixing a projection into that grid would
 * put a number nobody has spent under the same heading as the ones they did, and the app's whole
 * stats doctrine is that a figure says what it is: absent below its threshold, coverage on a partial
 * sum, "aprox." on a declared consumption. A forecast is one step further out and has to look it.
 *
 * The basis line is not a disclaimer, it is the figure's scope — the same job the coverage note does
 * on a measured sum. [STATS-A-MEASURE-CARRIES-ITS-COVERAGE-001]
 */
@Composable
internal fun StatsPaceCard(rows: List<PaceRow>, elapsedPercent: Int) {
    if (rows.isEmpty()) return
    PapOutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(CARD_PAD_DP.dp)) {
            Text(
                text = stringResource(Res.string.stats_pace_title),
                style = PaparcarType.current.cardTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(TITLE_GAP_DP.dp))
            Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP_DP.dp)) {
                rows.forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = row.label,
                            style = PaparcarType.current.body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        // The figure is a CIFRA, but a small one: it is a guess, and a guess drawn
                        // as large as a measured total would outrank the card that measured it.
                        // [UI-TYPE-TWO-VOICES-ONE-ROW-001]
                        Text(
                            text = row.value,
                            style = PaparcarType.current.counter,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }
            Spacer(Modifier.height(TITLE_GAP_DP.dp))
            Text(
                text = stringResource(Res.string.stats_pace_basis, elapsedPercent),
                style = PaparcarType.current.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One forecast, already worded and formatted by the screen that knows the units. */
internal data class PaceRow(val label: String, val value: String)

private const val CARD_PAD_DP = 16
private const val TITLE_GAP_DP = 12
private const val ROW_GAP_DP = 10
