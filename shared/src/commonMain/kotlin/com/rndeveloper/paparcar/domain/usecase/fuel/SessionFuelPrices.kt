package com.rndeveloper.paparcar.domain.usecase.fuel

import com.rndeveloper.paparcar.domain.fuel.FuelPrice
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Vehicle

/**
 * The two rungs a period's cost can stand on, resolved together.
 * [FUEL-PRICE-RESOLVER-001] [STATS-A-PACE-ROW-NAMES-THE-PERIOD-IT-PROJECTS-001]
 *
 * A plug-in hybrid needs both; every other car needs one; and the aggregate refuses to price a
 * period that has only half of what it burned. Keeping the pair in one value is what lets a caller
 * hand both to `ComputePeriodStatsUseCase` without deciding anything.
 */
data class SessionFuelPrices(val litre: FuelPrice?, val kwh: FuelPrice?) {
    /**
     * The one to CAPTION the figure with. The two rungs cannot disagree — one resolver, one order —
     * so either answers for the label, and the litre is asked first because most cars only have it.
     */
    val quoted: FuelPrice? get() = litre ?: kwh
}

/**
 * What [vehicle]'s energy cost where these [sessions] happened.
 *
 * ⛔ **Extracted so the SECOND consumer could exist.** This lived private inside
 * `HistoryTimeline.resolve`, so the month-recap use case — which computes the same figures for the
 * notification — had no way to ask for a price without copying the country rule. It did not copy
 * it: it passed no prices at all, and the recap's spend row was therefore unreachable code from the
 * day it was written. [STATS-A-PACE-ROW-NAMES-THE-PERIOD-IT-PROJECTS-001]
 */
fun ResolveFuelPriceUseCase.forSessions(
    vehicle: Vehicle?,
    sessions: List<UserParking>,
): SessionFuelPrices {
    if (vehicle == null) return SessionFuelPrices(litre = null, kwh = null)
    val countryCode = sessions.pricingCountryCode()
    return SessionFuelPrices(
        litre = litre(vehicle, countryCode),
        kwh = kwh(vehicle, countryCode),
    )
}

/**
 * Where this period happened, for pricing purposes: the country of the MOST RECENT parking that
 * knows its own. A period that straddles a border is priced where the driver ended up, which is both
 * the likelier place they refuel and a decision worth naming — the alternative (pricing each session
 * in its own country and summing) would produce a total in mixed currencies, and the aggregate
 * already refuses those. [FUEL-PRICE-RESOLVER-001]
 */
private fun List<UserParking>.pricingCountryCode(): String? =
    maxByOrNull { it.location.timestamp }?.address?.countryCode
        ?: firstNotNullOfOrNull { it.address?.countryCode }
