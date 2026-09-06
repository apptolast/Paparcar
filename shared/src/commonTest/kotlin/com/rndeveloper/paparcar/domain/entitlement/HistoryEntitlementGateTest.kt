package com.rndeveloper.paparcar.domain.entitlement

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.UserParking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The downgrade rule of the history gate [MONEY-001]: free narrows the window, never the data.
 */
class HistoryEntitlementGateTest {

    private val nowMs = 1_790_000_000_000L
    private val dayMs = 86_400_000L

    private fun session(id: String, ageDays: Int, isActive: Boolean = false) = UserParking(
        id = id,
        userId = "user-1",
        vehicleId = "v-1",
        location = GpsPoint(
            latitude = 36.6,
            longitude = -6.2,
            accuracy = 8f,
            timestamp = nowMs - ageDays * dayMs,
            speed = 0f,
        ),
        isActive = isActive,
    )

    @Test
    fun `should_seeEverything_when_plus`() {
        val sessions = listOf(session("new", 1), session("old", 200))

        val gated = gateHistoryToEntitlement(sessions, isPlus = true, nowMs = nowMs)

        assertEquals(sessions, gated.visible)
        assertEquals(0, gated.hiddenOlderCount)
    }

    @Test
    fun `should_hideOlderThanThirtyDaysAndCountThem_when_free`() {
        val sessions = listOf(session("new", 1), session("edge", 29), session("old", 31), session("older", 200))

        val gated = gateHistoryToEntitlement(sessions, isPlus = false, nowMs = nowMs)

        assertEquals(listOf("new", "edge"), gated.visible.map { it.id })
        assertEquals(2, gated.hiddenOlderCount)
    }

    @Test
    fun `should_keepTheExactWindowBoundaryVisible_when_free`() {
        // Exactly FREE_HISTORY_WINDOW_DAYS old sits ON the boundary and stays visible (>=).
        val sessions = listOf(session("boundary", FREE_HISTORY_WINDOW_DAYS))

        val gated = gateHistoryToEntitlement(sessions, isPlus = false, nowMs = nowMs)

        assertEquals(1, gated.visible.size)
        assertEquals(0, gated.hiddenOlderCount)
    }

    @Test
    fun `should_neverHideAnActiveSession_when_free`() {
        // A car parked 60 days ago and never moved is the PRESENT, not history.
        val sessions = listOf(session("parked-long-ago", 60, isActive = true), session("old", 60))

        val gated = gateHistoryToEntitlement(sessions, isPlus = false, nowMs = nowMs)

        assertEquals(listOf("parked-long-ago"), gated.visible.map { it.id })
        assertEquals(1, gated.hiddenOlderCount)
    }

    @Test
    fun `should_reportZeroHidden_when_freeWithOnlyRecentSessions`() {
        val gated = gateHistoryToEntitlement(listOf(session("new", 3)), isPlus = false, nowMs = nowMs)

        assertEquals(1, gated.visible.size)
        assertEquals(0, gated.hiddenOlderCount)
    }
}
