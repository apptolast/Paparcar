package com.rndeveloper.paparcar.domain.fuel

import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.Money

/**
 * National average pump prices, shipped in the binary. [FUEL-PRICE-RESOLVER-001]
 *
 * This is the rung that makes the spend figure work **in every country and with no network**: the
 * app knows where the car parked (`AddressInfo.countryCode`, already stamped by the reverse
 * geocoder) and can price a drive without asking anyone. A real pump near the car is better and
 * arrives with `FUEL-STATIONS-001`; what the user types is better still and always wins.
 *
 * ⛔ **Every figure here carries [VALID_AS_OF] and the UI says "average".** A price with no date is
 * a number someone made up: fuel moves weekly, this table moves with releases, and the honest thing
 * is to let the reader weigh it. Refreshing it is part of preparing a release — see the checklist in
 * the ticket doc.
 *
 * Countries are the nine locales the app speaks, plus a euro-area fallback so a Belgian or an Irish
 * user gets a figure rather than a blank. Anywhere else resolves to null, and null means the cost
 * tile simply does not appear — never a zero, never a guess wearing a currency symbol.
 */
object StaticAverageFuelPrices {

    /** When these figures were last checked. Reviewed with every release; see the ticket doc. */
    const val VALID_AS_OF = "2026-09"

    /**
     * Average price of one LITRE, by country and fuel. Electric is absent on purpose: a litre price
     * for an EV is a category error, and its energy is priced by [averageKwh].
     */
    private val LITRE: Map<String, Map<FuelType, Double>> = mapOf(
        "ES" to mapOf(FuelType.GASOLINE to 1.55, FuelType.DIESEL to 1.45, FuelType.LPG_CNG to 0.99),
        "PT" to mapOf(FuelType.GASOLINE to 1.78, FuelType.DIESEL to 1.62, FuelType.LPG_CNG to 0.92),
        "FR" to mapOf(FuelType.GASOLINE to 1.79, FuelType.DIESEL to 1.68, FuelType.LPG_CNG to 0.95),
        "IT" to mapOf(FuelType.GASOLINE to 1.82, FuelType.DIESEL to 1.72, FuelType.LPG_CNG to 0.79),
        "DE" to mapOf(FuelType.GASOLINE to 1.75, FuelType.DIESEL to 1.63, FuelType.LPG_CNG to 1.09),
        "NL" to mapOf(FuelType.GASOLINE to 2.09, FuelType.DIESEL to 1.79, FuelType.LPG_CNG to 1.19),
        "PL" to mapOf(FuelType.GASOLINE to 1.52, FuelType.DIESEL to 1.49, FuelType.LPG_CNG to 0.71),
        "RO" to mapOf(FuelType.GASOLINE to 1.48, FuelType.DIESEL to 1.51, FuelType.LPG_CNG to 0.83),
        // Euro-area fallback for the countries the app is installable in but does not translate.
        EURO_AREA to mapOf(FuelType.GASOLINE to 1.72, FuelType.DIESEL to 1.62, FuelType.LPG_CNG to 0.98),
    )

    /** Average domestic price of one kWh — what an EV or a plug-in hybrid charges at. */
    private val KWH: Map<String, Double> = mapOf(
        "ES" to 0.16, "PT" to 0.21, "FR" to 0.23, "IT" to 0.26, "DE" to 0.35,
        "NL" to 0.31, "PL" to 0.19, "RO" to 0.17, EURO_AREA to 0.25,
    )

    /** Currency by country. Only the euro area and Poland/Romania for now — see [LITRE]. */
    private val CURRENCY: Map<String, String> = mapOf(
        "ES" to "EUR", "PT" to "EUR", "FR" to "EUR", "IT" to "EUR", "DE" to "EUR",
        "NL" to "EUR", "RO" to "RON", "PL" to "PLN", EURO_AREA to "EUR",
    )

    /**
     * Average litre price for [fuel] in [countryCode], or null where this table cannot answer —
     * an unknown country, or a fuel that has no litres (an EV).
     */
    fun averageLitre(countryCode: String?, fuel: FuelType): Money? {
        if (!fuel.usesLiters) return null
        val country = resolveCountry(countryCode) ?: return null
        val amount = LITRE[country]?.get(fuel.pumpProduct()) ?: return null
        return Money(amount, CURRENCY.getValue(country))
    }

    /**
     * Which product this fuel actually buys at the pump.
     *
     * A hybrid does not fill up with "hybrid": it fills up with petrol, and so does a plug-in
     * hybrid. The table prices PRODUCTS — the three a pump sells — and this is what maps a car's
     * powertrain onto one. Caught by its own test: `PLUG_IN_HYBRID` had no row and the spend for
     * every hybrid came back null, which the screen would have shown as "no cost figure" rather
     * than as the bug it was.
     *
     * A `when` and not a map, so a new [FuelType] cannot be added without answering this.
     * [VEH-A-NEW-VEHICLE-TYPE-MUST-NOT-BE-A-CAR-BY-OMISSION-001]
     */
    private fun FuelType.pumpProduct(): FuelType = when (this) {
        FuelType.HYBRID, FuelType.PLUG_IN_HYBRID -> FuelType.GASOLINE
        FuelType.GASOLINE, FuelType.DIESEL, FuelType.LPG_CNG, FuelType.ELECTRIC -> this
    }

    /** Average kWh price in [countryCode], or null where this table cannot answer. */
    fun averageKwh(countryCode: String?): Money? {
        val country = resolveCountry(countryCode) ?: return null
        val amount = KWH[country] ?: return null
        return Money(amount, CURRENCY.getValue(country))
    }

    /**
     * The row to read for [countryCode]: itself when the table has it, the euro-area row when the
     * country is in it, and null otherwise. Null is a real answer — outside these borders the app
     * has no idea what fuel costs and says nothing rather than pricing a drive in Madrid euros.
     */
    private fun resolveCountry(countryCode: String?): String? {
        val code = countryCode?.uppercase() ?: return null
        if (code in LITRE) return code
        return if (code in EURO_AREA_MEMBERS) EURO_AREA else null
    }

    /** Key of the shared euro-area row. Not a country code — it can never collide with one. */
    private const val EURO_AREA = "EU"

    /**
     * Euro-area members the table does not price one by one. They read the shared row, which is
     * closer to the truth for them than nothing at all — and the label still says "average".
     */
    private val EURO_AREA_MEMBERS = setOf(
        "AT", "BE", "CY", "EE", "FI", "GR", "HR", "IE", "LT", "LU", "LV", "MT", "SI", "SK",
    )
}
