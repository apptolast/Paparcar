@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.domain.usecase.stats

import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.stats.HeatMeasure
import com.rndeveloper.paparcar.domain.stats.ParkingCluster
import com.rndeveloper.paparcar.domain.stats.PeriodStats
import com.rndeveloper.paparcar.domain.stats.ScopedMeasure
import com.rndeveloper.paparcar.domain.stats.buildParkingHeatmap
import com.rndeveloper.paparcar.domain.stats.insights.InsightKind
import com.rndeveloper.paparcar.domain.stats.insights.MAX_INSIGHTS
import com.rndeveloper.paparcar.domain.stats.insights.costliestPlace
import com.rndeveloper.paparcar.domain.stats.insights.easiestTimeToPark
import com.rndeveloper.paparcar.domain.stats.insights.placeTrend
import com.rndeveloper.paparcar.domain.stats.insights.searchTimeTrend
import com.rndeveloper.paparcar.domain.stats.insights.worstTimeToPark
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The verdict of the insight engine, and the four rules under it.
 * [STATS-AN-INSIGHT-IS-A-VERDICT-WITH-ITS-EVIDENCE-001]
 */
class DeriveStatsInsightsUseCaseTest {

    private val utc = TimeZone.UTC
    private val derive = DeriveStatsInsightsUseCase()

    /** 2026-09-15 is a Tuesday (row 1); 2026-09-19 a Saturday (row 5). */
    private fun park(day: Int, hour: Int, searchTimeMs: Long?, id: String = "$day-$hour-$searchTimeMs") =
        UserParking(
            id = id,
            location = GpsPoint(
                40.4, -3.7, 8f,
                LocalDateTime(2026, 9, day, hour, 0).toInstant(utc).toEpochMilliseconds(),
                0f,
            ),
            isActive = false,
            address = AddressInfo(street = "Calle A", city = null, region = null, country = null),
            searchTimeMs = searchTimeMs,
        )

    private fun place(
        id: String,
        name: String?,
        avgMs: Long,
        visits: Int,
        lat: Double = 40.4,
    ) = ParkingCluster(
        id = id,
        name = name,
        latitude = lat,
        longitude = -3.7,
        parkings = visits,
        avgStayMs = null,
        searchTime = ScopedMeasure(avgMs * visits, fromParkings = visits, ofParkings = visits),
        spotsReleased = 0,
        lastParkedAtMs = 0L,
    )

    private fun stats(
        places: List<ParkingCluster> = emptyList(),
        searchTime: ScopedMeasure<Long>? = null,
        previousSearchTime: ScopedMeasure<Long>? = null,
    ) = PeriodStats(
        parkings = 10,
        endedParkings = 10,
        distance = null,
        fuelLiters = null,
        energyKwh = null,
        costEstimate = null,
        co2Kg = null,
        avgParkedDurationMs = null,
        peakHourBucket = null,
        mostActiveDayOfWeek = null,
        places = places,
        spotsReleased = 0,
        autoDetectedCount = 0,
        detectionKnownCount = 0,
        searchTime = searchTime,
        previous = previousSearchTime?.let {
            PeriodStats(
                parkings = 10, endedParkings = 10, distance = null, fuelLiters = null,
                energyKwh = null, costEstimate = null, co2Kg = null, avgParkedDurationMs = null,
                peakHourBucket = null, mostActiveDayOfWeek = null, places = emptyList(),
                spotsReleased = 0, autoDetectedCount = 0, detectionKnownCount = 0, searchTime = it,
            )
        },
    )

    // ── The two clock rules ──────────────────────────────────────────────────

    /**
     * ⛔ Every grid HAS a maximum, including one where every slot costs the same. The maximum is
     * only a finding when it stands clearly above the period's own average — otherwise the app
     * would interrupt somebody to tell them that one of their Tuesdays was, technically, first.
     */
    @Test
    fun should_claimNoWorstSlot_when_everySlotCostsTheSame() {
        val flat = buildParkingHeatmap(
            listOf(
                park(15, 9, 300_000L), park(22, 9, 300_000L),
                park(19, 18, 300_000L), park(26, 18, 300_000L),
            ),
            HeatMeasure.SEARCH_TIME, utc,
        )
        assertNull(worstTimeToPark(flat))
    }

