package com.rndeveloper.paparcar.domain.detection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TripBatteryGaugeTest {

    private fun sample(microAh: Long?, charging: Boolean = false, atMs: Long) =
        TripBatteryGauge.Sample(chargeMicroAh = microAh, charging = charging, atMs = atMs)

    private val longEnough = TripBatteryGauge.MIN_CLEAN_DURATION_MS + 1

    @Test
    fun `should measure a clean positive cost when the job only discharged`() {
        val cost = TripBatteryGauge.settle(
            sample(4_000_000L, atMs = 0L),
            sample(3_950_000L, atMs = longEnough),
        )
        assertEquals(50_000L, cost.deltaMicroAh, "discharge decreases the counter - cost is start minus end")
        assertTrue(cost.clean)
    }

    @Test
    fun `should keep the figure but refuse the clean verdict when charging at either end`() {
        val cost = TripBatteryGauge.settle(
            sample(4_000_000L, charging = true, atMs = 0L),
            sample(3_950_000L, atMs = longEnough),
        )
        assertEquals(50_000L, cost.deltaMicroAh, "a dirty figure is still data")
        assertFalse(cost.clean, "the counter moved the other way for part of the span")
    }

    @Test
    fun `should refuse the clean verdict when the delta is negative - plugged in mid-span unseen`() {
        val cost = TripBatteryGauge.settle(
            sample(3_900_000L, atMs = 0L),
            sample(4_000_000L, atMs = longEnough),
        )
        assertEquals(-100_000L, cost.deltaMicroAh)
        assertFalse(cost.clean)
    }

    @Test
    fun `should report no figure at all when the fuel gauge answers nothing`() {
        val cost = TripBatteryGauge.settle(
            sample(null, atMs = 0L),
            sample(3_950_000L, atMs = longEnough),
        )
        assertNull(cost.deltaMicroAh, "absent, never invented")
        assertFalse(cost.clean)
    }

    @Test
    fun `should refuse the clean verdict when the span is shorter than the noise floor`() {
        val cost = TripBatteryGauge.settle(
            sample(4_000_000L, atMs = 0L),
            sample(3_999_000L, atMs = TripBatteryGauge.MIN_CLEAN_DURATION_MS - 1),
        )
        assertEquals(1_000L, cost.deltaMicroAh)
        assertFalse(cost.clean, "under the floor the fuel gauge's own noise outweighs the signal")
    }
}
