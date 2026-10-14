@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.fuel

import com.rndeveloper.paparcar.domain.fuel.FollowedFuelStation
import com.rndeveloper.paparcar.domain.fuel.FuelStation
import com.rndeveloper.paparcar.domain.fuel.FuelStationsQuery
import com.rndeveloper.paparcar.domain.fuel.FuelStationsResult
import com.rndeveloper.paparcar.domain.fuel.FuelPriceSample
import com.rndeveloper.paparcar.domain.fuel.FuelStationsSource
import com.rndeveloper.paparcar.domain.fuel.StationPrice
import com.rndeveloper.paparcar.domain.fuel.fuelStationKey
import com.rndeveloper.paparcar.domain.fuel.localDayOf
import com.rndeveloper.paparcar.domain.geocoder.GeocoderDataSource
import com.rndeveloper.paparcar.domain.location.LocationDataSource
import com.rndeveloper.paparcar.domain.location.UserLocationUi
import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.Entitlement
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.Money
import com.rndeveloper.paparcar.domain.model.SearchResult
import com.rndeveloper.paparcar.domain.onboarding.NearbyComplement
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.usecase.entitlement.ObserveIsPlusUseCase
import com.rndeveloper.paparcar.domain.usecase.location.GetOneLocationUseCase
import com.rndeveloper.paparcar.domain.util.haversineMeters
import com.rndeveloper.paparcar.fakes.FakeAppPreferences
import com.rndeveloper.paparcar.fakes.FakeEntitlementRepository
import com.rndeveloper.paparcar.fakes.FakeFuelWatchRepository
import com.rndeveloper.paparcar.fakes.FakeVehicleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * El shell de I/O del vigilante de precios. [FUEL-A-FOLLOW-MUST-SHOW-ITSELF-001]
 *
 * ⛔ No existía, y por eso el fallo que prueba el primer test vivió desde que se escribió la
 * función: los dos veredictos tenían sus tests —son puros— y lo que nadie miró fue **de dónde
 * salían las muestras que comparan**. Una muestra que no se toma no rompe ningún test de un
 * evaluador: lo deja sin nada que decir, que es exactamente igual que un precio que no se movió.
 */
class RunFuelPriceWatchUseCaseTest {

    // ── Dobles ───────────────────────────────────────────────────────────────

    /**
     * Un mundo de gasolineras que contesta **por radio**, como un feed de verdad: lo que cae dentro
     * de `radiusMeters` de donde se pregunta. Es lo que hace que el 5 km de la respuesta del usuario
     * y el 1 km de la consulta por una seguida sean reales en el test y no un acuerdo entre fakes.
     */
    private class FakeStationsSource(private val world: List<FuelStation>) : FuelStationsSource {
        val queries = mutableListOf<FuelStationsQuery>()
        var failAt: Pair<Double, Double>? = null

        override fun covers(countryCode: String) = true

        override suspend fun stationsNear(query: FuelStationsQuery): Result<FuelStationsResult> {
            queries += query
            failAt?.let { (lat, lon) ->
                if (haversineMeters(query.latitude, query.longitude, lat, lon) < 500) {
                    return Result.failure(IllegalStateException("feed down"))
                }
            }
            val within = world.filter {
                haversineMeters(query.latitude, query.longitude, it.latitude, it.longitude) <=
                    query.radiusMeters
            }
            return Result.success(
                FuelStationsResult(stations = within, reportedAt = REPORTED_AT, source = SOURCE),
            )
        }
    }

    private class FixedGeocoder : GeocoderDataSource {
        override suspend fun getAddress(lat: Double, lon: Double): Result<AddressInfo> =
            Result.success(
                AddressInfo(
                    street = null, city = "Madrid", region = "Comunidad de Madrid",
                    country = "España", countryCode = "ES", province = "Madrid",
                ),
            )

        override suspend fun searchByName(query: String, maxResults: Int): Result<List<SearchResult>> =
            Result.success(emptyList())
    }

