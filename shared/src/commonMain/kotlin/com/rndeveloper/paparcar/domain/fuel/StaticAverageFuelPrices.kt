package com.rndeveloper.paparcar.domain.fuel

import com.rndeveloper.paparcar.domain.model.Money

/**
 * The half of the average-price table that is still written by hand: electricity.
 * [FUEL-PRICE-RESOLVER-001] [FUEL-NATIONAL-AVERAGE-FROM-OPENVAN-001]
 *
 * ⛔ **This file is small because OpenVan.camp has no kWh price — measured, not assumed.** Its feed
 * publishes twelve fuel keys (`gasoline`, `diesel`, `lpg`, `cng`, `e85`, `kerosene`…) and not one of
 * them is electricity. So the litre half moved to [EmbeddedAverageFuelPrices], generated from that
 * feed and covering 161 countries, while the kWh half stayed here: nine countries, typed by a
 * person, reviewed by a person.
 *
 * That asymmetry is the honest state of the world and the comment exists so nobody "finishes the
 * migration" by inventing electricity prices from the same source. If a kWh feed with a workable
 * licence turns up, this is the file that disappears.
 *
 * The domestic kWh price is also a genuinely different number from a pump price: it is what a car
 * charges at overnight, not what a public charger bills, and the two differ by more than any
 * national average would suggest. Pricing a plug-in hybrid's electricity at motorway-charger rates
 * would overstate its cost as badly as the old petrol table understated everything else.
 */
object StaticAverageFuelPrices {

    /** When these kWh figures were last checked by hand. The litre half dates itself separately. */
    const val KWH_VALID_AS_OF = "2026-09"

    /** Average DOMESTIC price of one kWh — what an EV or a plug-in hybrid charges at overnight. */
    private val KWH: Map<String, Double> = mapOf(
        "ES" to 0.16, "PT" to 0.21, "FR" to 0.23, "IT" to 0.26, "DE" to 0.35,
        "NL" to 0.31, "PL" to 0.19, "RO" to 0.17, EURO_AREA to 0.25,
    )

    /** Currency for the [KWH] rows. Only the euro area plus Poland and Romania are priced. */
    private val KWH_CURRENCY: Map<String, String> = mapOf(
        "ES" to "EUR", "PT" to "EUR", "FR" to "EUR", "IT" to "EUR", "DE" to "EUR",
        "NL" to "EUR", "RO" to "RON", "PL" to "PLN", EURO_AREA to "EUR",
    )

    /**
     * Average kWh price in [countryCode], or null where this table cannot answer.
     *
     * Null is a real answer — outside these borders the app has no idea what a kilowatt-hour costs
     * and says nothing rather than pricing a charge in Madrid euros.
     */
    fun averageKwh(countryCode: String?): Money? {
        val country = resolveCountry(countryCode) ?: return null
        val amount = KWH[country] ?: return null
        return Money(amount, KWH_CURRENCY.getValue(country))
    }

    /**
     * The row to read for [countryCode]: itself when the table has it, the euro-area row when the
     * country is in the euro area, and null otherwise.
     *
     * The litre table no longer needs this fallback — it prices all fourteen of those countries one
     * by one now, and the spread between them reached 0.80 €/L, which is precisely what a single
     * shared row was flattening. The kWh table still leans on it, because nine hand-typed rows
     * cannot do better and a shared euro figure beats no figure at all.
     */
    private fun resolveCountry(countryCode: String?): String? {
        val code = countryCode?.uppercase() ?: return null
        if (code in KWH) return code
        return if (code in EURO_AREA_MEMBERS) EURO_AREA else null
    }

    /** Key of the shared euro-area row. Not a country code — it can never collide with one. */
    private const val EURO_AREA = "EU"

    /** Euro-area members the kWh table does not price one by one. */
    private val EURO_AREA_MEMBERS = setOf(
        "AT", "BE", "CY", "EE", "FI", "GR", "HR", "IE", "LT", "LU", "LV", "MT", "SI", "SK",
    )
}
