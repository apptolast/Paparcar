package com.rndeveloper.paparcar.domain.detection

import com.rndeveloper.paparcar.fakes.FakeArrivalResolutionRecord
import com.rndeveloper.paparcar.fakes.FakeExitDeliveryRecords
import com.rndeveloper.paparcar.fakes.FakePendingArmRecords
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [IOS-F0-06] Semantic contract of the side-record ports, pinned against the in-memory fakes.
 * These are the invariants any platform impl (Android prefs wrapper, iOS NSUserDefaults) must
 * hold — the safety net's recovery logic and the iOS wake reconstruction both depend on them.
 */
class DetectionSideRecordsContractTest {

    private val t0 = 1_783_185_093_721L

    // ── PendingArmRecords [DET-NEVER-SILENT-001] ─────────────────────────────────────────────

    @Test
    fun should_notReportStale_when_heartbeatIsFresh() {
        val records = FakePendingArmRecords()
        records.arm("arm-1", armedAt = t0, trigger = "GEOFENCE_EXIT")
        assertTrue(records.scanStale(nowMs = t0 + 1_000L, deadMs = 60_000L).isEmpty())
    }

    @Test
    fun should_reportStale_when_heartbeatExceedsDeadThreshold() {
        val records = FakePendingArmRecords()
        records.arm("arm-1", armedAt = t0, trigger = "GEOFENCE_EXIT")
        val stale = records.scanStale(nowMs = t0 + 61_000L, deadMs = 60_000L)
        assertEquals(1, stale.size)
        assertEquals("arm-1", stale.single().armId)
        assertFalse(stale.single().sawDriving, "a fresh arm never saw driving")
    }

    @Test
    fun should_latchSawDrivingForever_when_anyHeartbeatReportsIt() {
        // The latch is what lets the watchdog distinguish "died mid-trip after real driving"
        // from "armed and nothing happened" — a later false must never unlatch it.
        val records = FakePendingArmRecords()
        records.arm("arm-1", armedAt = t0, trigger = "GEOFENCE_EXIT")
        records.heartbeat("arm-1", heartbeatAt = t0 + 10_000L, sawDriving = true)
        records.heartbeat("arm-1", heartbeatAt = t0 + 20_000L, sawDriving = false)
        val stale = records.scanStale(nowMs = t0 + 200_000L, deadMs = 60_000L)
        assertTrue(stale.single().sawDriving, "sawDriving latches true and never unlatches")
        assertEquals(t0 + 20_000L, stale.single().heartbeatAt)
    }

    @Test
    fun should_ignoreHeartbeat_when_armWasAlreadyCleared() {
        // A terminal must stay terminal: a straggler heartbeat from a dying coroutine must not
        // resurrect the pending and re-trigger the watchdog on a session that resolved cleanly.
        val records = FakePendingArmRecords()
        records.arm("arm-1", armedAt = t0, trigger = "BT_DISCONNECT")
        records.clear("arm-1")
        records.heartbeat("arm-1", heartbeatAt = t0 + 10_000L, sawDriving = true)
        assertTrue(records.scanStale(nowMs = t0 + 500_000L, deadMs = 60_000L).isEmpty())
    }

    // ── ExitDeliveryRecords [DET-CONJUNCTION-001] ────────────────────────────────────────────

    @Test
    fun should_returnDeliveryTimestamp_when_recorded_andNullWhenUnknown() {
        val records = FakeExitDeliveryRecords()
        records.record("fence-1", deliveredAtMs = t0)
        assertEquals(t0, records.deliveredAt("fence-1"))
        assertNull(records.deliveredAt("fence-2"))
    }

    @Test
    fun should_answerRecentDelivery_onlyInsideTheWindow() {
        val records = FakeExitDeliveryRecords()
        records.record("fence-1", deliveredAtMs = t0)
        assertTrue(records.hasRecentDelivery(nowMs = t0 + 5_000L, maxAgeMs = 10_000L))
        assertFalse(records.hasRecentDelivery(nowMs = t0 + 11_000L, maxAgeMs = 10_000L), "expired")
        assertFalse(records.hasRecentDelivery(nowMs = t0 - 1_000L, maxAgeMs = 10_000L), "future stamps never count")
    }

    // ── ArrivalResolutionRecord [DET-BACKFILL-TAINT-001] ─────────────────────────────────────

    @Test
    fun should_keepOnlyTheLatestStamp_when_stampedTwice() {
        // ONE slot, latest wins: it describes THE arrival in flight, never a history.
        val record = FakeArrivalResolutionRecord()
        assertNull(record.latest())
        record.stamp(atMs = t0, latitude = 36.60, longitude = -6.25)
        record.stamp(atMs = t0 + 60_000L, latitude = 36.61, longitude = -6.26)
        val latest = record.latest()
        assertEquals(t0 + 60_000L, latest?.atMs)
        assertEquals(36.61, latest?.latitude)
    }
}