    /** Un fix y nada más: `GetOneLocationUseCase` toma el primero que pase su filtro de edad. */
    private class OneFixSource(private val fix: GpsPoint?) : LocationDataSource {
        override fun observeBalancedLocation(): Flow<GpsPoint> =
            fix?.let { flowOf(it) } ?: emptyFlow()
        override fun observeCruiseLocation(): Flow<GpsPoint> = emptyFlow()
        override fun observeHighAccuracyLocation(): Flow<GpsPoint> = emptyFlow()
        override fun observeUiLocation(): Flow<UserLocationUi> = emptyFlow()
        override fun observePassiveLocation(): Flow<GpsPoint> = emptyFlow()
        override suspend fun getLastKnownLocation(): GpsPoint? = fix
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private fun station(id: String, lat: Double, lon: Double, perLitre: Double?) = FuelStation(
        id = id,
        name = "Gasolinera $id",
        address = null,
        town = null,
        latitude = lat,
        longitude = lon,
        prices = perLitre?.let {
            mapOf(
                FuelType.DIESEL to StationPrice(
                    perUnit = Money(amount = it, currency = "EUR"),
                    reportedAtMs = NOW_MS - 3_600_000,
                ),
            )
        } ?: emptyMap(),
    )

    /**
     * Tres a tiro de desvío del usuario, porque **la mediana de dos es la más barata**
     * (`sorted[(size - 1) / 2]`) y entonces el ahorro sale cero. Con 1,70 · 1,70 · 1,60 la mediana
     * es 1,70 y la barata ahorra 10 c/l, por encima del suelo de 3.
     */
    private val AROUND_THE_USER = listOf(
        station("normal-a", USER_LAT, USER_LON, 1.700),
        station("normal-b", USER_LAT + 0.005, USER_LON, 1.700),
        station("cheap", USER_LAT + 0.008, USER_LON, 1.600),
    )

    private fun followed(id: String, lat: Double, lon: Double) = FollowedFuelStation(
        stationKey = fuelStationKey(SOURCE, id),
        source = SOURCE,
        id = id,
        name = "Gasolinera $id",
        latitude = lat,
        longitude = lon,
        followedAtMs = NOW_MS - 86_400_000,
    )

    private class Harness(
        world: List<FuelStation>,
        plus: Boolean = true,
        /** El sello de [NearbyComplement.FUEL]: «este usuario ha abierto el carril alguna vez», que
         *  es lo que arma el carril free. [FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001] */
        askedAboutFuel: Boolean = true,
        fix: GpsPoint? = GpsPoint(
            latitude = USER_LAT, longitude = USER_LON, accuracy = 8f, timestamp = NOW_MS,
            speed = 0f,
        ),
    ) {
        val source = FakeStationsSource(world)
        val watchRepository = FakeFuelWatchRepository()
        val preferences = FakeAppPreferences().apply {
            setIntroducedComplements(if (askedAboutFuel) setOf(NearbyComplement.FUEL) else emptySet())
        }
        val useCase = RunFuelPriceWatchUseCase(
            watchRepository = watchRepository,
            vehicleRepository = FakeVehicleRepository(
                defaultVehicle = Vehicle(
                    id = "v-1",
                    userId = "u-1",
                    sizeCategory = VehicleSize.MEDIUM_SUV,
                    fuelType = FuelType.DIESEL,
                    isActive = true,
                ),
            ),
            getNearbyFuelStations = GetNearbyFuelStationsUseCase(source, FixedGeocoder()),
            getOneLocation = GetOneLocationUseCase(
                locationDataSource = OneFixSource(fix),
                nowMs = { NOW_MS },
            ),
            observeIsPlus = ObserveIsPlusUseCase(
                FakeEntitlementRepository(if (plus) Entitlement.PLUS else Entitlement.FREE),
            ),
            appPreferences = preferences,
            evaluateDailyPriceMove = EvaluateDailyPriceMoveUseCase(),
            evaluateCheaperStationNearby = EvaluateCheaperStationNearbyUseCase(),
            clock = { NOW_MS },
        )
    }

    // ── La regresión ─────────────────────────────────────────────────────────

    /**
     * ⛔ **El fallo que abrió [FUEL-A-FOLLOW-MUST-SHOW-ITSELF-001].** La muestra se tomaba filtrando
     * la respuesta del usuario, que tiene 5 km de radio, así que una seguida más lejos no grababa
     * nada ese día — y sin dos muestras de días distintos el veredicto de movimiento no existe.
     * Justo las gasolineras que uno sigue porque no las tiene delante eran las que nunca podían
     * avisar.
     */
    @Test
    fun should_recordASampleForAFollowedStation_when_itSitsFarFromTheUsersFix() = runTest {
        val far = station("far", FAR_LAT, FAR_LON, perLitre = 1.579)
        val harness = Harness(world = listOf(station("near", USER_LAT, USER_LON, 1.699), far))
        harness.watchRepository.follow(followed("far", FAR_LAT, FAR_LON))

        val looked = assertIs<FuelWatchOutcome.Looked>(harness.useCase())

        // Dos: la seguida de lejos y la de al lado del usuario, que la mide el carril free.
        // [FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001]
        assertEquals(2, looked.samplesRecorded)
        val series = harness.watchRepository.historyFor(
            fuelStationKey(SOURCE, "far"), FuelType.DIESEL, 30,
        )
        assertEquals(1, series.size)
        assertEquals(1.579, series.single().perUnit.amount)
        // Preguntada en SU sitio, no en el del usuario.
        assertTrue(
            harness.source.queries.any {
                haversineMeters(it.latitude, it.longitude, FAR_LAT, FAR_LON) < 100
            },
            "nadie preguntó por la gasolinera seguida: ${harness.source.queries}",
        )
    }

