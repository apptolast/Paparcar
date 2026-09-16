package com.rndeveloper.paparcar.presentation.home.model

import com.rndeveloper.paparcar.GallonKind
import com.rndeveloper.paparcar.domain.fuel.StationPrice
import com.rndeveloper.paparcar.domain.model.Money
import com.rndeveloper.paparcar.presentation.util.pumpPriceToDisplay
import kotlin.math.roundToInt

/**
 * The ONE way a pump price is written — list row, map capsule and station peek all quote it, and
 * three private copies is how one of them rounds differently some day.
 * [FUEL-STATIONS-MAP-AND-PEEK-001]
 *
 * ⛔ **Three decimals, not two.** Fuel is quoted to a tenth of a cent, and that tenth is frequently
 * the entire difference between two forecourts — rounding 1,879 and 1,875 both to "1,88" would erase
 * exactly the comparison this feature exists to make. **The gallon does not change this**: the US
 * quotes $3.499/gal on the very same signage, to the same tenth of a cent.
 */
internal fun formatPumpPrice(amount: Double): String {
    val thousandths = (amount * PRICE_SCALE).roundToInt()
    val whole = thousandths / PRICE_SCALE.toInt()
    val fraction = (thousandths % PRICE_SCALE.toInt()).toString().padStart(PRICE_DECIMALS, '0')
    return "$whole.$fraction"
}

/**
 * `1.879` — a stored per-LITRE amount, written in the unit this driver actually buys it in.
 * [I18N-A-PRICE-IS-QUOTED-IN-THE-UNIT-YOU-BUY-IT-001]
 *
 * ⛔ **This is THE outbound boundary for every fuel price**, and it is one function because the
 * readers are four surfaces plus a worker: the sheet row, the station peek, the two map capsules and
 * `FuelPriceWatchWorker`, which writes both alert bodies through here. A second place converting the
 * same number is how one of them would some day quote litres to an American.
 */
internal fun Money.pumpPriceFigure(gallon: GallonKind?): String =
    formatPumpPrice(pumpPriceToDisplay(amount, gallon))

/** `1.879 EUR` — amount in the unit it is sold in, currency as the feed states it. */
internal fun Money.pumpPriceLabel(gallon: GallonKind?): String = "${pumpPriceFigure(gallon)} $currency"

/**
 * Whether this figure needs saying out loud that somebody else pumps it.
 * [FUEL-ITALY-A-PRICE-DEPENDS-ON-WHO-PUMPS-IT-001]
 *
 * ⛔ **Only the attended price is marked, never the self one.** Self-service is the default
 * everywhere the question is even asked, and in the four countries that do not ask it
 * [StationPrice.isSelf] is null — so marking "self" would put a word on every price in Europe to
 * qualify the exception. The exception is what gets the word.
 *
 * Asked as a question so no call site spells `isSelf == false`, which is the comparison that reads
 * as a typo and gets "simplified" into `!isSelf` — silently marking every French price attended.
 */
internal fun StationPrice.isAttendedService(): Boolean = isSelf == false

private const val PRICE_SCALE = 1_000.0
private const val PRICE_DECIMALS = 3
