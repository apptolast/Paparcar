package com.rndeveloper.paparcar.domain.stats

import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.PlaceCategory
import com.rndeveloper.paparcar.domain.model.PlaceInfo
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.model.Zone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Where you park, as patches of ground instead of as geocoder strings.
 * [STATS-A-USUAL-PLACE-IS-A-CLUSTER-NOT-A-STREET-001]
 */
class ParkingClustersTest {

    private val base = 1_700_000_000_000L

    /** ~1.1 m per 0.00001° of latitude, which is what makes these fixtures readable. */
    private fun at(
        index: Int,
        lat: Double,
        lon: Double = -3.7,
        street: String? = null,
        poi: String? = null,
        endedAfterMs: Long? = 2 * 3_600_000L,
        searchTimeMs: Long? = null,
        publishedSpot: Boolean = false,
    ) = UserParking(
        id = "p$index",
        location = GpsPoint(lat, lon, 8f, base + index * 86_400_000L, 0f),
        isActive = endedAfterMs == null,
        endedAtMs = endedAfterMs?.let { base + index * 86_400_000L + it },
        address = street?.let { AddressInfo(street = it, city = null, region = null, country = null) },
        placeInfo = poi?.let { PlaceInfo(name = it, category = PlaceCategory.OTHER) },
        searchTimeMs = searchTimeMs,
        publishedSpot = publishedSpot,
    )

    /**
     * ⛔ THE failure this replaces: the same corner answered with two road names was two places.
     * Forty metres apart is one place whatever the geocoder called it.
     */
    @Test
    fun should_beOnePlace_when_theParkingsAreMetresApart_evenWithDifferentStreets() {
        val places = clusterParkings(
            listOf(
                at(1, 40.40000, street = "Calle de Alcalá"),
                at(2, 40.40018, street = "Calle de O'Donnell"),
                at(3, 40.40036, street = "Calle de Alcalá"),
            ),
        )
        assertEquals(1, places.size)
        assertEquals(3, places.first().parkings)
        assertEquals("Calle de Alcalá", places.first().name, "the majority street names it")
    }

    /** And the mirror failure: four kilometres of one avenue were a single place. */
    @Test
    fun should_beTwoPlaces_when_theParkingsAreKilometresApart_underOneStreetName() {
        val places = clusterParkings(
            listOf(
                at(1, 40.40000, street = "Gran Vía"),
                at(2, 40.40010, street = "Gran Vía"),
                at(3, 40.40020, street = "Gran Vía"),
                at(4, 40.43000, street = "Gran Vía"),
                at(5, 40.43010, street = "Gran Vía"),
                at(6, 40.43020, street = "Gran Vía"),
            ),
        )
        assertEquals(2, places.size, "3 km apart is not the same place")
        assertTrue(places.all { it.parkings == 3 })
    }

    /** The user already said what this place is called. */
    @Test
    fun should_takeItsNameFromTheUsersZone_aboveTheStreet() {
        val sessions = List(3) { at(it, 40.40000 + it * 0.00005, street = "Calle de Serrano") }
        val zone = Zone(
            id = "z1", userId = "u", name = "Trabajo", lat = 40.40005, lon = -3.7,
            iconKey = "WORK", createdAt = 0L, radiusMeters = 250f, isPrivate = true,
        )
        val place = clusterParkings(sessions, listOf(zone)).single()
        assertEquals("Trabajo", place.name)
        assertEquals("z1", place.zoneId)
        assertTrue(place.isPrivateZone, "a garage never frees a spot, and the row says so")
    }

    @Test
    fun should_fallBackToThePoi_when_noStreetIsKnown() {
        val places = clusterParkings(List(3) { at(it, 40.40000 + it * 0.00005, poi = "Mercadona") })
        assertEquals("Mercadona", places.single().name)
    }

