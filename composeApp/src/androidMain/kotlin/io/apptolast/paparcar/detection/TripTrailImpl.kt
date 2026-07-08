package io.apptolast.paparcar.detection

import android.content.Context
import io.apptolast.paparcar.domain.detection.TripTrail
import io.apptolast.paparcar.domain.model.GpsPoint
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Disk-backed ring buffer of one-shot fixes. [DET-BREADCRUMBS-001]
 *
 * SharedPreferences on purpose (same reasoning as the position anchor [ANCHOR-PERSIST-001]): the
 * trail's whole value is surviving the OEM process kills between the samples — an in-memory list
 * would be empty exactly when the reconcile needs to look back at the trip. ~60 points ≈ a few KB.
 */
class TripTrailImpl(context: Context) : TripTrail {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val serializer = ListSerializer(GpsPoint.serializer())
    private val lock = Any()

    override fun append(point: GpsPoint) {
        synchronized(lock) {
            val current = readAll()
            // Parallel checks sample the SAME provider fix within the same second (duplicate
            // ticks are the field norm) — an identical timestamp adds no information.
            if (current.lastOrNull()?.timestamp == point.timestamp) return
            val pruned = (current + point)
                .filter { point.timestamp - it.timestamp <= MAX_AGE_MS }
                .takeLast(MAX_POINTS)
            runCatching {
                prefs.edit().putString(KEY_POINTS, Json.encodeToString(serializer, pruned)).apply()
            }.onFailure { e -> PaparcarLogger.w(DIAG, "⚠ trail append failed: ${e.message}") }
        }
    }

    override fun points(): List<GpsPoint> = synchronized(lock) { readAll() }

    private fun readAll(): List<GpsPoint> =
        runCatching {
            prefs.getString(KEY_POINTS, null)
                ?.let { Json.decodeFromString(serializer, it) }
                .orEmpty()
        }.getOrElse {
            PaparcarLogger.w(DIAG, "⚠ trail unreadable — starting fresh: ${it.message}")
            emptyList()
        }

    private companion object {
        const val PREFS_NAME = "trip_trail"
        const val KEY_POINTS = "points"
        const val DIAG = "PARKDIAG/Trail"
        /** Ring size: one-shot cadence is sparse (checks, attempts, pre-arms), so 60 points spans
         *  several trips; bounded so a chatty day cannot grow the prefs file. */
        const val MAX_POINTS = 60
        /** Older than this a breadcrumb describes yesterday's life, not the current parked
         *  session — and the spot-publish freshness gate would refuse anything it dates anyway. */
        const val MAX_AGE_MS = 12 * 60 * 60 * 1_000L
    }
}
