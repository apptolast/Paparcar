package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.model.GpsPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ParkingSearchTimeTest {

    /** ~111 m per 0.001° of latitude. The anchor sits at meters=0. */
    private fun p(meters: Double, ts: Long) =
        GpsPoint(36.6 + meters / 111_320.0, -6.27, 5f, ts, 8f)

    /** A point offset EAST so laps have two dimensions, not a back-and-forth line. */
    private fun pe(metersNorth: Double, metersEast: Double, ts: Long) =
        GpsPoint(
            36.6 + metersNorth / 111_320.0,
            -6.27 + metersEast / (111_320.0 * 0.802),
            5f,
            ts,
            8f,
        )

    private val anchor = p(0.0, ts = 1_000_000L)

    // ── DIRECT arrivals are a measured ZERO, not a floor ────────────────────────────────────────

    @Test
    fun `should measure zero when the arrival drove straight in`() {
        // Crosses the 300 m boundary at t=940s and stops at t=980s, essentially in a line: red
        // lights on the way to your door are not a search. The old ~60 s floor is retired.
        val route = listOf(
            p(2_000.0, ts = 800_000L),
            p(600.0, ts = 900_000L),   // still outside the radius
            p(250.0, ts = 940_000L),   // entered — the clock starts here
            p(20.0, ts = 980_000L),    // the measured stop, at the car
        )
        assertEquals(0L, ParkingSearchTime.measure(route, anchor))
    }

    @Test
    fun `should measure zero when a slow direct approach outlasts the time floor without laps`() {
        // 4 minutes inside the radius (queue, lights) but the driven line is the straight way in:
        // time alone must not turn traffic into a search — only the driven SHAPE may.
        val route = listOf(
            p(2_000.0, ts = 600_000L),
            p(400.0, ts = 700_000L),   // last time far
            p(250.0, ts = 720_000L),   // entered
            p(150.0, ts = 840_000L),
            p(80.0, ts = 920_000L),
            p(15.0, ts = 960_000L),    // the measured stop
        )
        assertEquals(0L, ParkingSearchTime.measure(route, anchor))
    }

    // ── Demonstrated laps ARE the search, timed to the measured stop ────────────────────────────

    @Test
    fun `should charge every lap when the trip circled the block`() {
        // Entered at t=700s, drove two blocks' worth of laps inside the radius, stopped at t=960s.
        // The figure ends at the measured stop (t=960s), never at the anchor's clock (t=1000s).
        val route = listOf(
            p(2_000.0, ts = 600_000L),
            p(400.0, ts = 680_000L),        // last time FAR
            p(250.0, ts = 700_000L),        // re-entered — clock starts
            pe(120.0, 180.0, ts = 760_000L), // lap east
            pe(240.0, 40.0, ts = 820_000L),  // lap north
            pe(90.0, -160.0, ts = 880_000L), // lap west — still inside: laps do not reset the clock
            p(20.0, ts = 960_000L),          // the measured stop
        )
        assertEquals(260_000L, ParkingSearchTime.measure(route, anchor))
    }

    @Test
    fun `should restart the clock when a through-street passed the spot early in the trip`() {
        // Passed within 100 m at t=200s on the way OUT, drove far, came back with laps. Only the
        // final approach counts — "last time far", never "first time near".
        val route = listOf(
            p(100.0, ts = 200_000L),         // early pass-by must not start the clock
            p(3_000.0, ts = 500_000L),       // far again
            p(250.0, ts = 700_000L),         // final approach begins
            pe(120.0, 180.0, ts = 780_000L),
            pe(240.0, 40.0, ts = 850_000L),
            pe(90.0, -160.0, ts = 910_000L),
            p(20.0, ts = 960_000L),          // the measured stop
        )
        assertEquals(260_000L, ParkingSearchTime.measure(route, anchor))
    }

    // ── Confirmation latency is NOT a search [STATS-EVERY-NUMBER-CITES-ITS-EVIDENCE-001] ────────

    @Test
    fun `should not book the minutes a manual pin waited for its tap`() {
        // Field case, Tuesday 2026-09-22 00:26 (session 3d95ffc8): direct drive home, car stopped
        // at t=980s, the user answered the prompt ~6.5 min later — the anchor is a wall clock. The
        // old formula booked 387 s of "searching"; the measured stop ends the clock instead.
        val tapAnchor = p(0.0, ts = 1_367_000L) // the tap, 387 s after the stop
        val route = listOf(
            p(2_000.0, ts = 800_000L),
            p(600.0, ts = 900_000L),
            p(250.0, ts = 940_000L),
            p(20.0, ts = 980_000L),    // the measured stop
            p(45.0, ts = 1_050_000L),  // the walk away, still ticking in the buffer
            p(70.0, ts = 1_150_000L),
        )
        assertEquals(0L, ParkingSearchTime.measure(route, tapAnchor))
    }

    @Test
    fun `should time a real search to the stop even when confirmation came much later`() {
        // Laps happened AND the confirm was late (unattended timeout): the laps are charged, the
        // latency is not.
        val lateAnchor = p(0.0, ts = 1_900_000L) // safety net confirmed 15+ min after the stop
        val route = listOf(
            p(2_000.0, ts = 600_000L),
            p(400.0, ts = 680_000L),
            p(250.0, ts = 700_000L),         // re-entered
            pe(120.0, 180.0, ts = 760_000L),
            pe(240.0, 40.0, ts = 820_000L),
            pe(90.0, -160.0, ts = 880_000L),
            p(20.0, ts = 960_000L),          // the measured stop
        )
        assertEquals(260_000L, ParkingSearchTime.measure(route, lateAnchor))
    }

    @Test
    fun `should end the clock at the stop when the walk drifted far from the pin`() {
        // After parking, the user walked 400 m away before tapping: pedestrian fixes beyond the
        // radius must neither extend the search nor reset the entry.
        val tapAnchor = p(0.0, ts = 1_500_000L)
        val route = listOf(
            p(2_000.0, ts = 600_000L),
            p(400.0, ts = 680_000L),
            p(250.0, ts = 700_000L),
            pe(120.0, 180.0, ts = 760_000L),
            pe(240.0, 40.0, ts = 820_000L),
            pe(90.0, -160.0, ts = 880_000L),
            p(20.0, ts = 960_000L),    // the measured stop
            p(150.0, ts = 1_100_000L), // walking away
            p(400.0, ts = 1_300_000L), // beyond the radius on foot — must not become "last far"
        )
        assertEquals(260_000L, ParkingSearchTime.measure(route, tapAnchor))
    }

    // ── Unmeasurable is null, never a number ────────────────────────────────────────────────────

    @Test
    fun `should answer null when the trip never left the neighbourhood`() {
        val route = listOf(p(120.0, ts = 900_000L), p(20.0, ts = 950_000L))
        assertNull(
            ParkingSearchTime.measure(route, anchor),
            "a short hop cannot distinguish searching from arriving",
        )
    }

    @Test
    fun `should answer null when the route never witnessed the arrival`() {
        // GPS died 250 m out: the stop instant is unknown — unmeasurable, not zero.
        val route = listOf(
            p(2_000.0, ts = 800_000L),
            p(600.0, ts = 900_000L),
            p(250.0, ts = 940_000L),
        )
        assertNull(ParkingSearchTime.measure(route, anchor))
    }

    @Test
    fun `should answer null when clocks are missing or the figure is implausible`() {
        assertNull(ParkingSearchTime.measure(listOf(p(2_000.0, ts = 0L)), anchor), "no clock, no figure")
        assertNull(
            ParkingSearchTime.measure(listOf(p(2_000.0, ts = 800_000L)), p(0.0, ts = 0L)),
            "an unstamped anchor ends no clock",
        )
        val tooLong = listOf(
            p(400.0, ts = 1_000L),
            pe(120.0, 180.0, ts = 2_000L),
            pe(240.0, 40.0, ts = 30 * 60_000L),
            pe(90.0, -160.0, ts = 60 * 60_000L),
            p(20.0, ts = 2_001L + ParkingSearchTime.MAX_PLAUSIBLE_SEARCH_MS),
        )
        assertNull(
            ParkingSearchTime.measure(
                tooLong,
                p(0.0, ts = 3_000L + ParkingSearchTime.MAX_PLAUSIBLE_SEARCH_MS),
            ),
            "an anomaly is not a figure",
        )
    }
}
