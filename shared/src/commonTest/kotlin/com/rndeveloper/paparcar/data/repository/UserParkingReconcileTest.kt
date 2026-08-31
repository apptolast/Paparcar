package com.rndeveloper.paparcar.data.repository

import com.rndeveloper.paparcar.data.datasource.local.room.UserParkingEntity
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
        detectionPath: String? = null,
        retractedAtMs: Long? = null,
        zoneRadiusMeters: Float? = null,
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
        detectionPath = detectionPath,
        retractedAtMs = retractedAtMs,
        zoneRadiusMeters = zoneRadiusMeters,
    )

    // ── The doubt radius: one null, two meanings ─────────────────────────────────────────────
    // [DET-NOTHING-TO-JUDGE-IS-NOT-NO-DOUBT-001]

    /**
     * The case `zoneRadiusMeters = r.zoneRadiusMeters ?: l?.zoneRadiusMeters` was written FOR, and
     * it is right: a remote document written before the field travelled
     * ([SYNC-A-PARKING-MUST-TRAVEL-WHOLE-001]) carries null, and taking that null would turn a zone
     * this device MEASURED into an exact pin — claiming more certainty than we have.
     */
    @Test
    fun `a legacy remote without the field keeps the radius this device measured`() {
        val local = listOf(s("A", updatedAt = 100, zoneRadiusMeters = 250f))
        val remote = listOf(s("A", updatedAt = 200, zoneRadiusMeters = null))

        val merged = reconcileParkingSessions(local, remote)

        assertEquals(250f, merged.single().zoneRadiusMeters, "a legacy doc must not erase a measured zone")
    }

    /**
     * ⚠️ **CHARACTERIZATION OF A KNOWN DEFECT — this test is GREEN on purpose and asserts the WRONG
     * behaviour.** It exists because the residual was written in prose in
     * `docs/backlog/park-a-pin-must-say-who-placed-it-001.md` and prose does not fail a build.
     *
     * [PARK-A-PIN-MUST-SAY-WHO-PLACED-IT-001] made the drag clear `zoneRadiusMeters` deliberately:
     * the user has just said where the car is, so the doubt is answered and the target badge must
     * go. On the device that did the drag, it does.
     *
     * On a SECOND device that still holds the old radius, this merge puts it back — the badge is
     * resurrected on a pin the user corrected by hand. The `?:` cannot tell the two nulls apart,
     * and the test above is the reason it leans the way it does.
     *
     * ⛔ **When [DET-NOTHING-TO-JUDGE-IS-NOT-NO-DOUBT-001] lands, this assertion must be INVERTED**
     * (expect `null`) and this KDoc deleted. If it is still here and still green, the defect is
     * still shipping.
     */
    @Test
    fun `characterizes the defect - another device resurrects the radius a drag cleared`() {
        // Device B still has the pre-drag zone in Room.
        val local = listOf(s("A", updatedAt = 100, zoneRadiusMeters = 250f))
        // Device A dragged the pin: it cleared the radius ON PURPOSE and uploaded that null.
        val remote = listOf(s("A", updatedAt = 200, zoneRadiusMeters = null))

        val merged = reconcileParkingSessions(local, remote)

        assertEquals(
            250f,
            merged.single().zoneRadiusMeters,
            "TODAY the badge comes back; the correct answer is null — see DET-NOTHING-TO-JUDGE-IS-NOT-NO-DOUBT-001",
        )
    }

    /**
     * **The tesis of [DET-NOTHING-TO-JUDGE-IS-NOT-NO-DOUBT-001], stated as an assertion.**
     *
     * The two tests above are not two scenarios that happen to agree — their inputs are *the same
     * bytes*. "The remote predates the field" and "the remote says there is no radius" reach this
     * function as one indistinguishable value, so no amount of care inside the merge can separate
     * them: the information is not there to read.
     *
     * That is why the fix cannot live here. Whatever shape it takes, it has to make the two causes
     * distinguishable BEFORE they arrive — which is the same conclusion `markFenceStatePoisoned`
     * reached when it replaced an absent key with an explicit stamp
     * ([DET-FENCE-REREGISTER-BY-CAUSE-001 §B]).
     *
     * 🔬 **Measured, not argued.** Dropping the `?: l?.zoneRadiusMeters` — the obvious "fix", taking
     * the remote always — turns BOTH tests above red in the same run: the defect one (as intended)
     * and the legacy one (as collateral). The trade-off is therefore not a matter of taste and
     * cannot be resolved by leaning the operator the other way; with today's inputs, every choice is
     * wrong for one of the two causes.
     */
    @Test
    fun `the two meanings of an absent radius are the same input`() {
        val remoteThatPredatesTheField = s("A", updatedAt = 200, zoneRadiusMeters = null)
        val remoteWhoseDragClearedIt = s("A", updatedAt = 200, zoneRadiusMeters = null)

        assertEquals(
            remoteThatPredatesTheField,
            remoteWhoseDragClearedIt,
            "if these ever differ, the reconcile CAN tell them apart and the ticket has landed",
        )
    }

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
    fun `detection provenance survives taking a pre-provenance remote snapshot`() {
        // tripMaxSpeedMps is local-only; armEvidence + detectionPath now sync but a LEGACY remote
        // snapshot (written before DET-PIN-PROVENANCE-001) carries them null. A remote-wins merge
        // against such a snapshot must not blank the local provenance.
        val local = listOf(
            s("A", updatedAt = 10, tripMaxSpeedMps = 12.5f, armEvidence = "speed", detectionPath = "steps+egress"),
        )
        val remote = listOf(s("A", updatedAt = 20, tripMaxSpeedMps = null, armEvidence = null, detectionPath = null))

        val merged = reconcileParkingSessions(local, remote)

        assertEquals(12.5f, merged.single().tripMaxSpeedMps)
        assertEquals("speed", merged.single().armEvidence)
        assertEquals("steps+egress", merged.single().detectionPath)
    }

    /**
     * [PARK-A-REFUTED-PIN-LEAVES-THE-HISTORY-001] A withdrawal is never un-done by a document that
     * predates it. The remote doc here is NEWER, so it wins the LWW — and it carries
     * `retractedAtMs = null` because it was written before the field travelled, or by a device that
     * has not received the withdrawal yet. Taking that null would put the phantom row back in the
     * history AND claim more than we know (that the parking was real). Same shape as
     * `zoneRadiusMeters` and the close provenance above it.
     */
    @Test
    fun `a withdrawal survives taking a newer remote snapshot that predates it`() {
        val local = listOf(s("A", updatedAt = 10, retractedAtMs = 1_700L))
        val remote = listOf(s("A", updatedAt = 20, retractedAtMs = null))

        val merged = reconcileParkingSessions(local, remote)

        assertEquals(1_700L, merged.single().retractedAtMs, "once withdrawn, withdrawn")
    }

    @Test
    fun `a remote withdrawal reaches a local row that has not heard about it`() {
        val local = listOf(s("A", updatedAt = 10, retractedAtMs = null))
        val remote = listOf(s("A", updatedAt = 20, retractedAtMs = 2_500L))

        val merged = reconcileParkingSessions(local, remote)

        assertEquals(2_500L, merged.single().retractedAtMs, "the withdrawal travels both ways")
    }
}
