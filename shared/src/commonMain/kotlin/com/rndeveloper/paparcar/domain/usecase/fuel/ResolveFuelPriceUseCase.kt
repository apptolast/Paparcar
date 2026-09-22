@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.fuel

import com.rndeveloper.paparcar.domain.fuel.AverageFuelPriceTable
import com.rndeveloper.paparcar.domain.fuel.FuelPrice
import com.rndeveloper.paparcar.domain.fuel.FuelPriceOrigin
import com.rndeveloper.paparcar.domain.fuel.StaticAverageFuelPrices
import com.rndeveloper.paparcar.domain.model.Money
import com.rndeveloper.paparcar.domain.model.Vehicle
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * What this car's energy costs, and where that figure came from. [FUEL-PRICE-RESOLVER-001]
 *
 * ONE place decides the order, because the order IS the product rule: what the user typed beats a
 * real pump, a real pump beats the national average, and the average beats nothing. Spreading that
 * across two screens is how one of them ends up quoting the average while the other quotes the
 * user's own price for the same drive.
 *
 * A verdict, not a predicate: its result is quoted on screen — the tile says "tu precio" or "precio
 * medio en España" — so it is a use case with its own tests. [DET-VERDICT-NOT-PREDICATE-001]
 *
 * ⛔ No `FuelPriceSource` port yet, and that is deliberate. The roadmap sketches one, and the plan
 * for this ticket promised it; writing it here would have meant designing `priceNear(...)` without
 * its adapter — which coordinate prices a whole PERIOD? does it download by province? what does it
 * cache and for how long? Those answers belong to `FUEL-STATIONS-001`, and an interface shaped by
 * guesses is not a head start, it is something the adapter has to fight. So the rung is a
 * PARAMETER: whoever has a station price passes it, and today nobody does. The order lives here
 * regardless, and it is what the tests pin.
 */
class ResolveFuelPriceUseCase(
    /**
     * The national averages to quote, read at the MOMENT of the call. [FUEL-NATIONAL-AVERAGE-FROM-OPENVAN-001]
     *
     * A provider and not an [AverageFuelPriceTable] because this use case outlives a refresh: hold
     * the table itself and whichever instance was built before the fetch landed would keep quoting
     * the snapshot in the binary forever, on one screen but not the other. Reading through the
     * lambda means there is one live answer and every screen sees the same one.
     *
     * Defaults to the embedded snapshot so tests, previews and the state gallery need no wiring —
     * and the default is the FLOOR, never a blank: a caller that forgets to inject gets the honest
     * offline answer rather than a null that would silently hide every cost figure.
     */
    private val averages: () -> AverageFuelPriceTable = { AverageFuelPriceTable.Embedded },
    /** The user's calendar day, injectable for tests. The staleness gate below compares dates, not
     *  instants — an average is dated by day, so its age is counted in days. */
    private val today: () -> LocalDate = { Clock.System.todayIn(TimeZone.currentSystemDefault()) },
) {

    /**
     * Price of one litre for [vehicle], or null when nothing can answer honestly.
     *
     * [countryCode] comes from the parking's own address (`AddressInfo.countryCode`), resolved on
     * the fly and never stored — a user's country is not ours to keep.
     */
    fun litre(
        vehicle: Vehicle,
        countryCode: String?,
        stationPrice: Money? = null,
    ): FuelPrice? {
        val fuel = vehicle.fuelType ?: return null
        if (!fuel.usesLiters) return null
        vehicle.fuelPricePerLiter?.let { return FuelPrice(it, FuelPriceOrigin.UserOverride) }
        stationPrice?.let { return FuelPrice(it, FuelPriceOrigin.Station) }
        val table = averages()
        // [STATS-AN-ESTIMATE-NAMES-ITSELF-AND-ITS-AGE-001] A national average EXPIRES: past the
        // ceiling it no longer prices the period — the cost tile refuses, exactly as it does with
        // no price at all. Below it, the figure still ships WITH its date (fuelPriceNoteText).
        // Only this rung ages out: the user's own price is their word, and the station rung will
        // bring its own freshness with FUEL-STATIONS-001.
        if (!nationalAverageStillPrices(table.asOf)) return null
        return table.averageLitre(countryCode, fuel)
            ?.let { FuelPrice(it, FuelPriceOrigin.NationalAverage, measuredOn = table.asOf) }
    }

    /**
     * Price of one kWh for [vehicle], or null when this car draws no electricity at all.
     *
     * A plug-in hybrid needs BOTH this and [litre]; the aggregate refuses to price it with only
     * one, and that refusal is the whole point — a cost that covers the petrol and silently drops
     * the charging is not a cost. [STATS-CORE-001]
     */
    fun kwh(
        vehicle: Vehicle,
        countryCode: String?,
        stationPrice: Money? = null,
    ): FuelPrice? {
        val fuel = vehicle.fuelType ?: return null
        if (!fuel.usesElectricity) return null
        vehicle.energyPricePerKwh?.let { return FuelPrice(it, FuelPriceOrigin.UserOverride) }
        stationPrice?.let { return FuelPrice(it, FuelPriceOrigin.Station) }
        // ⛔ La mitad de kWh sigue escrita a mano y solo sabe fecharse por MES, no por dia
        // (OpenVan no publica electricidad). Se estampa el primer dia de ese mes: decir "hace ~N
        // dias" con esa precision es honesto; inventar un dia exacto no lo seria.
        // [FUEL-AN-AVERAGE-MUST-SAY-WHEN-IT-WAS-MEASURED-001]
        val measuredOn = "${StaticAverageFuelPrices.KWH_VALID_AS_OF}-01"
        if (!nationalAverageStillPrices(measuredOn)) return null
        return StaticAverageFuelPrices.averageKwh(countryCode)
            ?.let { FuelPrice(it, FuelPriceOrigin.NationalAverage, measuredOn = measuredOn) }
    }

    /**
     * Whether a national average measured on [measuredOn] (ISO `yyyy-MM-dd`) may still price a
     * period today. An unparseable date refuses too: a price that cannot say when it was measured
     * cannot defend itself months later either — absent, never invented.
     * [STATS-AN-ESTIMATE-NAMES-ITSELF-AND-ITS-AGE-001]
     */
    private fun nationalAverageStillPrices(measuredOn: String): Boolean {
        val measured = runCatching { LocalDate.parse(measuredOn) }.getOrNull() ?: return false
        return measured.daysUntil(today()) <= MAX_NATIONAL_AVERAGE_AGE_DAYS
    }

    companion object {
        /** Past ~6 months a national average describes another market: fuel prices move with
         *  season, taxes and crises, and a figure that old under a tile called "approx. spend"
         *  stops being an estimate and starts being an invention. The table refreshes with every
         *  release, so this only bites installs that haven't updated in half a year. */
        const val MAX_NATIONAL_AVERAGE_AGE_DAYS = 180
    }
}
