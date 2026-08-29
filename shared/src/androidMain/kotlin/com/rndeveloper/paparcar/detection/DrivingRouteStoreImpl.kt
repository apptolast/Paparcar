package com.rndeveloper.paparcar.detection

import android.content.Context
import com.rndeveloper.paparcar.domain.detection.DrivingRoute
import com.rndeveloper.paparcar.domain.detection.ports.DrivingRouteStore
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Disk-backed record of the current trip's driven route. [DET-ROUTE-TRACK-001]
 *
 * SharedPreferences on purpose (same reasoning as [TripTrailImpl] and the position anchor): the
 * route's whole value is surviving the app leaving the foreground or an OEM process kill — an
 * in-memory list would be empty exactly when the trip controller restarts and needs to redraw the
 * route. ~500 decimated points ≈ a few KB. The accumulation rule ([DrivingRoute]) is pure and unit
 * tested; this class is persistence only.
 */
class DrivingRouteStoreImpl(context: Context) : DrivingRouteStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val serializer = ListSerializer(GpsPoint.serializer())
    private val lock = Any()

    override fun append(point: GpsPoint) {
        synchronized(lock) {
            val current = readAll()
            val next = DrivingRoute.append(current, point)
            if (next === current) return // decimated tick — no disk write
            runCatching {
                prefs.edit().putString(KEY_POINTS, Json.encodeToString(serializer, next)).apply()
            }.onFailure { e -> PaparcarLogger.w(DIAG, "⚠ route append failed: ${e.message}") }
        }
    }

    override fun points(): List<GpsPoint> = synchronized(lock) { readAll() }

    override fun clear() {
        synchronized(lock) {
            runCatching { prefs.edit().remove(KEY_POINTS).apply() }
                .onFailure { e -> PaparcarLogger.w(DIAG, "⚠ route clear failed: ${e.message}") }
        }
    }

    private fun readAll(): List<GpsPoint> =
        runCatching {
            prefs.getString(KEY_POINTS, null)
                ?.let { Json.decodeFromString(serializer, it) }
                .orEmpty()
        }.getOrElse {
            PaparcarLogger.w(DIAG, "⚠ route unreadable — starting fresh: ${it.message}")
            emptyList()
        }

    private companion object {
        const val PREFS_NAME = "driving_route"
        const val KEY_POINTS = "points"
        const val DIAG = "PARKDIAG/Route"
    }
}
