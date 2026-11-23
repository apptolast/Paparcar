@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.stats

import com.rndeveloper.paparcar.domain.detection.ParkingDetectionSource
import com.rndeveloper.paparcar.domain.detection.detectionSource
import com.rndeveloper.paparcar.domain.model.Money
import com.rndeveloper.paparcar.domain.model.RefuelStopAnswer
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.Zone
import com.rndeveloper.paparcar.domain.stats.EmissionFactors
import com.rndeveloper.paparcar.domain.stats.PeriodStats
import com.rndeveloper.paparcar.domain.stats.clusterParkings
import com.rndeveloper.paparcar.domain.stats.sumMeasuredDistance
import com.rndeveloper.paparcar.domain.stats.sumMeasuredSearchTime
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
        /** The user's named places. Without them a cluster still exists — it is just called by its
         *  street instead of "Trabajo". [STATS-A-USUAL-PLACE-IS-A-CLUSTER-NOT-A-STREET-001] */
        zones: List<Zone> = emptyList(),
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

        // Where you REPEAT, as patches of ground rather than as strings a geocoder returned.
        // Over the ENDED sessions, like every other habit figure here: an open parking has not
        // told us what it cost yet. [STATS-A-USUAL-PLACE-IS-A-CLUSTER-NOT-A-STREET-001]
        val places = clusterParkings(ended, zones)

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
            places = places,
            spotsReleased = ended.count { it.publishedSpot },
            // [DET-A-STOP-AT-A-PUMP-IS-NOT-A-PARKING-001] Dated like every other habit figure here
            // — by the session's own clock — so answering an old stop from the history credits the
            // period the stop belongs to, not the period the user happened to answer in.
            refuelsConfirmed = ended.count { it.refuelStop == RefuelStopAnswer.CONFIRMED },
            // ⛔ DOS cajones excluidos, y el segundo se añadió para que esta cifra NO cambie.
            // [DET-A-YES-IS-NOT-A-HAND-001]
            //
            // Esto decía `!= Manual`, cuando `Manual` significaba a la vez «lo puso tu mano» y «la
            // app preguntó y dijiste que sí». Al separarlos, dejarlo como estaba habría empezado a
            // contar como auto-detectada una sesión que la app **tuvo que preguntar** — mentir en
            // la otra dirección. Si hubo que preguntar, no se detectó solo.
            autoDetectedCount = if (shareSignificant) {
                known.count { it.detectionSource() !in NOT_DETECTED_ALONE }
            } else {
                0
            },
            detectionKnownCount = if (shareSignificant) known.size else 0,
            // The pitch's flagship figure: time LOST hunting in the period — a sum, not an
            // average, because it is a cost. Sessions without the measure contribute nothing
            // (absent, never 0), and the sum says so: it travels with its coverage exactly like
            // the distance does, through the same builder. [TRIP-THE-SEARCH-FOR-A-SPOT-IS-MEASURED-001]
            // [STATS-A-MEASURE-CARRIES-ITS-COVERAGE-001]
            searchTime = sumMeasuredSearchTime(sessions),
            // The previous period never nests its own previous — one step back is the comparison.
            previous = previousSessions?.let {
                invoke(
                    sessions = it,
                    vehicles = vehicles,
                    previousSessions = null,
                    zones = zones,
                    fuelPricePerLiter = fuelPricePerLiter,
                    energyPricePerKwh = energyPricePerKwh,
                )
            },
        )
    }

    companion object {
        /** Los orígenes que NO cuentan como «la app lo detectó sola»: la mano del usuario, y la
         *  respuesta a un aviso que la app tuvo que lanzar. [DET-A-YES-IS-NOT-A-HAND-001] */
        private val NOT_DETECTED_ALONE = setOf(
            ParkingDetectionSource.Manual,
            ParkingDetectionSource.Confirmed,
        )

        const val MIN_SESSIONS_FOR_PEAK = 5
        const val MIN_SESSIONS_FOR_AUTO_SHARE = 5
        const val MIN_SESSIONS_FOR_DURATION = 3
        private const val METERS_PER_KM = 1_000.0
        private const val PER_100_KM = 100.0
    }
}
