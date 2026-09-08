@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.stats

import com.rndeveloper.paparcar.domain.detection.ParkingDetectionSource
import com.rndeveloper.paparcar.domain.detection.detectionSource
import com.rndeveloper.paparcar.domain.model.Money
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.stats.EmissionFactors
import com.rndeveloper.paparcar.domain.stats.PeriodStats
import com.rndeveloper.paparcar.domain.stats.sumMeasuredDistance
import com.rndeveloper.paparcar.domain.vehicle.FuelDefaults
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * THE stats aggregator — one pure, synchronous computation every stats surface derives from
 * (the F4 panel, the teaser, the history block's `HistoryStatsData`). [STATS-CORE-001]
 *
 * Inputs arrive ALREADY GATED (`gateHistoryToEntitlement` — a free user's figures are computed
 * over what they may see, anything else is a paywall with a hole) and ALREADY SLICED to the
 * period (window authority stays in `VehicleHistoryCalculator.windowStartMs`, the one place a
 * time window is decided). No clock in here: every date comes from the sessions themselves.
 *
 * Consumption honesty: litres/kWh/cost/CO₂ are computed ONLY over sessions with a MEASURED route
 * distance, using that session's vehicle (override, or the named `FuelDefaults`) — and
 * [PeriodStats.distance] carries the coverage, so a subtotal can never print as a total
 * [UI-HISTORY-A-PARTIAL-SUM-IS-NOT-A-TOTAL-001]. A plug-in hybrid contributes both energies —
 * a declared approximation (the UI labels these figures "aprox.").
 */
class ComputePeriodStatsUseCase {

    operator fun invoke(
        sessions: List<UserParking>,
        vehicles: List<Vehicle> = emptyList(),
        /** The equivalent previous period, already sliced by the caller — feeds ▲/▼ comparisons. */
        previousSessions: List<UserParking>? = null,
        /** Prices land with FUEL-PRICES-001; until then null ⇒ [PeriodStats.costEstimate] = null. */
        fuelPricePerLiter: Money? = null,
        energyPricePerKwh: Money? = null,
    ): PeriodStats? {
        if (sessions.isEmpty()) return null
        val ended = sessions.filter { !it.isActive }
        val tz = TimeZone.currentSystemDefault()

        // ── Energy over the measured distance ────────────────────────────────
        var liters = 0.0
        var kwh = 0.0
        var co2 = 0.0
        var litersContributed = false
        var kwhContributed = false
        sessions.forEach { session ->
            val km = (session.routeDistanceMeters ?: return@forEach) / METERS_PER_KM
            val vehicle = vehicles.firstOrNull { it.id == session.vehicleId } ?: return@forEach
            val fuel = vehicle.fuelType ?: return@forEach
            val primaryPer100 = vehicle.consumptionPer100
                ?: FuelDefaults.defaultConsumptionPer100(fuel, vehicle.vehicleType)
            val primary = km * primaryPer100 / PER_100_KM
            if (fuel.usesLiters) {
                liters += primary
                litersContributed = true
                EmissionFactors.kgCo2PerLiter(fuel)?.let { co2 += primary * it }
            } else {
                // Pure EV: the primary figure IS kWh.
                kwh += primary
                kwhContributed = true
                co2 += primary * EmissionFactors.KG_CO2_PER_KWH_EU_MIX
            }
            if (fuel.hasSecondaryElectric) {
                val electricPer100 = vehicle.consumptionElectricPer100
                    ?: FuelDefaults.defaultElectricConsumptionPer100(fuel)
                if (electricPer100 != null) {
                    val electric = km * electricPer100 / PER_100_KM
                    kwh += electric
                    kwhContributed = true
                    co2 += electric * EmissionFactors.KG_CO2_PER_KWH_EU_MIX
                }
            }
        }
        val anyEnergy = litersContributed || kwhContributed

        // ── Cost: every consumed energy needs ITS price, or there is no cost ─
        val costEstimate: Money? = run {
            if (!anyEnergy) return@run null
            if (litersContributed && fuelPricePerLiter == null) return@run null
            if (kwhContributed && energyPricePerKwh == null) return@run null
            val currencies = listOfNotNull(
                fuelPricePerLiter?.takeIf { litersContributed }?.currency,
                energyPricePerKwh?.takeIf { kwhContributed }?.currency,
            ).distinct()
            // Two energies priced in two currencies cannot be summed honestly.
            val currency = currencies.singleOrNull() ?: return@run null
            Money(
                amount = liters * (fuelPricePerLiter?.amount ?: 0.0) +
                    kwh * (energyPricePerKwh?.amount ?: 0.0),
                currency = currency,
            )
        }

        // ── Time-shaped stats over ENDED sessions ────────────────────────────
        val durations = ended.mapNotNull { s ->
            s.endedAtMs?.let { end -> (end - s.location.timestamp).takeIf { it > 0 } }
        }
        val avgParkedDurationMs = durations
            .takeIf { it.size >= MIN_SESSIONS_FOR_DURATION }
            ?.let { it.sum() / it.size }

        val peakHourBucket = ended
            .takeIf { it.size >= MIN_SESSIONS_FOR_PEAK }
            ?.groupingBy {
                Instant.fromEpochMilliseconds(it.location.timestamp).toLocalDateTime(tz).hour
            }
            ?.eachCount()
            ?.maxByOrNull { it.value }
            ?.key

        val mostActiveDayOfWeek = ended
            .takeIf { it.size >= MIN_SESSIONS_FOR_PEAK }
            ?.groupingBy {
                Instant.fromEpochMilliseconds(it.location.timestamp)
                    .toLocalDateTime(tz).date.dayOfWeek.isoDayNumber
            }
            ?.eachCount()
            ?.maxByOrNull { it.value }
            ?.key

        // A street is "usual" when it repeats, not when it merely wins a 1-1 tie.
        val favoriteStreet = ended
            .mapNotNull { it.address?.street?.takeIf { s -> s.isNotBlank() } }
            .groupBy { it }
            .maxByOrNull { it.value.size }
            ?.takeIf { it.value.size >= MIN_SESSIONS_FOR_STREET }
            ?.key

        // One resolver for "who placed this pin" — never a second set of path strings here.
        // [UI-HISTORY-IDENTITY-AND-SOURCE-001]
        val known = ended.filter { it.detectionSource() != ParkingDetectionSource.Unknown }
        val shareSignificant = known.size >= MIN_SESSIONS_FOR_AUTO_SHARE

        return PeriodStats(
            parkings = sessions.size,
            endedParkings = ended.size,
            distance = sumMeasuredDistance(sessions),
            fuelLiters = liters.takeIf { litersContributed },
            energyKwh = kwh.takeIf { kwhContributed },
            costEstimate = costEstimate,
            co2Kg = co2.takeIf { anyEnergy },
            avgParkedDurationMs = avgParkedDurationMs,
            peakHourBucket = peakHourBucket,
            mostActiveDayOfWeek = mostActiveDayOfWeek,
            favoriteStreet = favoriteStreet,
            spotsReleased = ended.count { it.publishedSpot },
            autoDetectedCount = if (shareSignificant) {
                known.count { it.detectionSource() != ParkingDetectionSource.Manual }
            } else {
                0
            },
            detectionKnownCount = if (shareSignificant) known.size else 0,
            searchTimeMs = null,
            // The previous period never nests its own previous — one step back is the comparison.
            previous = previousSessions?.let {
                invoke(
                    sessions = it,
                    vehicles = vehicles,
                    previousSessions = null,
                    fuelPricePerLiter = fuelPricePerLiter,
                    energyPricePerKwh = energyPricePerKwh,
                )
            },
        )
    }

    companion object {
        const val MIN_SESSIONS_FOR_PEAK = 5
        const val MIN_SESSIONS_FOR_STREET = 3
        const val MIN_SESSIONS_FOR_AUTO_SHARE = 5
        const val MIN_SESSIONS_FOR_DURATION = 3
        private const val METERS_PER_KM = 1_000.0
        private const val PER_100_KM = 100.0
    }
}
