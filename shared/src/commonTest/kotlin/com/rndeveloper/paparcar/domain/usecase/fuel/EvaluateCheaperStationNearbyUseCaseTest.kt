package com.rndeveloper.paparcar.domain.usecase.fuel

import com.rndeveloper.paparcar.domain.fuel.FuelStation
import com.rndeveloper.paparcar.domain.fuel.StationPrice
import com.rndeveloper.paparcar.domain.model.FuelType
import com.rndeveloper.paparcar.domain.model.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001] */
class EvaluateCheaperStationNearbyUseCaseTest {

    private val useCase = EvaluateCheaperStationNearbyUseCase()

    /**
     * Two seconds past the epoch, so the default stamp of 1_000L below is ONE SECOND old and every
     * pre-existing case keeps meaning exactly what it meant before the freshness floor arrived.
     * Ages in the new cases are written as offsets from here.
     * [FUEL-A-CHEAP-PRICE-WITH-NO-FLOOR-IS-THE-STALEST-ONE-001]
     */
    private val NOW = 2_000L
    private val HOUR = 60L * 60 * 1_000

    // ~40.4168 N, -3.7038 W (Madrid). 0,001° of latitude ≈ 111 m.
    private val hereLat = 40.4168
    private val hereLon = -3.7038

    private fun station(
        id: String,
        price: Double?,
        latOffset: Double = 0.0,
        reportedAtMs: Long? = 1_000L,
        name: String = "Station $id",
        currency: String = "EUR",
    ) = FuelStation(
        id = id,
        name = name,
        address = null,
        town = null,
        latitude = hereLat + latOffset,
        longitude = hereLon,
        prices = price?.let {
            mapOf(FuelType.DIESEL to StationPrice(Money(it, currency), reportedAtMs))
        }.orEmpty(),
    )

    private fun candidates(vararg stations: FuelStation): Map<String, FuelStation> =
        stations.associateBy { "minetur|${it.id}" }

    @Test
    fun should_measureAgainstTheFollowedStation_when_thereIsOne() {
        val verdict = useCase(
            latitude = hereLat,
            longitude = hereLon,
            fuelType = FuelType.DIESEL,
            candidates = candidates(
                station("mine", price = 1.699, name = "Mi gasolinera"),
                station("cheap", price = 1.629, latOffset = 0.005),
            ),
            referenceStationKeys = setOf("minetur|mine"),
            nowMs = NOW,
        )

        assertEquals("cheap", verdict?.station?.id)
        assertEquals(CheaperReference.FOLLOWED, verdict?.reference)
        assertEquals("Mi gasolinera", verdict?.referenceName)
        assertEquals(0.070, verdict?.savingPerUnit ?: 0.0, absoluteTolerance = 1e-9)
    }

    /**
     * ⛔ The reference with nothing followed is the MEDIAN, never the dearest pump around — that
     * version fires every single day, because next to the worst forecourt in town everything is a
     * bargain.
     */
    @Test
    fun should_measureAgainstTheAreaMedian_when_nothingIsFollowed() {
        val verdict = useCase(
            latitude = hereLat,
            longitude = hereLon,
            fuelType = FuelType.DIESEL,
            candidates = candidates(
                station("a", price = 1.900),
                station("b", price = 1.700),
                station("c", price = 1.660),
            ),
            referenceStationKeys = emptySet(),
            nowMs = NOW,
        )

        assertEquals(CheaperReference.AREA_MEDIAN, verdict?.reference)
        // Median of {1.660, 1.700, 1.900} = 1.700, so the saving is measured from there — not from
        // the 1.900 that would have made this a 24-cent "bargain".
        assertEquals(1.700, verdict?.referencePrice?.amount ?: 0.0, absoluteTolerance = 1e-9)
        assertEquals(0.040, verdict?.savingPerUnit ?: 0.0, absoluteTolerance = 1e-9)
    }

    @Test
    fun should_sayNothing_when_theSavingIsBelowTheThreshold() {
        val verdict = useCase(
            latitude = hereLat,
            longitude = hereLon,
            fuelType = FuelType.DIESEL,
            candidates = candidates(
                station("mine", price = 1.659),
                station("cheap", price = 1.649, latOffset = 0.005),
            ),
            referenceStationKeys = setOf("minetur|mine"),
            nowMs = NOW,
        )

        assertNull(verdict, "1 c/l does not pay for the detour it would ask for")
    }