    @Test
    fun should_nameTheWorstSlot_when_itStandsAboveTheRest() {
        val grid = buildParkingHeatmap(
            listOf(
                park(15, 18, 900_000L), park(22, 18, 900_000L),   // Tuesdays 16-20: 15 min
                park(19, 9, 120_000L), park(26, 9, 120_000L),     // Saturdays 08-12: 2 min
            ),
            HeatMeasure.SEARCH_TIME, utc,
        )
        val worst = worstTimeToPark(grid)!!
        assertEquals(InsightKind.WORST_TIME_TO_PARK, worst.kind)
        assertEquals(1, worst.values.dayIndex, "martes")
        assertEquals(16, worst.values.bandStartHour)
        assertEquals(2, worst.samples, "y dice sobre cuántos aparcamientos lo dice")
    }

    /** "You park worst AND best on Tuesday at six" is true and useless. */
    @Test
    fun should_notOfferTheSameSlot_asBothWorstAndBest() {
        val grid = buildParkingHeatmap(
            listOf(park(15, 18, 900_000L), park(22, 18, 900_000L)),
            HeatMeasure.SEARCH_TIME, utc,
        )
        val worst = worstTimeToPark(grid)
        assertNull(easiestTimeToPark(grid, worst))
    }

    /**
     * The baseline is weighted by visits: a slot used twice must not define "what usually happens"
     * as loudly as one used twelve times.
     */
    @Test
    fun should_weighTheBaseline_byHowOftenEachSlotIsUsed() {
        // Seis visitas baratas en la MISMA celda (martes 08-12, dos martes x tres horas) contra dos
        // caras en otra (sábado 20-24).
        val sessions = listOf(15, 22).flatMap { day ->
            listOf(8, 9, 10).map { hour -> park(day, hour, 120_000L, id = "cheap-$day-$hour") }
        } + listOf(park(19, 22, 600_000L), park(26, 22, 600_000L))
        val grid = buildParkingHeatmap(sessions, HeatMeasure.SEARCH_TIME, utc)
        val worst = worstTimeToPark(grid)!!
        // Unweighted, the average of the two cell means would be 6 min and the 10-min cell would
        // barely clear the bar; weighted by the six cheap visits it is 3 min, and the finding is real.
        assertTrue(worst.effect > 2.0, "el contraste real es contra lo que sueles hacer")
    }

    // ── Place ────────────────────────────────────────────────────────────────

    @Test
    fun should_needTwoPlaces_toCallOneCostly() {
        assertNull(costliestPlace(listOf(place("a", "Trabajo", 600_000L, visits = 4))))
    }

    @Test
    fun should_nameTheCostliestPlace_againstTheOthers() {
        val insight = costliestPlace(
            listOf(
                place("a", "Trabajo", 540_000L, visits = 4),
                place("b", "Casa", 120_000L, visits = 6),
            ),
        )!!
        assertEquals("Trabajo", insight.values.placeName)
        assertEquals(4, insight.samples)
        assertTrue(insight.effect > 4.0)
    }

    /** A place that cannot say where it is never speaks. */
    @Test
    fun should_staySilent_when_theCostliestPlaceHasNoName() {
        assertNull(
            costliestPlace(
                listOf(
                    place("a", null, 540_000L, visits = 4),
                    place("b", "Casa", 120_000L, visits = 6),
                ),
            ),
        )
    }

    // ── Trend ────────────────────────────────────────────────────────────────

    /**
     * ⛔ It inherits the comparability rule instead of writing a second one: between two partially
     * measured periods, a change measures how many trips got measured.
     * [STATS-A-MEASURE-CARRIES-ITS-COVERAGE-001]
     */
    @Test
    fun should_refuseTheTrend_when_thePeriodsAreNotComparable() {
        val now = ScopedMeasure(1_200_000L, fromParkings = 4, ofParkings = 8)
        val before = ScopedMeasure(300_000L, fromParkings = 2, ofParkings = 9)
        assertNull(searchTimeTrend(now, before, comparable = false))
        assertTrue(searchTimeTrend(now, before, comparable = true) != null)
    }

