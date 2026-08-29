package com.rndeveloper.paparcar.detection

import android.content.Context
import androidx.core.content.edit
import com.rndeveloper.paparcar.detection.worker.ParkingSafetyNetWorker
import com.rndeveloper.paparcar.domain.detection.ArrivalResolution
import com.rndeveloper.paparcar.domain.detection.ArrivalResolutionRecord
import com.rndeveloper.paparcar.domain.detection.ExitDeliveryRecords
import com.rndeveloper.paparcar.domain.detection.PendingArm
import com.rndeveloper.paparcar.domain.detection.PendingArmRecords

/**
 * [IOS-F0-06] Android implementations of the common side-record contracts — thin wrappers over
 * the EXISTING SharedPreferences code (same file `parking_safety_net`, same keys, same value
 * formats). No field-data migration: a device upgrading through this change reads its records
 * unchanged. The service and workers keep their direct call paths for now; rewiring them onto
 * these ports belongs to the safety-net split (audit 8.2.3), not this ticket.
 */

class AndroidPendingArmRecords(private val context: Context) : PendingArmRecords {

    override fun arm(armId: String, armedAt: Long, trigger: String) =
        PendingDetectionStore.arm(context, armId, armedAt, trigger)

    override fun heartbeat(armId: String, heartbeatAt: Long, sawDriving: Boolean) =
        PendingDetectionStore.heartbeat(context, armId, heartbeatAt, sawDriving)

    override fun clear(armId: String) = PendingDetectionStore.clear(context, armId)

    override fun scanStale(nowMs: Long, deadMs: Long): List<PendingArm> =
        PendingDetectionStore.scanStale(context, nowMs, deadMs).map {
            PendingArm(
                armId = it.armId,
                armedAt = it.armedAt,
                heartbeatAt = it.heartbeatAt,
                trigger = it.trigger,
                sawDriving = it.sawDriving,
            )
        }
}

class AndroidExitDeliveryRecords(private val context: Context) : ExitDeliveryRecords {

    override fun record(geofenceId: String, deliveredAtMs: Long) =
        ParkingSafetyNetWorker.recordStaleExitDelivery(context, geofenceId, deliveredAtMs)

    override fun deliveredAt(geofenceId: String): Long? =
        context.getSharedPreferences(ParkingSafetyNetWorker.PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(ParkingSafetyNetWorker.EXIT_KEY_PREFIX + geofenceId, 0L)
            .takeIf { it > 0L }

    override fun hasRecentDelivery(nowMs: Long, maxAgeMs: Long): Boolean =
        ParkingSafetyNetWorker.hasRecentStaleExit(context, nowMs, maxAgeMs)
}

class AndroidArrivalResolutionRecord(private val context: Context) : ArrivalResolutionRecord {

    override fun stamp(atMs: Long, latitude: Double, longitude: Double) {
        context.getSharedPreferences(ParkingSafetyNetWorker.PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putLong(ParkingSafetyNetWorker.KEY_ARRIVAL_RESOLUTION_AT, atMs)
                putString(ParkingSafetyNetWorker.KEY_ARRIVAL_RESOLUTION_POS, "$latitude,$longitude")
            }
    }

    override fun latest(): ArrivalResolution? {
        val prefs = context.getSharedPreferences(ParkingSafetyNetWorker.PREFS_NAME, Context.MODE_PRIVATE)
        val atMs = prefs.getLong(ParkingSafetyNetWorker.KEY_ARRIVAL_RESOLUTION_AT, 0L)
            .takeIf { it > 0L } ?: return null
        val raw = prefs.getString(ParkingSafetyNetWorker.KEY_ARRIVAL_RESOLUTION_POS, null) ?: return null
        val parts = raw.split(',')
        val lat = parts.getOrNull(0)?.toDoubleOrNull() ?: return null
        val lon = parts.getOrNull(1)?.toDoubleOrNull() ?: return null
        return ArrivalResolution(atMs = atMs, latitude = lat, longitude = lon)
    }
}
