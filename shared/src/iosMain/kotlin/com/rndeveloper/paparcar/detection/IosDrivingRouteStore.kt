package com.rndeveloper.paparcar.detection

import com.rndeveloper.paparcar.domain.detection.DrivingRoute
import com.rndeveloper.paparcar.domain.detection.ports.DrivingRouteStore
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import platform.Foundation.NSRecursiveLock
import platform.Foundation.NSUserDefaults

/**
 * Disk-backed record of the current trip's driven route — the iOS twin of Android's
 * `DrivingRouteStoreImpl`. [DET-ROUTE-TRACK-001][IOS-A-PARITY-TAIL-001]
 *
 * The accumulation rule ([DrivingRoute]) is pure, common and unit tested; this class is persistence
 * only, so the same trip decimates to the same polyline on both platforms. Fed by
 * [IosDetectionController]'s tap on the tracking stream (the fixes the coordinator already
 * consumes → zero extra battery); read by `ConfirmParkingUseCase.encodeFreshRoute()` (common), so
 * sessions confirmed on iOS carry their `routePolyline` from this binding alone.
 */
class IosDrivingRouteStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : DrivingRouteStore {

    private val serializer = ListSerializer(GpsPoint.serializer())
    private val lock = NSRecursiveLock()

    override fun append(point: GpsPoint) {
        lock.lock()
        try {
            val current = readAll()
            val next = DrivingRoute.append(current, point)
            if (next === current) return // decimated tick — no disk write
            runCatching {
                defaults.setObject(Json.encodeToString(serializer, next), forKey = KEY_POINTS)
            }.onFailure { e -> PaparcarLogger.w(DIAG, "⚠ route append failed: ${e.message}") }
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

    override fun clear() {
        lock.lock()
        try {
            runCatching { defaults.removeObjectForKey(KEY_POINTS) }
                .onFailure { e -> PaparcarLogger.w(DIAG, "⚠ route clear failed: ${e.message}") }
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
            PaparcarLogger.w(DIAG, "⚠ route unreadable — starting fresh: ${it.message}")
            emptyList()
        }

    private companion object {
        const val KEY_POINTS = "driving_route.points"
        const val DIAG = "PARKDIAG/Route"
    }
}