    @Test
    fun should_claimNoPlace_below_theRepeatThreshold() {
        val places = clusterParkings(
            listOf(at(1, 40.40000, street = "Calle A"), at(2, 40.40010, street = "Calle A")),
        )
        assertEquals(emptyList(), places, "two visits are not a habit")
        assertEquals(emptyList(), clusterParkings(emptyList()))
    }

    /**
     * The figure that makes the list worth reading: what parking HERE costs. It arrives with its
     * coverage, like every measured sum. [STATS-A-MEASURE-CARRIES-ITS-COVERAGE-001]
     */
    @Test
    fun should_priceEachPlace_inTimeAndInSpots() {
        val places = clusterParkings(
            listOf(
                at(1, 40.40000, street = "Calle A", searchTimeMs = 300_000L, publishedSpot = true),
                at(2, 40.40010, street = "Calle A", searchTimeMs = 180_000L),
                at(3, 40.40020, street = "Calle A"),
            ),
        )
        val place = places.single()
        assertEquals(480_000L, place.searchTime?.value)
        assertEquals(2, place.searchTime?.fromParkings)
        assertEquals(3, place.searchTime?.ofParkings, "and it says it only measured two of three")
        assertEquals(2 * 3_600_000L, place.avgStayMs)
        assertEquals(1, place.spotsReleased)
    }

    @Test
    fun should_orderPlaces_byHowOftenYouGoThere() {
        val places = clusterParkings(
            List(3) { at(it, 40.40000 + it * 0.00005, street = "Poco") } +
                List(5) { at(10 + it, 40.43000 + it * 0.00005, street = "Mucho") },
        )
        assertEquals(listOf("Mucho", "Poco"), places.map { it.name })
    }

    /** An unnamed place still exists and still counts — it just cannot be listed. */
    @Test
    fun should_keepAnUnnamedPlace_withoutAName() {
        val place = clusterParkings(List(3) { at(it, 40.40000 + it * 0.00005) }).single()
        assertNull(place.name)
        assertEquals(3, place.parkings)
    }

    // ── Calibración contra el corpus (15-09-2026) ───────────────────────────

    /**
     * ⛔ El caso que fija el SUELO del radio, medido sobre las 18 trazas de campo: el peor
     * esparcimiento de UN solo aparcamiento es de **94,7 m** (`HouseMirage001`, cuyos fixes pasean
     * por toda una casa). Si dos visitas al mismo sitio se muestrean en extremos opuestos de ese
     * paseo, un radio de 60 u 80 m las partiría en dos sitios — y el usuario vería su propia casa
     * dos veces en «Tus sitios».
     *
     * 95 m separan estos dos pines. Tienen que seguir siendo UN sitio.
     */
    @Test
    fun should_notSplitAPlace_when_twoVisitsLandAtOppositeEndsOfOneStopsScatter() {
        // 0,00085° de latitud ~ 95 m.
        val places = clusterParkings(
            listOf(
                at(1, 40.40000, street = "Calle A"),
                at(2, 40.40043, street = "Calle A"),
                at(3, 40.40085, street = "Calle A"),
            ),
        )
        assertEquals(1, places.size, "el peor esparcimiento medido de un solo stop no parte un sitio")
        assertEquals(3, places.single().parkings)
    }

    /**
     * Y el TECHO: en el corpus no hay un solo par de sitios distintos a menos de **205 m**, así que
     * el radio debe quedar holgadamente por debajo. A 205 m son dos sitios.
     */
    @Test
    fun should_keepTwoPlacesApart_at_theNearestMeasuredSeparation() {
        // 0,00184° ~ 205 m.
        val places = clusterParkings(
            List(3) { at(it, 40.40000 + it * 0.00002, street = "Calle A") } +
                List(3) { at(10 + it, 40.40184 + it * 0.00002, street = "Calle B") },
        )
        assertEquals(2, places.size, "205 m es la separación real más corta del corpus: dos sitios")
    }
}
