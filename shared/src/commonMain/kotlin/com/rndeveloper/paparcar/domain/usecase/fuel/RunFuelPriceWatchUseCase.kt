@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.fuel

import com.rndeveloper.paparcar.domain.fuel.FollowedFuelStation
import com.rndeveloper.paparcar.domain.fuel.FuelPriceSample
import com.rndeveloper.paparcar.domain.fuel.WatchedForecourt
import com.rndeveloper.paparcar.domain.fuel.asWatched
import com.rndeveloper.paparcar.domain.fuel.localDayOf
import com.rndeveloper.paparcar.domain.onboarding.NearbyComplement
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

    /** Looked, and this is what there is to say. Any list may be empty — that is a real answer. */
    data class Looked(
        /** Lo que hicieron las que SIGUES — carril Plus. [FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001] */
        val followedMoves: List<FuelPriceMove>,
        /**
         * Lo que hicieron las de TU ALREDEDOR — carril free.
         *
         * ⛔ Dos listas y no una con bandera: lo que cambia entre carriles no es un matiz del dato,
         * es la FRASE que se dice («la que sigues» frente a «una cerca de ti») y la tarjeta en la
         * que sale. Juntarlas obligaría a cada consumidor a volver a separarlas, y el día que uno
         * se olvide le contará al usuario que sigue una gasolinera que nunca estrelló.
         */
        val nearbyMoves: List<FuelPriceMove>,
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
 * ⛔ **Dos carriles, un tick.** [FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001]
 *  · **Tu alrededor** (free): «la más barata cerca», comparada contra la mediana de la zona.
 *  · **Las que sigues** (Plus): su movimiento diario, su serie de 30 días y tu propia referencia.
 * El plan no abre la puerta: decide qué carril corre. Un free con el interruptor puesto recibe lo
 * primero, y es la mitad que ahorra dinero sin pagar nada.
 *
 * ⛔ **El plan se lee en vivo y PAUSA, no olvida.** Perder Plus apaga el carril de las seguidas sin
 * tocar una sola seguida ni una sola lectura guardada; recuperarlo reanuda la misma serie. Misma
 * regla de bajada que las alertas de zona [MONEY-001].
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
        if (!appPreferences.notifyFuelPrices) return FuelWatchOutcome.Skipped("switch-off")

        // ⛔ **El plan ya no abre la puerta, decide QUÉ CARRIL corre.**
        // [FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001] Hasta el 13-10 esto era
        // `if (!isPlus) return Skipped("not-plus")`, así que un free no se enteraba ni de que la
        // gasolinera de su calle había bajado 4 c/l. Decisión del user: lo que pasa a tu ALREDEDOR
        // se avisa gratis; lo que vende Plus es el SUJETO que tú nombraste —vigilado esté donde
        // esté— y su memoria.
        val isPlus = observeIsPlus().first()
        val followed = watchRepository.getFollowed()

        // El carril de las seguidas: sujeto propio, su serie de 30 días y su referencia. Exige Plus
        // **y** algo seguido — sin sujeto no hay nada que vigilar.
        val watchesFollowed = isPlus && followed.isNotEmpty()

        // ⛔ **El freno del carril free, y es deliberado**: solo avisa a quien ha preguntado por
        // gasolineras alguna vez. Sin él la app notifica precios a quien nunca los ha mirado y baja
        // un feed de provincia al día para todo el mundo — es lo que el carril free de PLAZAS
        // consigue exigiendo el mapa delante [ZONE-AN-ALERT-NEAR-YOU-IS-NOT-AN-ALERT-IN-YOUR-ZONE-001],
        // y aquí no se puede exigir eso porque el aviso llega con la app cerrada.
        // El sello ya existía: es el mismo que decide si el carril debe decir de dónde salen sus
        // precios la primera vez. [ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001]
        val hasAskedAboutFuel = NearbyComplement.FUEL in appPreferences.observeIntroducedComplements().first()
        if (!hasAskedAboutFuel && !watchesFollowed) return FuelWatchOutcome.Skipped("never-asked-about-fuel")

        val fuel = vehicleRepository.observeActiveVehicle().first()?.fuelType
            ?: return FuelWatchOutcome.Skipped("no-fuel-type")

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

        // La lectura de hoy de un surtidor, sea de quien sea el carril: una sola fábrica, para que
        // las dos series se escriban con las mismas reglas. Sin precio no hay muestra — un precio
        // ausente no es un cero.
        fun sampleOf(nearby: NearbyFuelStation): FuelPriceSample? {
            val price = nearby.price ?: return null
            return FuelPriceSample(
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

        // ── Carril de las SEGUIDAS (Plus): 30 días, preguntadas donde están ──────────────────
        val followedSamples = followed
            .takeIf { watchesFollowed }
            .orEmpty()
            .mapNotNull { target -> pricedToday(target)?.let(::sampleOf) }

        // ── Carril de TU ALREDEDOR (free): las más cercanas con precio que NO sigues ─────────
        //
        // ⛔ **Acotado, y el techo es la mitad del diseño.** [FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001]
        // La tabla de la serie se escribió solo para seguidas con esta razón: «escribir una fila por
        // cada gasolinera que pase la cámara haría crecer una tabla sin techo». Sigue siendo verdad,
        // así que esto no abre la puerta: la entreabre a las [NEARBY_SAMPLED_MAX] más cercanas de la
        // respuesta de HOY, con retención de 3 días.
        //
        // ⛔ Y las seguidas se excluyen aquí: pertenecen al otro carril. Sin este filtro el mismo
        // surtidor produciría dos movimientos el mismo día y dos tarjetas que se contradicen en el
        // sujeto.
        val nearbyStations = answer.stations
            .filter { it.stationKey !in followedKeys }
            .filter { it.price != null }
            .sortedBy { it.distanceMeters }
            .take(NEARBY_SAMPLED_MAX)
        val nearbySamples = nearbyStations.mapNotNull(::sampleOf)

        watchRepository.recordSamples(followedSamples + nearbySamples)
        watchRepository.prune()

        val followedSubjects = followed.takeIf { watchesFollowed }.orEmpty().map { it.asWatched() }
        val nearbySubjects = nearbyStations.map { it.asWatched() }
        val historyByStation = (followedSubjects + nearbySubjects).associate { subject ->
            subject.stationKey to watchRepository.historyFor(subject.stationKey, fuel)
        }

        return FuelWatchOutcome.Looked(
            // El MISMO veredicto, dos veces, con sujetos distintos: «se movió este precio» no es
            // una pregunta distinta según quién mire. [FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001]
            followedMoves = evaluateDailyPriceMove(
                subjects = followedSubjects,
                fuelType = fuel,
                samplesByStation = historyByStation,
            ),
            nearbyMoves = evaluateDailyPriceMove(
                subjects = nearbySubjects,
                fuelType = fuel,
                samplesByStation = historyByStation,
            ),
            cheaper = evaluateCheaperStationNearby(
                latitude = here.latitude,
                longitude = here.longitude,
                fuelType = fuel,
                // The key the ANSWER already built — never re-derived from a bare id here.
                candidates = answer.stations.associate { it.stationKey to it.station },
                // ⛔ La REFERENCIA es la mitad vendible de este aviso: con Plus se compara contra
                // TU gasolinera («8 c/l menos que donde sueles repostar»), y sin Plus contra la
                // mediana de la zona —que el veredicto ya sabe hacer, y que es una frase igual de
                // verdadera y bastante menos tuya. [FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001]
                referenceStationKeys = if (isPlus) followedKeys else emptySet(),
                // The clock the whole run already shares, so the freshness floor ages prices
                // against the same instant the samples were stamped with.
                // [FUEL-A-CHEAP-PRICE-WITH-NO-FLOOR-IS-THE-STALEST-ONE-001]
                nowMs = now,
            ),
            samplesRecorded = followedSamples.size + nearbySamples.size,
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

        /**
         * Cuántas de tu alrededor se miden cada día. [FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001]
         *
         * Diez, y es un techo de ESCRITURA, no de respuesta: el carril sigue enseñando todas las
         * que haya en 5 km. Diez es lo que cabe en «una de tu alrededor ha bajado» sin convertir la
         * tabla en un diario de por dónde pasas — con retención de 3 días son 30 filas vivas por
         * conductor, frente a las ~90 que deja una sola seguida.
         */
        const val NEARBY_SAMPLED_MAX = 10
    }
}

/**
 * Una gasolinera de la respuesta de hoy, como sujeto de su serie corta.
 * [FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001]
 *
 * Vive aquí y no junto a [WatchedForecourt] a propósito: el modelo de la serie no tiene por qué
 * conocer la forma de una RESPUESTA del carril, que es un valor de la capa de casos de uso.
 */
private fun NearbyFuelStation.asWatched() = WatchedForecourt(
    stationKey = stationKey,
    name = station.name,
    latitude = station.latitude,
    longitude = station.longitude,
)
