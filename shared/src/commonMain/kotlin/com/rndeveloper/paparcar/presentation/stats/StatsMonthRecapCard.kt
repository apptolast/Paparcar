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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.fuel.FuelPrice
import com.rndeveloper.paparcar.domain.stats.PeriodStats
import com.rndeveloper.paparcar.presentation.util.distanceString
import com.rndeveloper.paparcar.presentation.util.durationText
import com.rndeveloper.paparcar.presentation.util.fuelPriceNoteText
import com.rndeveloper.paparcar.presentation.util.statDelta
import com.rndeveloper.paparcar.ui.components.PapOutlinedCard
import com.rndeveloper.paparcar.ui.components.PapPremiumMark
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
 * ⛔ **The Plus gate is `closedMonthIsFullyVisible`, and this KDoc used to claim it was unnecessary.**
 * It said the free window of seven days meant a closed month could not exist for a free account —
 * false on exactly the days this card composes, the 1st to the 7th, when a rolling seven-day window
 * still reaches into the tail of the month that closed. The gate is a real line of code now, and it
 * is written as coverage rather than as a plan check. [MONEY-001]
 * [STATS-A-PACE-ROW-NAMES-THE-PERIOD-IT-PROJECTS-001]
 *
 * It disappears by itself after the first week of the new month: a summary read on the 27th is not
 * a summary, it is a memory sitting on top of everything that is actually happening.
 */
@Composable
internal fun StatsMonthRecapCard(
    stats: PeriodStats,
    /**
     * Where the spend row's price came from, exactly as the figures card owes it. Null = no spend
     * to caveat. [FUEL-PRICE-RESOLVER-001]
     *
     * ⛔ Until this parameter existed the spend row was UNREACHABLE: the recap was computed without
     * prices, so `costEstimate` was always null and the `let` below never ran.
     */
    costPrice: FuelPrice? = null,
) {
    // ⛔ **El metal va en la MARCA, no en la card.** Vestir la card entera se midió en device y
    // costó legibilidad donde más duele: sobre el lecho verde, las etiquetas de fila («gasto
    // aprox.», «buscando plaza», `onSurfaceVariant`) caen de 5,9:1 a **3,5:1** de contraste, y esta
    // pantalla existe para LEER cifras. Además, la única superficie `Subdued` que el producto ya
    // acepta a lo ancho es una FILA — el pie del historial — y la versión anterior de ese pie, una
    // losa de tres bandas, se revocó en device por exactamente esto: área amplifica croma.
    // [UI-PLUS-A-BRAND-FILL-IS-NOT-A-METALLIC-FINISH-001] [UI-PLUS-EMPHASIS-MATCHES-THE-MOMENT-001]
    //
    // Lo que sí dice «tu Plus está trabajando» sin tocar un solo dato es el CRESTA: el mismo
    // `PapPremiumSurface`, en el tamaño de un glifo, junto al título. Es la marca de siempre, no una
    // segunda señal premium inventada. [PLUS-A-PLAN-MUST-NAME-WHAT-IT-JUST-GAINED-001]
    PapOutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(CARD_PAD_DP.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MARK_GAP_DP.dp),
            ) {
                PapPremiumMark(size = RECAP_MARK_DP.dp)
                Text(
                    // ⛔ "El mes pasado" y no "Septiembre": nombrar el mes exigiría doce nombres
                    // largos en nueve idiomas —108 strings— para un título, y la card sólo se
                    // compone durante la primera semana del mes nuevo, así que cuál es no tiene
                    // ambigüedad.
                    text = stringResource(Res.string.stats_recap_title),
                    style = PaparcarType.current.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
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
                        delta = statDelta(cost.amount, stats.previous?.costEstimate?.amount),
                    )
                }
                stats.searchTime?.let { search ->
                    RecapRow(
                        label = stringResource(Res.string.stats_tile_search_time),
                        value = durationText(search.value),
                        delta = statDelta(
                            search.value.toDouble(),
                            stats.previous?.searchTime?.value?.toDouble(),
                        ),
                    )
                }
            }
            // The same caveat, in the same words, as the figures card — a second card printing a
            // spend is not allowed to invent a second way of qualifying it.
            // [FUEL-AN-AVERAGE-MUST-SAY-WHEN-IT-WAS-MEASURED-001]
            if (stats.costEstimate != null && costPrice != null) {
                Spacer(Modifier.height(TITLE_GAP_DP.dp))
                Text(
                    text = fuelPriceNoteText(costPrice),
                    style = PaparcarType.current.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

private fun formatRecapMoney(value: Double): String {
    val cents = (value * CENTS).roundToInt()
    return "${cents / CENTS.toInt()}.${(cents % CENTS.toInt()).toString().padStart(2, '0')}"
}

/** Direction is a SHAPE, never a colour — up is not "bad" and down is not "good".
 *  [UI-COLOR-DOCTRINE-001] */
private const val UP = "▲"
private const val DOWN = "▼"
private const val CARD_PAD_DP = 16

/** La cresta al lado del título: a la altura de la mayúscula de `cardTitle`, no mayor. Es una
 *  firma, no un icono de sección — si crece, vuelve a ser la card la que se viste de premium. */
private const val RECAP_MARK_DP = 22
private const val MARK_GAP_DP = 10
private const val TITLE_GAP_DP = 12
private const val ROW_GAP_DP = 10
private const val CENTS = 100.0
