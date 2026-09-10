package com.rndeveloper.paparcar.data.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The geometry of *having asked*. [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001]
 *
 * ⛔ The invariant under test: **a saved answer may only speak for ground it actually saw.** Every
 * case here is a way of offering the cache an area it did not cover and checking that it declines,
 * because the failure this prevents is silent — a partial list served as a complete one reads as
 * "there is nothing here", which is a lie a driver acts on.
 */
class OsmCoverageTest {

    private val madridLat = 40.4168
    private val madridLon = -3.7038

    /** Metres to degrees of latitude, for placing a point a known distance north. */
    private fun northOf(latitude: Double, meters: Double) = latitude + meters / 111_320.0

    @Test
    fun `should_answer_when_theDiscContainsTheQuery`() {
        val disc = CoverageDisc(madridLat, madridLon, radiusMeters = 1_500, fetchedAt = 0)

        assertTrue(disc.covers(madridLat, madridLon, queryRadiusMeters = 1_500))
    }

    /**
     * ⛔ The one that keeps the cache honest: **overlap is not coverage.**
     *
     * Two 1.500 m discs 1.400 m apart overlap generously, and a cache that accepted overlap would
     * answer the second question from the first disc — serving the slice it happens to hold as if it
     * were the whole area. Most of that second disc was never asked about.
     */
    @Test
    fun `should_decline_when_theDiscsMerelyOverlap`() {
        val disc = CoverageDisc(madridLat, madridLon, radiusMeters = 1_500, fetchedAt = 0)

        val overlapping = disc.covers(
            queryLatitude = northOf(madridLat, 1_400.0),
            queryLongitude = madridLon,
            queryRadiusMeters = 1_500,
        )

        assertFalse(overlapping)
    }

    /** A wider disc genuinely contains a nearby narrower question, and answers it. */
    @Test
    fun `should_answer_when_aWiderDiscSwallowsTheQuery`() {
        val disc = CoverageDisc(madridLat, madridLon, radiusMeters = 2_000, fetchedAt = 0)

        assertTrue(
            disc.covers(northOf(madridLat, 200.0), madridLon, queryRadiusMeters = 1_500)
        )
    }

    /**
     * The same question asked twice from a hand's width apart is ONE question.
     *
     * GPS wanders by tens of metres while standing still; filing every reading as a new disc would
     * grow the coverage table without ever hitting it.
     */
    @Test
    fun `should_shareOneKey_when_twoReadingsFallInTheSameCell`() {
        val first = coverageKey(CoverageLayer.PUBLIC_PARKINGS, 40.41681, -3.70382, 1_500)
        val second = coverageKey(CoverageLayer.PUBLIC_PARKINGS, 40.41684, -3.70379, 1_500)

        assertEquals(first, second)
    }

    /** ⛔ Radius and layer are part of the identity: a 1.500 m answer must never serve a 5 km ask. */
    @Test
    fun `should_keepSeparateKeys_when_radiusOrLayerDiffer`() {
        val parkings = coverageKey(CoverageLayer.PUBLIC_PARKINGS, madridLat, madridLon, 1_500)
        val wider = coverageKey(CoverageLayer.PUBLIC_PARKINGS, madridLat, madridLon, 5_000)
        val fuel = coverageKey(CoverageLayer.FUEL_STATIONS, madridLat, madridLon, 1_500)

        assertNotEquals(parkings, wider)
        assertNotEquals(parkings, fuel)
    }

    /** A response that fitted under the cap saw its whole disc, so it keeps the radius it asked. */
    @Test
    fun `should_keepTheRequestedRadius_when_theResponseFitUnderTheCap`() {
        val radius = effectiveRadiusMeters(
            requestedRadiusMeters = 1_500,
            centreLatitude = madridLat,
            centreLongitude = madridLon,
            resultCoordinates = listOf(northOf(madridLat, 300.0) to madridLon),
            isTruncated = false,
        )

        assertEquals(1_500, radius)
    }

    /**
     * ⛔ A truncated response may not claim the radius it asked for.
     *
     * At the cap, Overpass stopped counting: beyond the farthest element that came back the app saw
     * nothing and knows nothing. Keeping the requested radius would let the disc promise a ring it
     * never looked at, and the next cold start would report that ring as a measured emptiness.
     */
    @Test
    fun `should_shrinkToTheFarthestResult_when_theResponseHitTheCap`() {
        val coordinates = List(80) { northOf(madridLat, 400.0) to madridLon }

        val radius = effectiveRadiusMeters(
            requestedRadiusMeters = 1_500,
            centreLatitude = madridLat,
            centreLongitude = madridLon,
            resultCoordinates = coordinates,
            isTruncated = true,
        )

        assertTrue(radius in 395..405, "expected about 400 m, was $radius")
    }

    /**
     * ⛔ A source that does not cap keeps the radius it asked for, however many rows it returned.
     *
     * Spain downloads a whole province and filters it on the phone, so a big answer is a COMPLETE
     * answer. Inferring truncation from a row count would shrink its coverage for being thorough —
     * which is why the flag is passed in by the source instead of guessed here.
     */
    @Test
    fun `should_keepTheRequestedRadius_when_theSourceDoesNotCapAtAll`() {
        val coordinates = List(300) { northOf(madridLat, 400.0) to madridLon }

        val radius = effectiveRadiusMeters(
            requestedRadiusMeters = 5_000,
            centreLatitude = madridLat,
            centreLongitude = madridLon,
            resultCoordinates = coordinates,
            isTruncated = false,
        )

        assertEquals(5_000, radius)
    }

    /** A truncated answer with nothing in it cannot shrink to zero: that would claim a point. */
    @Test
    fun `should_keepTheRequestedRadius_when_aTruncatedAnswerIsEmpty`() {
        val radius = effectiveRadiusMeters(
            requestedRadiusMeters = 1_500,
            centreLatitude = madridLat,
            centreLongitude = madridLon,
            resultCoordinates = emptyList(),
            isTruncated = true,
        )

        assertEquals(1_500, radius)
    }

    /**
     * The box is the cheap first cut and must err WIDE — a point on the circle has to survive it,
     * because the real distance filter runs afterwards and can only remove, never restore.
     */
    @Test
    fun `should_containTheWholeCircle_when_cuttingTheBoundingBox`() {
        val box = boundingBox(madridLat, madridLon, radiusMeters = 1_500)
        val onTheEdge = northOf(madridLat, 1_500.0)

        assertTrue(onTheEdge in box.minLatitude..box.maxLatitude)
        assertTrue(madridLon in box.minLongitude..box.maxLongitude)
    }
}
