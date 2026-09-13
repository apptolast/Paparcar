package com.rndeveloper.paparcar.detection

import com.rndeveloper.paparcar.domain.detection.ports.TripTrail
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import platform.Foundation.NSRecursiveLock
import platform.Foundation.NSUserDefaults

/**
 * Disk-backed ring buffer of one-shot fixes — the iOS twin of Android's `TripTrailImpl`.
 * [DET-BREADCRUMBS-001][IOS-A-PARITY-TAIL-001]
 *
 * NSUserDefaults for the same reason Android uses SharedPreferences: the trail's whole value is
 * surviving the process deaths between the samples — an in-memory list would be empty exactly when
 * the reconcile needs to look back at the trip. Ring bounds identical to Android on purpose: a
 * different window here would make the same trip reconcile differently per platform.
 */
class IosTripTrail(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : TripTrail {

    private val serializer = ListSerializer(GpsPoint.serializer())
    private val lock = NSRecursiveLock()

    override fun append(point: GpsPoint) {
        lock.lock()
        try {
            val current = readAll()
            // Parallel checks sample the SAME provider fix within the same second (duplicate
            // ticks are the field norm) — an identical timestamp adds no information.
            if (current.lastOrNull()?.timestamp == point.timestamp) return
            val pruned = (current + point)
                .filter { point.timestamp - it.timestamp <= MAX_AGE_MS }
                .takeLast(MAX_POINTS)
            runCatching {
                defaults.setObject(Json.encodeToString(serializer, pruned), forKey = KEY_POINTS)
            }.onFailure { e -> PaparcarLogger.w(DIAG, "⚠ trail append failed: ${e.message}") }
        } finally {
            lock.unlock()
        }
    }

    override fun points(): List<GpsPoint> {
        lock.lock()
        try {
            return readAll()
        } finally {
            lock.unlock()
        }
    }

    private fun readAll(): List<GpsPoint> =
        runCatching {
            (defaults.objectForKey(KEY_POINTS) as? String)
                ?.let { Json.decodeFromString(serializer, it) }
                .orEmpty()
        }.getOrElse {
            PaparcarLogger.w(DIAG, "⚠ trail unreadable — starting fresh: ${it.message}")
            emptyList()
        }

    private companion object {
        const val KEY_POINTS = "trip_trail.points"
        const val DIAG = "PARKDIAG/Trail"
        /** Ring size: one-shot cadence is sparse (checks, attempts, pre-arms), so 60 points spans
         *  several trips; bounded so a chatty day cannot grow the defaults plist. */
        const val MAX_POINTS = 60
        /** Older than this a breadcrumb describes yesterday's life, not the current parked
         *  session — and the spot-publish freshness gate would refuse anything it dates anyway. */
        const val MAX_AGE_MS = 12 * 60 * 60 * 1_000L
    }
}
