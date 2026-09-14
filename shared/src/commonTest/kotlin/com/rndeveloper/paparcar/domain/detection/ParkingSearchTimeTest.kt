package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.model.GpsPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ParkingSearchTimeTest {

    /** ~111 m per 0.001° of latitude. The anchor sits at meters=0. */
    private fun p(meters: Double, ts: Long) =
        GpsPoint(36.6 + meters / 111_320.0, -6.27, 5f, ts, 8f)

    private val anchor = p(0.0, ts = 1_000_000L)

    @Test
    fun `should charge only the final approach on a direct arrival`() {
        // Far → crosses the 300 m boundary at t=940s → parks at t=1000s: 60 s of approach floor.
        val route = listOf(
            p(2_000.0, ts = 800_000L),
            p(600.0, ts = 900_000L),   // still outside the radius
            p(250.0, ts = 940_000L),   // entered — the clock starts here
            p(50.0, ts = 980_000L),
        )
        assertEquals(60_000L, ParkingSearchTime.measure(route, anchor))
    }

    @Test
    fun `should charge every lap when the trip circled the block`() {
        // Entered at t=700s, wandered inside the radius (a loop), never left again → 300 s hunting.
        val route = listOf(
            p(2_000.0, ts = 600_000L),
            p(400.0, ts = 680_000L),   // last time FAR
            p(250.0, ts = 700_000L),   // re-entered — clock starts
            p(120.0, ts = 800_000L),
            p(280.0, ts = 900_000L),   // still inside: laps do not reset the clock
            p(60.0, ts = 960_000L),
        )
        assertEquals(300_000L, ParkingSearchTime.measure(route, anchor))
    }

    @Test
    fun `should restart the clock when a through-street passed the spot early in the trip`() {
        // Passed within 100 m at t=200s on the way OUT, drove far, came back at t=940s. Only the
        // final approach counts — "last time far", never "first time near".
        val route = listOf(
            p(100.0, ts = 200_000L),   // early pass-by
            p(3_000.0, ts = 500_000L), // far again
            p(250.0, ts = 940_000L),   // final approach
        )
        assertEquals(60_000L, ParkingSearchTime.measure(route, anchor))
    }

    @Test
    fun `should answer null when the trip never left the neighbourhood`() {
        val route = listOf(p(120.0, ts = 900_000L), p(60.0, ts = 950_000L))
        assertNull(ParkingSearchTime.measure(route, anchor), "a short hop cannot distinguish searching from arriving")
    }

    @Test
    fun `should ignore fixes recorded after the anchor - the walk does not search`() {
        val route = listOf(
            p(2_000.0, ts = 800_000L),
            p(250.0, ts = 940_000L),
            p(500.0, ts = 1_100_000L), // pedestrian tail AFTER the park, far again — must not reset
        )
        assertEquals(60_000L, ParkingSearchTime.measure(route, anchor))
    }

    @Test
    fun `should answer null when clocks are missing or the figure is implausible`() {
        assertNull(ParkingSearchTime.measure(listOf(p(2_000.0, ts = 0L)), anchor), "no clock, no figure")
        assertNull(
            ParkingSearchTime.measure(listOf(p(2_000.0, ts = 800_000L)), p(0.0, ts = 0L)),
            "an unstamped anchor ends no clock",
        )
        val tooLong = listOf(
            p(2_000.0, ts = 1_000L),
            p(250.0, ts = 2_000L), // "entered" 998 s… but anchor is far later
        )
        assertNull(
            ParkingSearchTime.measure(tooLong, p(0.0, ts = 2_000L + ParkingSearchTime.MAX_PLAUSIBLE_SEARCH_MS + 1)),
            "an anomaly is not a figure",
        )
    }
}