    @Test
    fun should_sayNothing_when_theOnlyCheaperPumpIsBeyondTheDetour() {
        val verdict = useCase(
            latitude = hereLat,
            longitude = hereLon,
            fuelType = FuelType.DIESEL,
            candidates = candidates(
                station("mine", price = 1.799),
                // ~5,5 km north.
                station("far", price = 1.599, latOffset = 0.05),
            ),
            referenceStationKeys = setOf("minetur|mine"),
            nowMs = NOW,
        )

        assertNull(verdict)
    }

    /** A candidate quoted from an older reading than the reference proves nothing. */
    @Test
    fun should_sayNothing_when_theCandidatePriceIsStalerThanTheReference() {
        val verdict = useCase(
            latitude = hereLat,
            longitude = hereLon,
            fuelType = FuelType.DIESEL,
            candidates = candidates(
                station("mine", price = 1.799, reportedAtMs = 5_000L),
                station("stale", price = 1.599, latOffset = 0.005, reportedAtMs = 1_000L),
            ),
            referenceStationKeys = setOf("minetur|mine"),
            nowMs = NOW,
        )

        assertNull(verdict)
    }

    @Test
    fun should_sayNothing_when_nothingAroundHasAPriceForThisFuel() {
        val verdict = useCase(
            latitude = hereLat,
            longitude = hereLon,
            fuelType = FuelType.DIESEL,
            candidates = candidates(station("a", price = null), station("b", price = null)),
            referenceStationKeys = emptySet(),
            nowMs = NOW,
        )

        assertNull(verdict)
    }

    @Test
    fun should_neverProposeAFollowedStation_when_itIsItselfTheCheapest() {
        val verdict = useCase(
            latitude = hereLat,
            longitude = hereLon,
            fuelType = FuelType.DIESEL,
            candidates = candidates(
                station("mine", price = 1.599),
                station("other", price = 1.799, latOffset = 0.005),
            ),
            referenceStationKeys = setOf("minetur|mine"),
            nowMs = NOW,
        )

        assertNull(verdict, "the cheapest pump around is already the one you use")
    }

    /** A border area can answer in two currencies, and the smaller number is not the lower price. */
    @Test
    fun should_compareWithinOneCurrency_when_theAnswerMixesTwo() {
        val verdict = useCase(
            latitude = hereLat,
            longitude = hereLon,
            fuelType = FuelType.DIESEL,
            candidates = candidates(
                station("eur1", price = 1.899, currency = "EUR"),
                station("eur2", price = 1.800, currency = "EUR"),
                station("eur3", price = 1.750, currency = "EUR", latOffset = 0.005),
                // Would be "cheapest" by raw number, and is a different money entirely.
                station("pln", price = 0.400, currency = "PLN", latOffset = 0.005),
            ),
            referenceStationKeys = emptySet(),
            nowMs = NOW,
        )

        assertTrue(verdict != null && verdict.price.currency == "EUR")
        assertEquals("eur3", verdict.station.id)
    }

    // ── El suelo de frescura [FUEL-A-CHEAP-PRICE-WITH-NO-FLOOR-IS-THE-STALEST-ONE-001] ──────────

    /**
     * El caso de Roma, con sus cifras reales (16-09-2026, radio 5 km, nada seguido).
     *
     * STAR OIL a 1,669 € era la más barata del barrio y llevaba **158 h** congelada; la siguiente,
     * 1,709 €, tenía 5 h. Sin suelo la alerta mandaba a conducir 1,9 km por 0,455 €/L — 18 € de
     * depósito — a pagar un precio de hace casi una semana.
     */
    @Test
    fun should_skipTheCheapest_when_itsPriceIsOlderThanTheFloor_and_nothingIsFollowed() {
        val verdict = useCase(
            latitude = hereLat,
            longitude = hereLon,
            fuelType = FuelType.DIESEL,
            // Cinco filas, no tres: con tres la mediana del área ERA el propio candidato y el
            // ahorro salía cero — el test fallaba por su montaje, no por el suelo. Estas cinco
            // dejan la mediana en 2,124 €, que es la que midió Roma.
            candidates = candidates(
                station("stale", price = 1.669, latOffset = 0.005, reportedAtMs = NOW - 158 * HOUR),
                station("fresh", price = 1.709, latOffset = 0.004, reportedAtMs = NOW - 5 * HOUR),
                station("median", price = 2.124, latOffset = 0.003, reportedAtMs = NOW - 18 * HOUR),
                station("dear1", price = 2.200, latOffset = 0.003, reportedAtMs = NOW - 18 * HOUR),
                station("dear2", price = 2.300, latOffset = 0.003, reportedAtMs = NOW - 18 * HOUR),
            ),
            referenceStationKeys = emptySet(),
            nowMs = NOW,
        )

        assertEquals("fresh", verdict?.station?.id, "la rancia no se nombra, por barata que sea")
        assertEquals(0.415, verdict?.savingPerUnit ?: 0.0, absoluteTolerance = 1e-9)
    }

