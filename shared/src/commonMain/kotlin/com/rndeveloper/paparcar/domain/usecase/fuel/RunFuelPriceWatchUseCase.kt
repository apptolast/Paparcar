@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.fuel

import com.rndeveloper.paparcar.domain.fuel.FollowedFuelStation
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

        // Las respuestas que ya tenemos en este tick. La del usuario entra primero porque hace
        // falta de todos modos para «más barata cerca», así que una seguida que caiga en su radio
        // no cuesta una consulta extra.
        val lookups = mutableListOf(answer.stations)
        var extraLookups = 0

        /**
         * El precio de ESTA seguida hoy, preguntando **donde está la gasolinera** si la respuesta
         * del usuario no la cubre. [FUEL-A-FOLLOW-MUST-SHOW-ITSELF-001]
         *
         * ⛔ Esto antes era `answer.stations.filter { it.stationKey in followedKeys }`, y ahí vivía
         * el bug: la respuesta del usuario tiene un radio de 5 km alrededor del fix, así que una
         * seguida más lejos **no grababa muestra ese día**. Con dos muestras de días distintos como
         * requisito del veredicto ([EvaluateDailyPriceMoveUseCase]), la serie solo crecía los días
         * en que el usuario pasaba cerca a la hora exacta del tick — y el aviso era inalcanzable
         * justo para las gasolineras que uno sigue porque NO las tiene delante. El almacén ya
         * copiaba la posición para esto y no se usaba.
         *
         * Cada respuesta nueva se guarda y sirve para el resto: N seguidas en una provincia cuestan
         * UNA consulta.
         */
        suspend fun pricedToday(target: FollowedFuelStation): NearbyFuelStation? {
            lookups.firstNotNullOfOrNull { list -> list.firstOrNull { it.stationKey == target.stationKey } }
                ?.let { return it }
            // ⛔ Techo por tick: cada disco nuevo es la descarga de una provincia entera, y el coste
            // de datos de seguir gasolineras no puede crecer sin límite. Se registra cuando muerde
            // — una muestra que no se toma es un hueco en la serie, no un precio que no se movió.
            if (extraLookups >= MAX_LOOKUPS_PER_RUN) {
                PaparcarLogger.d(TAG) { "· ${target.stationKey} sin muestra hoy — presupuesto de consultas agotado" }
                return null
            }
            extraLookups++
            val around = getNearbyFuelStations(
                latitude = target.latitude,
                longitude = target.longitude,
                fuel = fuel,
                radiusMeters = AROUND_FOLLOWED_RADIUS_M,
            ).getOrElse { error ->
                PaparcarLogger.w(TAG, "lookup around ${target.stationKey} failed", error)
                return null
            }
            lookups += around.stations
            return around.stations.firstOrNull { it.stationKey == target.stationKey }
        }

        // ⛔ Only followed stations are recorded. The history exists to answer «did YOUR price
        // move», and writing a row for every forecourt the camera happened to pass would grow an
        // unbounded table whose extra rows nobody ever reads.
        val samples = followed
            .mapNotNull { target ->
                val nearby = pricedToday(target) ?: return@mapNotNull null
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
                // The clock the whole run already shares, so the freshness floor ages prices
                // against the same instant the samples were stamped with.
                // [FUEL-A-CHEAP-PRICE-WITH-NO-FLOOR-IS-THE-STALEST-ONE-001]
                nowMs = now,
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

        /**
         * Radio al preguntar **por una seguida, en su propio sitio**. Un kilómetro porque la
         * gasolinera está en el centro exacto de la consulta: lo que se pide no es «qué hay por
         * aquí» sino «este surtidor, hoy». [FUEL-A-FOLLOW-MUST-SHOW-ITSELF-001]
         */
        const val AROUND_FOLLOWED_RADIUS_M = 1_000

        /**
         * Consultas extra por tick, además de la del usuario. Ocho es más seguidas de las que un
         * conductor tiene —se siguen las que se usan, no un catálogo— y a la vez un techo duro:
         * cada consulta en un sitio nuevo baja el feed de una provincia entera, y un follow no
         * puede costar datos sin límite una vez al día. Lo que no entra queda registrado.
         */
        const val MAX_LOOKUPS_PER_RUN = 8
    }
}
