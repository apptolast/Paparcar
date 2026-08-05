package io.apptolast.paparcar.detection

import android.content.Context
import androidx.core.content.edit

/**
 * [DET-RESIDENT-FGS-001 · F2] Durable "the service is resident in SENTRY" stamp, in the SAME
 * SharedPreferences the safety-net worker already owns (like [PendingDetectionStore]). The service
 * writes it on enterSentry and clears it on every DELIBERATE exit (SENTRY→ACTIVE wake, idle
 * teardown). A stamp found while the service is NOT resident is therefore evidence the OS killed
 * the resident watcher — `resolveSentryKillVerdict` (commonMain, pure) turns that into the
 * `sentry killed` telemetry on both detection lanes (worker tick + service re-arm).
 *
 * Single slot on purpose: residency watches "the parked state", not one session — a repark simply
 * re-stamps. [enteredElapsedMs] pairs the wall clock with `elapsedRealtime` so a reboot (elapsed
 * went backwards) explains a dead residency innocently, same trick as the worker's heartbeat.
 *
 * Value format: `geofenceId|enteredAt|enteredElapsed`.
 */
object SentryResidenceStore {
    private const val PREFS = "parking_safety_net"
    private const val KEY = "sentry_residence"

    /** sessionId stand-in when the parked session has no geofence — mirrors the worker's fallback. */
    const val FALLBACK_SESSION = "system"

    data class Residency(
        val geofenceId: String,
        val enteredAtMs: Long,
        val enteredElapsedMs: Long,
    )

    /** Persist the residency at enterSentry; returns what was stored (with the fallback applied)
     *  so the caller logs the same sessionId the kill detector will later report. */
    fun stamp(context: Context, geofenceId: String?, enteredAtMs: Long, enteredElapsedMs: Long): Residency {
        val residency = Residency(geofenceId ?: FALLBACK_SESSION, enteredAtMs, enteredElapsedMs)
        prefs(context).edit {
            putString(KEY, "${residency.geofenceId}|${residency.enteredAtMs}|${residency.enteredElapsedMs}")
        }
        return residency
    }

    fun read(context: Context): Residency? {
        val raw = prefs(context).getString(KEY, null) ?: return null
        val parts = raw.split("|")
        if (parts.size != 3) return null
        val enteredAt = parts[1].toLongOrNull() ?: return null
        val enteredElapsed = parts[2].toLongOrNull() ?: return null
        return Residency(parts[0], enteredAt, enteredElapsed)
    }

    fun clear(context: Context) {
        prefs(context).edit { remove(KEY) }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