    /** ⛔ Y el suelo no puede dejar muda a Alemania, cuyo feed no sella NINGUNA hora. */
    @Test
    fun should_stillNameACandidate_when_theSourcePublishesNoHourAtAll() {
        val verdict = useCase(
            latitude = hereLat,
            longitude = hereLon,
            fuelType = FuelType.DIESEL,
            candidates = candidates(
                station("de1", price = 1.629, latOffset = 0.005, reportedAtMs = null),
                station("de2", price = 1.729, latOffset = 0.004, reportedAtMs = null),
                station("de3", price = 1.799, latOffset = 0.003, reportedAtMs = null),
            ),
            referenceStationKeys = emptySet(),
            nowMs = NOW,
        )

        assertEquals("de1", verdict?.station?.id, "sin sello no es lo mismo que rancio")
    }

    /**
     * ⛔ El suelo mide al CANDIDATO, nunca a la referencia, y la asimetría es el diseño: una
     * candidata rancia hace que la app diga «conduce y paga 1,669 €», que es falso al llegar; una
     * referencia rancia sólo debilita el «más barata que la tuya», mientras la cifra sobre la que
     * se actúa sigue siendo cierta.
     */
    @Test
    fun should_stillFire_when_theFollowedReferenceIsStaleButTheCandidateIsFresh() {
        val verdict = useCase(
            latitude = hereLat,
            longitude = hereLon,
            fuelType = FuelType.DIESEL,
            candidates = candidates(
                station("mine", price = 2.124, reportedAtMs = NOW - 200 * HOUR, name = "La mía"),
                station("fresh", price = 1.999, latOffset = 0.005, reportedAtMs = NOW - 2 * HOUR),
            ),
            referenceStationKeys = setOf("minetur|mine"),
            nowMs = NOW,
        )

        assertEquals("fresh", verdict?.station?.id)
        assertEquals(CheaperReference.FOLLOWED, verdict?.reference)
    }

    /** Nada que nombrar: si TODO lo barato está rancio, la alerta calla en vez de inventar. */
    @Test
    fun should_stayQuiet_when_everyCheaperCandidateIsStale() {
        val verdict = useCase(
            latitude = hereLat,
            longitude = hereLon,
            fuelType = FuelType.DIESEL,
            candidates = candidates(
                station("s1", price = 1.669, latOffset = 0.005, reportedAtMs = NOW - 158 * HOUR),
                station("s2", price = 1.799, latOffset = 0.004, reportedAtMs = NOW - 1146 * HOUR),
                station("here", price = 2.124, latOffset = 0.003, reportedAtMs = NOW - 18 * HOUR),
            ),
            referenceStationKeys = emptySet(),
            nowMs = NOW,
        )

        assertNull(verdict, "callar es la respuesta honesta cuando sólo lo viejo es barato")
    }

    /**
     * La frontera exacta, para que 48 h no se vuelva 47 ni 49 sin que nadie lo note.
     *
     * Con un segundo candidato fresco detrás, cruzar el umbral **cambia a quién nombra la alerta**
     * en vez de callarla — así el test distingue el suelo de un "no hay ahorro suficiente".
     */
    @Test
    fun should_acceptAPriceExactlyAtTheFloor_and_rejectTheOneJustPastIt() {
        fun verdictAt(ageHours: Long) = useCase(
            latitude = hereLat,
            longitude = hereLon,
            fuelType = FuelType.DIESEL,
            candidates = candidates(
                station("edge", price = 1.629, latOffset = 0.005, reportedAtMs = NOW - ageHours * HOUR),
                station("runnerUp", price = 1.899, latOffset = 0.004, reportedAtMs = NOW - HOUR),
                station("median", price = 2.100, latOffset = 0.003, reportedAtMs = NOW),
                station("dear1", price = 2.124, latOffset = 0.003, reportedAtMs = NOW),
                station("dear2", price = 2.200, latOffset = 0.003, reportedAtMs = NOW),
            ),
            referenceStationKeys = emptySet(),
            nowMs = NOW,
        )

        assertEquals("edge", verdictAt(48)?.station?.id, "48 h justas entran")
        assertEquals("runnerUp", verdictAt(49)?.station?.id, "49 h ya no, y la alerta pasa a la siguiente")
    }
}