    /** Lo que ya funcionaba sigue funcionando, y **sin pagar una consulta extra**: una seguida que
     *  cae en la respuesta del usuario se mide con ella. */
    @Test
    fun should_notAskTwice_when_theFollowedStationIsInsideTheUsersAnswer() = runTest {
        val harness = Harness(world = listOf(station("near", USER_LAT, USER_LON, 1.699)))
        harness.watchRepository.follow(followed("near", USER_LAT, USER_LON))

        val looked = assertIs<FuelWatchOutcome.Looked>(harness.useCase())

        assertEquals(1, looked.samplesRecorded)
        assertEquals(1, harness.source.queries.size)
    }

    /** Dos seguidas en el mismo sitio comparten la respuesta: N seguidas en una provincia cuestan
     *  UNA consulta, porque cada respuesta se reutiliza para las que queden. */
    @Test
    fun should_reuseOneLookup_when_twoFollowedStationsShareIt() = runTest {
        val harness = Harness(
            world = listOf(
                station("far-a", FAR_LAT, FAR_LON, 1.579),
                station("far-b", FAR_LAT + 0.002, FAR_LON, 1.589),
            ),
        )
        harness.watchRepository.follow(followed("far-a", FAR_LAT, FAR_LON))
        harness.watchRepository.follow(followed("far-b", FAR_LAT + 0.002, FAR_LON))

        val looked = assertIs<FuelWatchOutcome.Looked>(harness.useCase())

        assertEquals(2, looked.samplesRecorded)
        // La del usuario + UNA por las dos seguidas.
        assertEquals(2, harness.source.queries.size)
    }

    /** El techo de consultas por tick existe para que seguir gasolineras no cueste datos sin
     *  límite: cada sitio nuevo baja el feed de una provincia. */
    @Test
    fun should_stopAtTheLookupBudget_when_manyFollowedStationsSitApart() = runTest {
        // Una por "provincia": separadas lo bastante para que ninguna respuesta cubra a otra.
        val apart = (0 until LOOKUP_BUDGET + 3).map { i ->
            station("apart-$i", FAR_LAT + i * 0.5, FAR_LON, 1.500 + i * 0.01)
        }
        val harness = Harness(world = apart)
        apart.forEach { harness.watchRepository.follow(followed(it.id, it.latitude, it.longitude)) }

        val looked = assertIs<FuelWatchOutcome.Looked>(harness.useCase())

        assertEquals(LOOKUP_BUDGET, looked.samplesRecorded)
        assertEquals(LOOKUP_BUDGET + 1, harness.source.queries.size)
    }

    /**
     * Un feed que no contesta por una seguida deja un HUECO, no una muestra inventada ni una
     * ejecución muerta: las demás se miden igual. Un hueco solo retrasa la comparación — el
     * veredicto compara las dos últimas lecturas, no exige que sean de días consecutivos.
     */
    @Test
    fun should_keepSamplingTheRest_when_theLookupForOneFollowedStationFails() = runTest {
        val harness = Harness(
            world = listOf(
                station("near", USER_LAT, USER_LON, 1.699),
                station("far", FAR_LAT, FAR_LON, 1.579),
            ),
        )
        harness.source.failAt = FAR_LAT to FAR_LON
        harness.watchRepository.follow(followed("near", USER_LAT, USER_LON))
        harness.watchRepository.follow(followed("far", FAR_LAT, FAR_LON))

        val looked = assertIs<FuelWatchOutcome.Looked>(harness.useCase())

        assertEquals(1, looked.samplesRecorded)
        assertTrue(
            harness.watchRepository.historyFor(
                fuelStationKey(SOURCE, "far"), FuelType.DIESEL, 30,
            ).isEmpty(),
        )
    }

