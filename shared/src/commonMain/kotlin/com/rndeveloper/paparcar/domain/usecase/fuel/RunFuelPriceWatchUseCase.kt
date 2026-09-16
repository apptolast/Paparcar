@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.fuel

import com.rndeveloper.paparcar.domain.fuel.FuelPriceSample
import com.rndeveloper.paparcar.domain.fuel.localDayOf
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.repository.FuelWatchRepository
import com.rndeveloper.paparcar.domain.repository.VehicleRepository
import com.rndeveloper.paparcar.domain.usecase.entitlement.ObserveIsPlusUseCase
import com.rndeveloper.paparcar.domain.usecase.location.GetOneLocationUseCase
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

/**
 * What one run of the fuel watch produced — the caller (WorkManager on Android) turns this into
 * notifications and into its own retry vocabulary. [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001]
 */
sealed class FuelWatchOutcome {
    /** Nothing to do: not Plus, the switch is off, nothing followed, or the car burns nothing. */
    data class Skipped(val reason: String) : FuelWatchOutcome()

    /** Looked, and this is what there is to say. Either list may be empty — that is a real answer. */
    data class Looked(
        val moves: List<FuelPriceMove>,
        val cheaper: CheaperStationNearby?,
        val samplesRecorded: Int,
    ) : FuelWatchOutcome()

    /** Could not look (no fix, feed down). Nothing recorded, so the series keeps its shape. */
    data class Unavailable(val reason: String) : FuelWatchOutcome()
}

/**
 * One look at the prices this driver has a stake in: record today's reading, then ask the two
 * verdicts what is worth saying. [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001]
 *
 * The I/O shell around two pure evaluators — it fetches, writes and gates, and decides nothing. The
 * verdicts stay testable without a clock, a radio or a database.
 *
 * ⛔ **The gate is read live and it PAUSES, it does not forget.** Losing Plus makes this return
 * [FuelWatchOutcome.Skipped] without touching one followed station or one stored reading; regaining
 * it resumes the same series. Same downgrade rule as the zone alerts [MONEY-001].
 */
class RunFuelPriceWatchUseCase(
    private val watchRepository: FuelWatchRepository,
    private val vehicleRepository: VehicleRepository,
    private val getNearbyFuelStations: GetNearbyFuelStationsUseCase,
    private val getOneLocation: GetOneLocationUseCase,
    private val observeIsPlus: ObserveIsPlusUseCase,
    private val appPreferences: AppPreferences,
    private val evaluateDailyPriceMove: EvaluateDailyPriceMoveUseCase,
    private val evaluateCheaperStationNearby: EvaluateCheaperStationNearbyUseCase,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    suspend operator fun invoke(): FuelWatchOutcome {
        if (!observeIsPlus().first()) return FuelWatchOutcome.Skipped("not-plus")
        if (!appPreferences.notifyFuelPrices) return FuelWatchOutcome.Skipped("switch-off")

        val fuel = vehicleRepository.observeActiveVehicle().first()?.fuelType
            ?: return FuelWatchOutcome.Skipped("no-fuel-type")

        val followed = watchRepository.getFollowed()

        // The anchor is where the driver IS, not where a followed station sits: «cheaper nearby» is
        // a sentence about a detour from here, and a fix we do not have is not one we may invent.
        val here = getOneLocation(MAX_FIX_AGE_MS)
            ?: return FuelWatchOutcome.Unavailable("no-fix")

        val answer = getNearbyFuelStations(
            latitude = here.latitude,
            longitude = here.longitude,
            fuel = fuel,
        ).getOrElse { error ->
            PaparcarLogger.w(TAG, "nearby lookup failed", error)
            return FuelWatchOutcome.Unavailable("lookup-failed")
        }

        val now = clock()
        val today = localDayOf(now)
        val followedKeys = followed.map { it.stationKey }.toSet()

        // ⛔ Only followed stations are recorded. The history exists to answer «did YOUR price
        // move», and writing a row for every forecourt the camera happened to pass would grow an
        // unbounded table whose extra rows nobody ever reads.
        val samples = answer.stations
            .filter { it.stationKey in followedKeys }
            .mapNotNull { nearby ->
                val price = nearby.price ?: return@mapNotNull null
                FuelPriceSample(
                    stationKey = nearby.stationKey,
                    fuelType = fuel,
                    perUnit = price.perUnit,
                    reportedAtMs = price.reportedAtMs,
                    observedAtMs = now,
                    observedDay = today,
                    // Carried so tomorrow's comparison can refuse to compare two different pumps.
                    // [FUEL-ITALY-A-PRICE-DEPENDS-ON-WHO-PUMPS-IT-001]
                    isSelf = price.isSelf,
                )
            }
        watchRepository.recordSamples(samples)
        watchRepository.prune()

        val historyByStation = followed.associate { station ->
            station.stationKey to watchRepository.historyFor(station.stationKey, fuel)
        }

        return FuelWatchOutcome.Looked(
            moves = evaluateDailyPriceMove(
                followed = followed,
                fuelType = fuel,
                samplesByStation = historyByStation,
            ),
            cheaper = evaluateCheaperStationNearby(
                latitude = here.latitude,
                longitude = here.longitude,
                fuelType = fuel,
                // The key the ANSWER already built — never re-derived from a bare id here.
                candidates = answer.stations.associate { it.stationKey to it.station },
                referenceStationKeys = followedKeys,
            ),
            samplesRecorded = samples.size,
        )
    }

    private companion object {
        const val TAG = "FuelWatch"

        /**
         * Ten minutes. The watch runs once a day and a fix that old still says which town you are
         * in, which is the resolution «a cheaper pump 2 km away» actually needs — asking the GPS for
         * a fresh fix to answer a question about yesterday's prices would spend battery for nothing.
         */
        const val MAX_FIX_AGE_MS = 10L * 60 * 1_000
    }
}
