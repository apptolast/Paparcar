package io.apptolast.paparcar.data.repository

import io.apptolast.paparcar.data.datasource.local.room.UserParkingEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [SYNC-RECONCILE-USERPARKING-001] The inbound Last-Write-Wins merge for parking sessions. The two
 * field incidents (2026-07-05, Redmi) are the first two tests: a stale remote `isActive=true` must
 * never resurrect a locally-ended session, and a clear whose remote propagation lagged must not
 * leave the session mirrored active.
 */
class UserParkingReconcileTest {

    private fun s(
        id: String,
        isActive: Boolean = false,
        updatedAt: Long = 0,
        pendingSync: Boolean = false,
        vehicleId: String? = "veh1",
        tripMaxSpeedMps: Float? = null,
        armEvidence: String? = null,
    ) = UserParkingEntity(
        id = id,
        userId = "u1",
        vehicleId = vehicleId,
        latitude = 36.6,
        longitude = -6.2,
        accuracy = 10f,
        timestamp = updatedAt,
        isActive = isActive,
        updatedAt = updatedAt,
        pendingSync = pendingSync,
        tripMaxSpeedMps = tripMaxSpeedMps,
        armEvidence = armEvidence,
    )

    @Test
    fun `stale remote active does not resurrect a locally-ended session`() {
        // Field incident: re-park cleared session A locally (pending, newer, inactive); the remote
        // clear lagged so Firestore still had A active. Cold-start sync must keep A ended.
        val local = listOf(s("A", isActive = false, updatedAt = 200, pendingSync = true))
        val remote = listOf(s("A", isActive = true, updatedAt = 100))

        val merged = reconcileParkingSessions(local, remote)

        assertEquals(1, merged.size)
        assertFalse(merged.single().isActive, "local end must win over the stale remote active")
        assertTrue(merged.single().pendingSync)
    }

    @Test
    fun `delayed clear leaves exactly one active after merge`() {
        // Two remote actives (A stale-active because its clear didn't propagate, B the real current).
        // Locally A is ended (pending, newer), B is active. The merge must yield one active: B.
        val local = listOf(
            s("A", isActive = false, updatedAt = 200, pendingSync = true),
            s("B", isActive = true, updatedAt = 200, pendingSync = true),
        )
        val remote = listOf(
            s("A", isActive = true, updatedAt = 100),
            s("B", isActive = true, updatedAt = 190),
        )

        val merged = reconcileParkingSessions(local, remote)

        val active = merged.filter { it.isActive }.map { it.id }
        assertEquals(listOf("B"), active, "only the real current session stays active")
    }

    @Test
    fun `pending clear self-heals once remote catches up`() {
        val local = listOf(s("A", isActive = false, updatedAt = 200, pendingSync = true))
        val remote = listOf(s("A", isActive = false, updatedAt = 200)) // server caught up

        val merged = reconcileParkingSessions(local, remote)

        assertFalse(merged.single().isActive)
        assertFalse(merged.single().pendingSync, "taken from remote → clean again")
    }

    @Test
    fun `clean local takes remote - reinstall restore path`() {
        // Post-migration rows are updatedAt=0/pending=false; the reinstall/device-switch restore this
        // sync exists for must still import remote truth.
        val local = emptyList<UserParkingEntity>()
        val remote = listOf(s("A", isActive = true, updatedAt = 100))

        val merged = reconcileParkingSessions(local, remote)

        assertEquals(1, merged.size)
        assertTrue(merged.single().isActive)
    }

    @Test
    fun `local-only pending session created offline is kept`() {
        val local = listOf(
            s("known", updatedAt = 10),
            s("offline", isActive = true, updatedAt = 200, pendingSync = true),
        )
        val remote = listOf(s("known", updatedAt = 10))

        val merged = reconcileParkingSessions(local, remote)

        assertEquals(setOf("known", "offline"), merged.map { it.id }.toSet())
    }

    @Test
    fun `local-only detection provenance survives taking remote`() {
        // tripMaxSpeedMps + armEvidence are never written to Firestore; a remote-wins merge must not
        // blank them (they feed the repark-plausibility guard).
        val local = listOf(s("A", updatedAt = 10, tripMaxSpeedMps = 12.5f, armEvidence = "speed"))
        val remote = listOf(s("A", updatedAt = 20, tripMaxSpeedMps = null, armEvidence = null))

        val merged = reconcileParkingSessions(local, remote)

        assertEquals(12.5f, merged.single().tripMaxSpeedMps)
        assertEquals("speed", merged.single().armEvidence)
    }
}
