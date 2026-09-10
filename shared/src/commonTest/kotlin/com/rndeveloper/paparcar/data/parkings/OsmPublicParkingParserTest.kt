package com.rndeveloper.paparcar.data.parkings

import com.rndeveloper.paparcar.domain.parkings.FeeInfo
import com.rndeveloper.paparcar.domain.parkings.PublicParkingKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fixture copied from the live Overpass answer within 1 km of Puerta del Sol (09-09-2026): 39
 * elements, of which 19 underground, 15 excluded by access, 2 kerbside.
 * [PARKINGS-ON-THE-MAP-001]
 *
 * ⛔ The tags below are the ones the feed really emits — `layer:"-1"`, `operator:type`, a bare
 * `maxheight`, a `capacity` as a string — not the ones a parser author would invent. The last time a
 * fixture was written from expectation instead of measurement, a province test passed on an input
 * the real geocoder never produces. [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
 */
class OsmPublicParkingParserTest {

    private val fixture = """
        {"elements":[
          {"type":"node","id":26341193,"lat":40.4157,"lon":-3.7028,
           "tags":{"access":"yes","amenity":"parking","capacity":"285","fee":"yes","layer":"-1",
                   "maxheight":"1.8","name":"Parking Benavente","opening_hours":"24/7",
                   "operator":"EMT Madrid","operator:type":"public","parking":"underground"}},
          {"type":"node","id":347603099,"lat":40.4152,"lon":-3.7075,
           "tags":{"amenity":"parking","layer":"-1","name":"Parking Plaza Mayor",
                   "parking":"underground","phone":"+34 913 66 13 43"}},
          {"type":"node","id":551573440,"lat":40.4185,"lon":-3.7060,
           "tags":{"access":"customers","amenity":"parking","fee":"yes","layer":"-1",
                   "name":"Parking Plaza de las Descalzas","parking":"underground"}},
          {"type":"node","id":347280797,"lat":40.4190,"lon":-3.7010,
           "tags":{"access":"private","amenity":"parking","layer":"-1","parking":"underground"}},
          {"type":"way","id":1076631707,"center":{"lat":40.4120,"lon":-3.7000},
           "tags":{"access":"yes","amenity":"parking","fee":"yes","parking":"street_side",
                   "surface":"asphalt"}},
          {"type":"way","id":908575097,"center":{"lat":40.4130,"lon":-3.7090},
           "tags":{"amenity":"parking"}},
          {"type":"node","id":900001,"lat":40.4140,"lon":-3.7020,
           "tags":{"access":"customers","amenity":"parking","name":"Centro comercial"}},
          {"type":"node","id":900002,"lat":40.4145,"lon":-3.7025,
           "tags":{"amenity":"parking","fee":"no","maxheight":"6'6\"","parking":"surface"}}
        ]}
    """.trimIndent()

    private val parsed = OsmPublicParkingParser.parse(fixture)

    @Test
    fun `should_dropKerbsideParking_becauseThatIsTheStreetWeAlreadySearch`() {
        // 256 of Málaga's 300 elements are street_side or lane. Drawing them would lay anonymous
        // polygons over the very kerb Paparcar publishes free spots on.
        assertTrue(parsed.none { it.id == "osm/way/1076631707" })
    }

    @Test
    fun `should_dropPrivateParking_becauseItIsNotYoursToUse`() {
        assertTrue(parsed.none { it.id == "osm/node/347280797" })
    }

    @Test
    fun `should_keepACustomersParking_whenItCharges_becauseYouCanBuyYourWayIn`() {
        // Measured: all three `access=customers` around Sol are `fee=yes`, and two are ordinary paid
        // garages — one run by the city council. Excluding the tag deleted them from the capital.
        assertTrue(parsed.any { it.id == "osm/node/551573440" })
    }

    @Test
    fun `should_dropACustomersParking_whenNothingSaysYouMayPay`() {
        assertTrue(parsed.none { it.id == "osm/node/900001" })
    }

    @Test
    fun `should_readAWaysCenter_notOnlyNodes`() {
        val untagged = parsed.single { it.id == "osm/way/908575097" }
        assertEquals(40.413, untagged.latitude, absoluteTolerance = 1e-6)
        assertEquals(PublicParkingKind.UNKNOWN, untagged.kind)
    }

    @Test
    fun `should_readCapacityHeightAndHours_whenTheyAreStated`() {
        val benavente = parsed.first { it.id == "osm/node/26341193" }
        assertEquals("Parking Benavente", benavente.name)
        assertEquals("EMT Madrid", benavente.operator)
        assertEquals(PublicParkingKind.UNDERGROUND, benavente.kind)
        assertEquals(285, benavente.capacity)
        assertEquals(1.8, benavente.maxHeightMeters)
        assertEquals("24/7", benavente.openingHours)
        assertEquals(FeeInfo.PAID, benavente.fee)
    }

    @Test
    fun `should_sayUnknown_whenNobodyStatedAFee`() {
        // ⛔ The untagged 71% are not free. Saying so sends a driver into a barrier.
        assertEquals(FeeInfo.UNKNOWN, parsed.first { it.id == "osm/node/347603099" }.fee)
        assertEquals(FeeInfo.UNKNOWN, parsed.single { it.id == "osm/way/908575097" }.fee)
        assertEquals(FeeInfo.FREE, parsed.first { it.id == "osm/node/900002" }.fee)
    }

    @Test
    fun `should_leaveAnImperialClearanceNull_ratherThanGuessIt`() {
        // Guessing a clearance is how a van gets wedged under a beam.
        assertNull(parsed.first { it.id == "osm/node/900002" }.maxHeightMeters)
    }

    @Test
    fun `should_titleByNameOrOperator_andLeaveTheRestToTheKind`() {
        assertEquals("Parking Plaza Mayor", parsed.first { it.id == "osm/node/347603099" }.displayName)
        assertNull(parsed.single { it.id == "osm/way/908575097" }.displayName)
    }

    @Test
    fun `should_excludeTheKerb_inTheQueryItself_becauseItIsMostOfThePayload`() {
        // 256 of Málaga's 300 elements are roadside: downloading them to drop them on the phone
        // spends the user's data on our convenience.
        val query = OsmPublicParkingParser.buildQuery(40.4168, -3.7038, 1_500, 20)
        assertTrue(query.contains("street_side"), query)
        assertTrue(query.contains("out center"), query)
    }

    @Test
    fun `should_askForUntaggedCarParksExplicitly_ratherThanTrustTheNegatedRegex`() {
        // ⛔ THE test of this query. Whether `[k!~"v"]` also matches elements missing `k` decides the
        // layer in villages — 59 of Cuenca's 90 car parks declare no `parking` tag — and it would
        // look perfect in a capital either way. `[!parking]` is unambiguous, so the union is right
        // under both semantics instead of resting on one nobody measured.
        val query = OsmPublicParkingParser.buildQuery(40.0704, -2.1374, 1_500, 20)
        assertTrue(query.contains("[!parking]"), query)
        assertTrue(query.count { it == '(' } >= 1 && query.contains(");"), "must be a union: $query")
    }

    @Test
    fun `should_notFilterAccessInTheQuery_becauseCustomersDependsOnTheFee`() {
        // `access=customers` is judged against `fee` (see the parser), which no tag filter can
        // express — so access is decided on the phone, over the whole element.
        val query = OsmPublicParkingParser.buildQuery(40.4168, -3.7038, 1_500, 20)
        assertTrue(!query.contains("customers"), query)
        assertTrue(!query.contains("access"), query)
    }
}
