package com.rndeveloper.paparcar.presentation.home.model

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

private const val PRICE_SCALE = 1_000.0
private const val PRICE_DECIMALS = 3