    // ── El reparto nuevo: tu alrededor es free, tus seguidas son Plus ────────
    // [FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001]

    /**
     * ⛔ **Un free SÍ recibe el aviso de su alrededor.** Hasta el 13-10 esto era
     * `if (!isPlus) return Skipped("not-plus")` y un free no se enteraba ni de que la gasolinera de
     * su calle estaba 10 c/l por debajo de lo normal por aquí. La referencia es la MEDIANA de la
     * zona — la propia («donde sueles repostar») es la mitad que se paga.
     */
    @Test
    fun should_tellAFreeDriverAboutACheaperPumpNearby_when_theyFollowNothing() = runTest {
        val harness = Harness(world = AROUND_THE_USER, plus = false)

        val looked = assertIs<FuelWatchOutcome.Looked>(harness.useCase())

        val cheaper = assertNotNull(looked.cheaper, "el free se queda sin su aviso")
        assertEquals("cheap", cheaper.station.id)
        assertEquals(CheaperReference.AREA_MEDIAN, cheaper.reference)
        // Y su alrededor queda MEDIDO, que es lo que permite el aviso de mañana.
        assertEquals(AROUND_THE_USER.size, looked.samplesRecorded)
        // Hoy no hay movimiento que contar: una sola lectura no es una serie.
        assertEquals(emptyList(), looked.nearbyMoves)
        assertEquals(emptyList(), looked.followedMoves)
    }

    /**
     * Y el carril de las seguidas está APAGADO sin Plus: ni se pregunta por ellas. Se mide en las
     * consultas, no solo en el resultado — un carril que consulta y luego calla habria gastado los
     * datos del usuario para nada.
     */
    @Test
    fun should_notWatchTheFollowedLane_when_theDriverHasNoPlus() = runTest {
        val harness = Harness(
            world = AROUND_THE_USER + station("far", FAR_LAT, FAR_LON, 1.579),
            plus = false,
        )
        harness.watchRepository.follow(followed("far", FAR_LAT, FAR_LON))

        val looked = assertIs<FuelWatchOutcome.Looked>(harness.useCase())

        assertEquals(emptyList(), looked.followedMoves)
        assertEquals(1, harness.source.queries.size, "preguntó por una seguida sin Plus")
        // Y la seguida no tiene ni una lectura: su carril no corrió.
        assertTrue(
            harness.watchRepository.historyFor(
                fuelStationKey(SOURCE, "far"), FuelType.DIESEL, 30,
            ).isEmpty(),
        )
        // Lo medido es su alrededor, que es el carril que sí le toca.
        assertEquals(AROUND_THE_USER.size, looked.samplesRecorded)
        // ⛔ PAUSA, no olvida: la seguida sigue ahí para cuando vuelva Plus. [MONEY-001]
        assertEquals(1, harness.watchRepository.getFollowed().size)
    }

    /**
     * ⛔ **El aviso que el user pidió primero**: una de tu alrededor bajó respecto a ayer, sin
     * seguir nada y sin Plus. [FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001]
     *
     * Esto no se podía ni escribir antes de la fase B: la serie solo guardaba seguidas, así que una
     * gasolinera sin estrella no tenía ayer con el que compararse.
     */
    @Test
    fun should_reportANearbyMove_when_yesterdayWasMeasuredAndThePriceFell() = runTest {
        val harness = Harness(world = AROUND_THE_USER, plus = false)
        // Ayer la barata costaba 10 c/l más, con otro sello del feed.
        harness.watchRepository.recordSamples(
            listOf(
                FuelPriceSample(
                    stationKey = fuelStationKey(SOURCE, "cheap"),
                    fuelType = FuelType.DIESEL,
                    perUnit = Money(amount = 1.700, currency = "EUR"),
                    reportedAtMs = NOW_MS - 90_000_000,
                    observedAtMs = NOW_MS - 86_400_000,
                    observedDay = localDayOf(NOW_MS) - 1,
                ),
            ),
        )

        val looked = assertIs<FuelWatchOutcome.Looked>(harness.useCase())

        val move = looked.nearbyMoves.singleOrNull()
        assertNotNull(move, "el free se queda sin el aviso de su alrededor: ${looked.nearbyMoves}")
        assertEquals(fuelStationKey(SOURCE, "cheap"), move.station.stationKey)
        assertEquals(PriceMoveDirection.DOWN, move.direction)
        // Y no se cuela en el carril de pago.
        assertEquals(emptyList(), looked.followedMoves)
    }

