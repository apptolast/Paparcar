package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.detection.ArrivalResolution
import io.apptolast.paparcar.domain.detection.ArrivalResolutionRecord
import io.apptolast.paparcar.domain.detection.ExitDeliveryRecords
import io.apptolast.paparcar.domain.detection.PendingArm
import io.apptolast.paparcar.domain.detection.PendingArmRecords

/**
 * [IOS-F0-06] In-memory fakes of the side-record contracts. They implement the FULL semantics
 * the contract tests pin (heartbeat latch, no-op after clear, latest-wins slot) — reference
 * behavior for the platform impls.
 */

class FakePendingArmRecords : PendingArmRecords {

    private val records = mutableMapOf<String, PendingArm>()

    override fun arm(armId: String, armedAt: Long, trigger: String) {
        records[armId] = PendingArm(armId, armedAt, armedAt, trigger, sawDriving = false)
    }

    override fun heartbeat(armId: String, heartbeatAt: Long, sawDriving: Boolean) {
        val prev = records[armId] ?: return
        records[armId] = prev.copy(heartbeatAt = heartbeatAt, sawDriving = sawDriving || prev.sawDriving)
    }

    override fun clear(armId: String) {
        records.remove(armId)
    }

    override fun scanStale(nowMs: Long, deadMs: Long): List<PendingArm> =
        records.values.filter { nowMs - it.heartbeatAt > deadMs }
}

class FakeExitDeliveryRecords : ExitDeliveryRecords {

    private val deliveries = mutableMapOf<String, Long>()

    override fun record(geofenceId: String, deliveredAtMs: Long) {
        deliveries[geofenceId] = deliveredAtMs
    }

    override fun deliveredAt(geofenceId: String): Long? = deliveries[geofenceId]

    override fun hasRecentDelivery(nowMs: Long, maxAgeMs: Long): Boolean =
        deliveries.values.any { (nowMs - it) in 0..maxAgeMs }
}

class FakeArrivalResolutionRecord : ArrivalResolutionRecord {

    private var slot: ArrivalResolution? = null

    override fun stamp(atMs: Long, latitude: Double, longitude: Double) {
        slot = ArrivalResolution(atMs = atMs, latitude = latitude, longitude = longitude)
    }

    override fun latest(): ArrivalResolution? = slot
}
