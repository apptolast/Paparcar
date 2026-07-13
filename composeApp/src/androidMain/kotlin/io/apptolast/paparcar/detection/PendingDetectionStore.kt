package io.apptolast.paparcar.detection

import android.content.Context
import androidx.core.content.edit

/**
 * [DET-NEVER-SILENT-001] Durable record of an ARMED detection session, stored in the SAME
 * SharedPreferences the safety-net worker already owns. Survives process death (it is on disk), so
 * the watchdog can recover a park that was silently lost when the OS killed the process mid-trip.
 *
 * Keyed by `armId` (NOT geofenceId — an arm has no saved parking yet). The service writes it at arm,
 * refreshes its heartbeat while the session is alive, and clears it at any terminal. A pending whose
 * heartbeat has gone stale ⇒ the process died before resolving ⇒ the watchdog nudges.
 *
 * Value format: `armedAt|heartbeatAt|trigger|sawDriving`.
 */
object PendingDetectionStore {
    private const val PREFS = "parking_safety_net"
    private const val PREFIX = "pending_"

    data class PendingArm(
        val armId: String,
        val armedAt: Long,
        val heartbeatAt: Long,
        val trigger: String,
        val sawDriving: Boolean,
    )

    /** Persist a new arm (heartbeat = armedAt, sawDriving = false). */
    fun arm(context: Context, armId: String, armedAt: Long, trigger: String) {
        write(context, PendingArm(armId, armedAt, armedAt, trigger, sawDriving = false))
    }

    /** Refresh the heartbeat of a live session; [sawDriving] latches true. No-op if already cleared. */
    fun heartbeat(context: Context, armId: String, heartbeatAt: Long, sawDriving: Boolean) {
        val prev = read(context, armId) ?: return
        write(context, prev.copy(heartbeatAt = heartbeatAt, sawDriving = sawDriving || prev.sawDriving))
    }

    fun clear(context: Context, armId: String) {
        prefs(context).edit { remove(PREFIX + armId) }
    }

    /** Pendings whose heartbeat is older than [deadMs] — presumed orphaned by process death. */
    fun scanStale(context: Context, nowMs: Long, deadMs: Long): List<PendingArm> =
        prefs(context).all.keys
            .filter { it.startsWith(PREFIX) }
            .mapNotNull { read(context, it.removePrefix(PREFIX)) }
            .filter { nowMs - it.heartbeatAt > deadMs }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun write(context: Context, p: PendingArm) {
        prefs(context).edit {
            putString(PREFIX + p.armId, "${p.armedAt}|${p.heartbeatAt}|${p.trigger}|${p.sawDriving}")
        }
    }

    private fun read(context: Context, armId: String): PendingArm? {
        val raw = prefs(context).getString(PREFIX + armId, null) ?: return null
        val parts = raw.split("|")
        if (parts.size != 4) return null
        val armedAt = parts[0].toLongOrNull() ?: return null
        val heartbeatAt = parts[1].toLongOrNull() ?: return null
        return PendingArm(armId, armedAt, heartbeatAt, parts[2], parts[3].toBoolean())
    }
}
