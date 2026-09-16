package com.rndeveloper.paparcar.presentation.home.model

import com.rndeveloper.paparcar.domain.fuel.StationPrice
import com.rndeveloper.paparcar.domain.model.Money
import kotlin.math.roundToInt

/**
 * The ONE way a pump price is written — list row, map capsule and station peek all quote it, and
 * three private copies is how one of them rounds differently some day.
 * [FUEL-STATIONS-MAP-AND-PEEK-001]
 *
 * ⛔ **Three decimals, not two.** Fuel is quoted per litre to a tenth of a cent, and that tenth is
 * frequently the entire difference between two forecourts — rounding 1,879 and 1,875 both to "1,88"
 * would erase exactly the comparison this feature exists to make.
 */
internal fun formatPumpPrice(amount: Double): String {
    val thousandths = (amount * PRICE_SCALE).roundToInt()
    val whole = thousandths / PRICE_SCALE.toInt()
    val fraction = (thousandths % PRICE_SCALE.toInt()).toString().padStart(PRICE_DECIMALS, '0')
    return "$whole.$fraction"
}

/** `1.879 EUR` — amount at the pump's precision, currency as the feed states it. */
internal fun Money.pumpPriceLabel(): String = "${formatPumpPrice(amount)} $currency"

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
