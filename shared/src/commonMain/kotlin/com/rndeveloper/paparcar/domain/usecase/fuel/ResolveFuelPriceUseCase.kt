package com.rndeveloper.paparcar.domain.usecase.fuel

import com.rndeveloper.paparcar.domain.fuel.FuelPrice
import com.rndeveloper.paparcar.domain.fuel.FuelPriceOrigin
import com.rndeveloper.paparcar.domain.fuel.StaticAverageFuelPrices
import com.rndeveloper.paparcar.domain.model.Money
import com.rndeveloper.paparcar.domain.model.Vehicle

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
class ResolveFuelPriceUseCase {

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
        return StaticAverageFuelPrices.averageLitre(countryCode, fuel)
            ?.let { FuelPrice(it, FuelPriceOrigin.NationalAverage) }
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
        return StaticAverageFuelPrices.averageKwh(countryCode)
            ?.let { FuelPrice(it, FuelPriceOrigin.NationalAverage) }
    }
}