    /**
     * ⛔ Una gasolinera SEGUIDA que además te rodea sale **solo** en su carril: si saliera en los
     * dos, el mismo surtidor produciría dos tarjetas que se contradicen en el sujeto —«la que
     * sigues» y «una cerca de ti»—. [FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001]
     */
    @Test
    fun should_keepAFollowedStationOutOfTheNearbyLane_when_itAlsoSitsAroundYou() = runTest {
        val harness = Harness(world = AROUND_THE_USER)
        harness.watchRepository.follow(followed("cheap", USER_LAT + 0.008, USER_LON))
        harness.watchRepository.recordSamples(
            listOf(
                FuelPriceSample(
                    stationKey = fuelStationKey(SOURCE, "cheap"),
                    fuelType = FuelType.DIESEL,
                    perUnit = Money(amount = 1.700, currency = "EUR"),
                    reportedAtMs = NOW_MS - 90_000_000,
                    observedAtMs = NOW_MS - 86_400_000,
                    observedDay = localDayOf(NOW_MS) - 1,
                ),
            ),
        )

        val looked = assertIs<FuelWatchOutcome.Looked>(harness.useCase())

        assertEquals(emptyList(), looked.nearbyMoves, "la seguida se colo en el carril free")
        assertEquals(
            listOf(fuelStationKey(SOURCE, "cheap")),
            looked.followedMoves.map { it.station.stationKey },
        )
    }

    /**
     * El techo de escritura del carril free: la tabla de la serie se abrió **a medias** y a
     * propósito. [FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001]
     */
    @Test
    fun should_measureAtMostTheCap_when_manyStationsSurroundYou() = runTest {
        val crowd = (0 until NEARBY_CAP + 4).map { i ->
            station("around-$i", USER_LAT + i * 0.002, USER_LON, 1.600 + i * 0.001)
        }
        val harness = Harness(world = crowd, plus = false)

        val looked = assertIs<FuelWatchOutcome.Looked>(harness.useCase())

        assertEquals(NEARBY_CAP, looked.samplesRecorded)
    }

    /**
     * ⛔ **El freno del carril free**: solo avisa a quien ha preguntado por gasolineras alguna vez.
     * Sin él la app notifica precios a quien nunca los ha mirado — y baja un feed de provincia al
     * dia para todo el mundo.
     */
    @Test
    fun should_skip_when_nobodyEverAskedAboutFuel() = runTest {
        val harness = Harness(world = AROUND_THE_USER, plus = false, askedAboutFuel = false)

        assertEquals(
            FuelWatchOutcome.Skipped("never-asked-about-fuel"),
            harness.useCase(),
        )
        assertEquals(0, harness.source.queries.size)
    }

    /**
     * Pero el freno es del carril FREE, no de la puerta: haber estrellado una gasolinera es una
     * intención más fuerte que haber abierto una pestaña, así que el carril Plus corre igual.
     */
    @Test
    fun should_stillWatchTheFollowedLane_when_aPlusDriverCarriesNoFuelStamp() = runTest {
        val harness = Harness(
            world = listOf(station("far", FAR_LAT, FAR_LON, 1.579)),
            askedAboutFuel = false,
        )
        harness.watchRepository.follow(followed("far", FAR_LAT, FAR_LON))

        val looked = assertIs<FuelWatchOutcome.Looked>(harness.useCase())

        assertEquals(1, looked.samplesRecorded)
    }

    private companion object {
        const val SOURCE = "minetur"
        const val REPORTED_AT = "13/10/2026 08:00:00"
        const val NOW_MS = 1_760_000_000_000L

        /**
         * `RunFuelPriceWatchUseCase.MAX_LOOKUPS_PER_RUN`, escrito aquí a mano **a propósito**: la
         * constante es privada de su clase (regla de los magic numbers) y leerla desde el test
         * convertiría «el techo es 8» en una tautología. Escrita, mover el techo obliga a tocar este
         * test, que es justo lo que debe pasar: es una decisión de coste de datos, no un detalle.
         */
        const val LOOKUP_BUDGET = 8

        /** `RunFuelPriceWatchUseCase.NEARBY_SAMPLED_MAX`, a mano por la misma razón que el de
         *  arriba: la constante es privada y moverla tiene que costar tocar este test. */
        const val NEARBY_CAP = 10

        // Sol, y una seguida a ~55 km — fuera del radio de 5 km de la respuesta del usuario.
        const val USER_LAT = 40.4168
        const val USER_LON = -3.7038
        const val FAR_LAT = 40.9168
        const val FAR_LON = -3.7038
    }
}
