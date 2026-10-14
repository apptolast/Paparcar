package com.rndeveloper.paparcar.domain.usecase.fuel

import com.rndeveloper.paparcar.domain.fuel.WatchedForecourt
import com.rndeveloper.paparcar.domain.fuel.FuelPriceSample
import com.rndeveloper.paparcar.domain.model.FuelType

/** Which way a watched price went. [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001] */
enum class PriceMoveDirection { UP, DOWN }

/**
 * A watched station's price moved, and both readings that prove it.
 *
 * ⛔ **Watched, not followed** [FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001]: desde el 13-10 esto lo
 * produce también el carril free, sobre una gasolinera de tu alrededor que nadie estrelló. Qué
 * carril lo dijo lo sabe quien lo pide — y es lo que decide la FRASE («la que sigues» vs. «una cerca
 * de ti») y la tarjeta en la que sale.
 *
 * [deltaPerUnit] is signed in the currency's own unit (−0.03 = three cents per litre cheaper), so a
 * reader never has to recombine a magnitude with a direction and get the sign wrong.
 */
data class FuelPriceMove(
    val station: WatchedForecourt,
    val fuelType: FuelType,
    val previous: FuelPriceSample,
    val current: FuelPriceSample,
    val deltaPerUnit: Double,
    val direction: PriceMoveDirection,
)

/**
 * What happened overnight to the prices you have a stake in.
 * [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001]
 *
 * A verdict, not a predicate: its result is the sentence the notification says and the one a
 * diagnosis would quote. Pure and synchronous — the reading of the history is I/O and happens above.
 *
 * ⛔ **The rule that makes this honest: two rows differing is not a price changing.** A feed can
 * answer for days with the same stale reading (Italy: 69% of prices two to seven days old, tail to
 * 2013) and it can also re-publish an unchanged price with a fresh stamp. So a move is only claimed
 * when BOTH samples carry a feed stamp AND those stamps differ. Otherwise the verdict is «no news»,
 * which is not «no change» — the same reason an absent price is never a zero.
 */
class EvaluateDailyPriceMoveUseCase {

    /**
     * @param subjects the forecourts whose series to read — followed ones, or the ones that were
     *        around the driver. This verdict does not care which: «did this price move» is the same
     *        question either way. [FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001]
     * @param samplesByStation the history of each subject for ONE fuel, oldest first. Callers hand
     *        over what the user's own car burns: a move in a fuel you cannot put in your tank is
     *        not news.
     */
    operator fun invoke(
        subjects: List<WatchedForecourt>,
        fuelType: FuelType,
        samplesByStation: Map<String, List<FuelPriceSample>>,
        minDeltaPerUnit: Double = MIN_DELTA_PER_UNIT,
    ): List<FuelPriceMove> = subjects.mapNotNull { station ->
        val samples = samplesByStation[station.stationKey].orEmpty()
            .filter { it.fuelType == fuelType }
            .sortedBy { it.observedDay }
        val current = samples.lastOrNull() ?: return@mapNotNull null
        val previous = samples.getOrNull(samples.lastIndex - 1) ?: return@mapNotNull null

        // The two readings must be about two different days. Same day = the writer collapsed them
        // and one of these is not what it looks like.
        if (current.observedDay == previous.observedDay) return@mapNotNull null

        // ⛔ The stamp gate. Without it, a feed that has not refreshed for a week produces a
        // "movement" every single day the user happens to open the app under different rounding.
        val currentStamp = current.reportedAtMs ?: return@mapNotNull null
        val previousStamp = previous.reportedAtMs ?: return@mapNotNull null
        if (currentStamp == previousStamp) return@mapNotNull null

        // Currencies are compared, never converted: a station that changed currency has not changed
        // its price, and 1,7 PLN is not cheaper than 1,6 EUR.
        if (current.perUnit.currency != previous.perUnit.currency) return@mapNotNull null

        // ⛔ And the same argument, one unit down: an Italian forecourt that stops quoting `self`
        // still quotes `servito`, measured up to **0,35 €/L** dearer. Comparing those two would
        // wake a user at dawn to report a 35-cent rise at a pump whose price never moved — and it
        // is the LOUDEST possible version of the mistake, because the delta is an order of
        // magnitude above the three-cent threshold this whole verdict is tuned for.
        // [FUEL-ITALY-A-PRICE-DEPENDS-ON-WHO-PUMPS-IT-001]
        if (current.isSelf != previous.isSelf) return@mapNotNull null

        val delta = current.perUnit.amount - previous.perUnit.amount
        if (kotlin.math.abs(delta) < minDeltaPerUnit) return@mapNotNull null

        FuelPriceMove(
            station = station,
            fuelType = fuelType,
            previous = previous,
            current = current,
            deltaPerUnit = delta,
            direction = if (delta > 0) PriceMoveDirection.UP else PriceMoveDirection.DOWN,
        )
    }

    private companion object {
        /**
         * Half a cent per litre. Below this the news is arithmetic, not money: a 40 l tank moves by
         * twenty cents and nobody would drive across a street for it — but it WOULD interrupt them
         * every morning, which is how a vigilance feature teaches people to silence it.
         */
        const val MIN_DELTA_PER_UNIT = 0.005
    }
}
