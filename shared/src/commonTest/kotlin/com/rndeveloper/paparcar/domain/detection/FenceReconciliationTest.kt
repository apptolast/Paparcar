package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.UserParking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [IOS-F1-A-CONTROLLER-FOR-THE-HAPPY-PATH-001] The reconcile decision the iOS controller runs on
 *  start: Room sessions vs OS-monitored regions. */
class FenceReconciliationTest {

    private fun session(id: String, fenceId: String?) = UserParking(
        id = id,
        vehicleId = "veh-$id",
        location = GpsPoint(40.4, -3.7, 8f, 1_000L, 0f),
        geofenceId = fenceId,
    )

    @Test
    fun should_registerMissingAndRemoveOrphans_when_setsDiverge() {
        val actions = reconcileFences(
            activeSessions = listOf(session("a", "fence-a"), session("b", "fence-b")),
            monitoredIds = setOf("fence-b", "fence-zombie"),
        )
        assertEquals(listOf("a"), actions.toRegister.map { it.id })
        assertEquals(setOf("fence-zombie"), actions.toRemove)
        assertTrue(!actions.isInSync)
    }

    @Test
    fun should_reportInSync_when_roomAndOsAgree() {
        val actions = reconcileFences(
            activeSessions = listOf(session("a", "fence-a")),
            monitoredIds = setOf("fence-a"),
        )
        assertTrue(actions.isInSync)
    }

    @Test
    fun should_skipSessionsWithoutFenceId_when_noneWasEverMinted() {
        // Inventory is restored, identity is never invented: a session that never had a fence
        // cannot gain one here.
        val actions = reconcileFences(
            activeSessions = listOf(session("a", null)),
            monitoredIds = emptySet(),
        )
        assertTrue(actions.isInSync)
    }

    @Test
    fun should_dropTheOldestBeyondTheBudget_when_wantedExceedsTheCap() {
        // Callers pass sessions most-recent-first; the cap keeps the newest parks monitored.
        val sessions = (1..25).map { session("s$it", "fence-$it") }
        val actions = reconcileFences(sessions, monitoredIds = emptySet())
        assertEquals(IOS_REGION_BUDGET, actions.toRegister.size)
        assertEquals("s1", actions.toRegister.first().id)
        assertEquals("s${IOS_REGION_BUDGET}", actions.toRegister.last().id)
    }

    @Test
    fun should_removeAFenceBeyondTheBudget_when_itLostItsSlot() {
        // A monitored fence whose session fell off the capped wanted set counts as removable —
        // the budget is enforced, not just respected on registration.
        val sessions = (1..21).map { session("s$it", "fence-$it") }
        val actions = reconcileFences(sessions, monitoredIds = setOf("fence-21"))
        assertEquals(setOf("fence-21"), actions.toRemove)
    }
}