    /** Per trip, not per period: a month with twice the trips spends twice the time for free. */
    @Test
    fun should_compareTheCostPerTrip_notTheTotals() {
        val now = ScopedMeasure(1_200_000L, fromParkings = 8, ofParkings = 8)    // 2,5 min/viaje
        val before = ScopedMeasure(600_000L, fromParkings = 4, ofParkings = 4)   // 2,5 min/viaje
        assertNull(searchTimeTrend(now, before, comparable = true), "el doble de viajes no es peor")
    }

    // ── The verdict ──────────────────────────────────────────────────────────

    @Test
    fun should_returnNothing_when_thePeriodHasNoFigures() {
        assertEquals(emptyList(), derive(sessions = emptyList(), stats = null))
    }

    @Test
    fun should_rankByFindingSize_andCapTheList() {
        val sessions = listOf(
            park(15, 18, 1_200_000L), park(22, 18, 1_200_000L),
            park(19, 9, 60_000L), park(26, 9, 60_000L),
        )
        val insights = derive(
            sessions = sessions,
            stats = stats(
                places = listOf(
                    place("a", "Trabajo", 540_000L, visits = 4),
                    place("b", "Casa", 120_000L, visits = 6),
                ),
                searchTime = ScopedMeasure(2_520_000L, fromParkings = 4, ofParkings = 4),
                previousSearchTime = ScopedMeasure(240_000L, fromParkings = 4, ofParkings = 4),
            ),
        )
        assertTrue(insights.size <= MAX_INSIGHTS, "tres es lo que alguien lee")
        assertTrue(insights.zipWithNext().all { (a, b) -> a.score >= b.score }, "de mayor a menor")
        assertTrue(insights.all { it.samples > 0 }, "ninguna frase sin su n")
    }

    // ── Un sitio que empeora [STATS-A-PLACE-CAN-GET-WORSE-001] ──────────────

    /**
     * La vigilancia que promete Plus: el mismo SITIO comparado consigo mismo. Y se empareja por
     * GEOMETRÍA, no por nombre — el mismo trozo de acera se llama distinto según el geocoder, que es
     * justo por lo que un sitio dejó de ser una cadena de texto.
     */
    @Test
    fun should_noticeAPlaceGettingHarder_matchingItByGround() {
        val insight = placeTrend(
            places = listOf(place("now", "Calle de Alcalá", 600_000L, visits = 4)),
            previousPlaces = listOf(place("before", "Calle de O'Donnell", 120_000L, visits = 4)),
        )!!
        assertEquals(InsightKind.PLACE_TREND, insight.kind)
        assertEquals("Calle de Alcalá", insight.values.placeName)
        assertTrue(insight.values.isUp, "ahora cuesta más")
        assertEquals(480_000L, insight.values.deltaMs)
    }

    @Test
    fun should_alsoSayItWhenAPlaceGetsEasier() {
        val insight = placeTrend(
            places = listOf(place("now", "Trabajo", 120_000L, visits = 4)),
            previousPlaces = listOf(place("before", "Trabajo", 600_000L, visits = 4)),
        )!!
        assertTrue(!insight.values.isUp)
    }

    /** Sitios distintos no se comparan: a 3 km no hay «el mismo sitio» que comparar. */
    @Test
    fun should_notCompareTwoDifferentPlaces() {
        assertNull(
            placeTrend(
                places = listOf(place("now", "Trabajo", 600_000L, visits = 4, lat = 40.4)),
                previousPlaces = listOf(place("far", "Casa", 120_000L, visits = 4, lat = 40.43)),
            ),
        )
    }

    /** Una media de una sola búsqueda no es una media, a ninguno de los dos lados. */
    @Test
    fun should_staySilent_when_eitherSideHasASingleHunt() {
        assertNull(
            placeTrend(
                places = listOf(place("now", "Trabajo", 600_000L, visits = 1)),
                previousPlaces = listOf(place("before", "Trabajo", 120_000L, visits = 4)),
            ),
        )
    }

    /** Y un cambio pequeño es el ruido normal de aparcar, no un hallazgo. */
    @Test
    fun should_staySilent_when_theMoveIsOrdinary() {
        assertNull(
            placeTrend(
                places = listOf(place("now", "Trabajo", 130_000L, visits = 4)),
                previousPlaces = listOf(place("before", "Trabajo", 120_000L, visits = 4)),
            ),
        )
    }
}
