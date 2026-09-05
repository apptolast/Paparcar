package com.rndeveloper.paparcar.detection

import com.rndeveloper.paparcar.domain.detection.ArrivalResolution
import com.rndeveloper.paparcar.domain.detection.ArrivalResolutionRecord
import com.rndeveloper.paparcar.domain.detection.ExitDeliveryRecords
import com.rndeveloper.paparcar.domain.detection.PendingArm
import com.rndeveloper.paparcar.domain.detection.PendingArmRecords
import platform.Foundation.NSUserDefaults

/**
 * [IOS-F0-06] iOS implementations of the common side-record contracts, backed by
 * [NSUserDefaults]. Same key scheme and value formats as Android's `parking_safety_net`
 * SharedPreferences (namespaced with [KEY_NAMESPACE] to keep the shared defaults tidy) so the
 * wake-and-query reconstruction (`docs/IOS-IMPLEMENTATION-PLAN.md` §4) reads records with the
 * exact semantics the contract tests pin.
 *
 * SKELETON status: functional persistence, not yet consumed — the F1 orchestrator is their
 * first reader/writer.
 */

private const val KEY_NAMESPACE = "parking_safety_net."

class IosPendingArmRecords(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : PendingArmRecords {

    override fun arm(armId: String, armedAt: Long, trigger: String) {
        write(PendingArm(armId, armedAt, armedAt, trigger, sawDriving = false))
    }

    override fun heartbeat(armId: String, heartbeatAt: Long, sawDriving: Boolean) {
        val prev = read(armId) ?: return
        write(prev.copy(heartbeatAt = heartbeatAt, sawDriving = sawDriving || prev.sawDriving))
    }

    override fun clear(armId: String) {
        defaults.removeObjectForKey(KEY_NAMESPACE + PREFIX + armId)
    }

    override fun scanStale(nowMs: Long, deadMs: Long): List<PendingArm> =
        defaults.dictionaryRepresentation().keys
            .filterIsInstance<String>()
            .filter { it.startsWith(KEY_NAMESPACE + PREFIX) }
            .mapNotNull { read(it.removePrefix(KEY_NAMESPACE + PREFIX)) }
            .filter { nowMs - it.heartbeatAt > deadMs }

    // Same value format as Android: armedAt|heartbeatAt|trigger|sawDriving.
    private fun write(p: PendingArm) {
        defaults.setObject(
            "${p.armedAt}|${p.heartbeatAt}|${p.trigger}|${p.sawDriving}",
            forKey = KEY_NAMESPACE + PREFIX + p.armId,
        )
    }

    private fun read(armId: String): PendingArm? {
        val raw = defaults.stringForKey(KEY_NAMESPACE + PREFIX + armId) ?: return null
        val parts = raw.split("|")
        if (parts.size != 4) return null
        val armedAt = parts[0].toLongOrNull() ?: return null
        val heartbeatAt = parts[1].toLongOrNull() ?: return null
        return PendingArm(armId, armedAt, heartbeatAt, parts[2], parts[3].toBoolean())
    }

    private companion object {
        const val PREFIX = "pending_"
    }
}

class IosExitDeliveryRecords(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : ExitDeliveryRecords {

    override fun record(geofenceId: String, deliveredAtMs: Long) {
        defaults.setObject(deliveredAtMs.toString(), forKey = KEY_NAMESPACE + PREFIX + geofenceId)
    }

    override fun deliveredAt(geofenceId: String): Long? =
        defaults.stringForKey(KEY_NAMESPACE + PREFIX + geofenceId)?.toLongOrNull()?.takeIf { it > 0L }

    override fun hasRecentDelivery(nowMs: Long, maxAgeMs: Long): Boolean =
        defaults.dictionaryRepresentation().keys
            .filterIsInstance<String>()
            .filter { it.startsWith(KEY_NAMESPACE + PREFIX) }
            .any { key ->
                val at = defaults.stringForKey(key)?.toLongOrNull() ?: return@any false
                (nowMs - at) in 0..maxAgeMs
            }

    private companion object {
        const val PREFIX = "exit_delivered_"
    }
}

class IosArrivalResolutionRecord(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : ArrivalResolutionRecord {

    override fun stamp(atMs: Long, latitude: Double, longitude: Double) {
        defaults.setObject(atMs.toString(), forKey = KEY_NAMESPACE + KEY_AT)
        defaults.setObject("$latitude,$longitude", forKey = KEY_NAMESPACE + KEY_POS)
    }

    override fun latest(): ArrivalResolution? {
        val atMs = defaults.stringForKey(KEY_NAMESPACE + KEY_AT)?.toLongOrNull()
            ?.takeIf { it > 0L } ?: return null
        val raw = defaults.stringForKey(KEY_NAMESPACE + KEY_POS) ?: return null
        val parts = raw.split(',')
        val lat = parts.getOrNull(0)?.toDoubleOrNull() ?: return null
        val lon = parts.getOrNull(1)?.toDoubleOrNull() ?: return null
        return ArrivalResolution(atMs = atMs, latitude = lat, longitude = lon)
    }

    private companion object {
        const val KEY_AT = "arrival_resolution_at"
        const val KEY_POS = "arrival_resolution_pos"
    }
}
