package com.rndeveloper.paparcar.data.detection

import com.rndeveloper.paparcar.data.datasource.local.room.TripRoutePointDao
import com.rndeveloper.paparcar.data.datasource.local.room.TripRoutePointEntity
import com.rndeveloper.paparcar.domain.detection.DrivingRoute
import com.rndeveloper.paparcar.domain.detection.ports.DrivingRouteStore
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Room-backed record of the current trip's driven route — ONE implementation for both platforms,
 * replacing the SharedPreferences/NSUserDefaults twins. [TRIP-A-LONG-DRIVE-KEEPS-ITS-ORIGIN-001]
 *
 * One row per admitted fix: appending is O(1) per tick (the old blobs rewrote the whole serialized
 * list on every fix), and the route is UNCAPPED — a long drive keeps its origin, which the blobs'
 * 500-point sliding cap made impossible. The accumulation rule stays pure and shared
 * ([DrivingRoute.admit]); this class is persistence only, so the same trip admits the same points
 * on both platforms and in the list-form tests.
 *
 * The last kept point is cached in memory (hydrated from the table on first use, so a reborn
 * process keeps decimating against what it persisted before dying) — admission needs only that one
 * point, never the whole route. Failures log and skip the tick rather than killing the detection
 * stream, matching the stores this one replaces.
 */
class RoomDrivingRouteStore(
    private val dao: TripRoutePointDao,
) : DrivingRouteStore {

    private val mutex = Mutex()

    /** Cache of the newest kept point; [lastHydrated] distinguishes "not loaded yet" from "empty". */
    private var last: GpsPoint? = null
    private var lastHydrated = false

    override suspend fun append(point: GpsPoint) {
        mutex.withLock {
            val previous = hydratedLast()
            if (!lastHydrated) return@withLock // table unreadable this tick — retry on the next fix
            when (DrivingRoute.admit(previous, point)) {
                DrivingRoute.Admission.REJECT, DrivingRoute.Admission.DECIMATE -> Unit
                DrivingRoute.Admission.RESET -> persist(point, wipeFirst = true)
                DrivingRoute.Admission.APPEND -> persist(point, wipeFirst = false)
            }
        }
    }

    override suspend fun points(): List<GpsPoint> = mutex.withLock {
        runCatching { dao.getAllOrdered().map { it.toGpsPoint() } }
            .getOrElse { e ->
                PaparcarLogger.w(DIAG, "⚠ route unreadable — answering empty: ${e.message}")
                emptyList()
            }
    }

    override suspend fun clear() {
        mutex.withLock {
            runCatching { dao.clear() }
                .onSuccess {
                    last = null
                    lastHydrated = true
                }
                .onFailure { e -> PaparcarLogger.w(DIAG, "⚠ route clear failed: ${e.message}") }
        }
    }

    /** The newest kept point, loading it from the table on first use. Null = empty route (or an
     *  unreadable table, which [lastHydrated] keeps marked as not-yet-loaded so the next tick
     *  retries the read instead of silently seeding a second origin). */
    private suspend fun hydratedLast(): GpsPoint? {
        if (!lastHydrated) {
            runCatching { dao.getLast()?.toGpsPoint() }
                .onSuccess {
                    last = it
                    lastHydrated = true
                }
                .onFailure { e -> PaparcarLogger.w(DIAG, "⚠ route last-point read failed: ${e.message}") }
        }
        return last
    }

    private suspend fun persist(point: GpsPoint, wipeFirst: Boolean) {
        runCatching {
            if (wipeFirst) dao.clear()
            dao.insert(TripRoutePointEntity.fromGpsPoint(point))
        }
            .onSuccess { last = point }
            .onFailure { e -> PaparcarLogger.w(DIAG, "⚠ route append failed: ${e.message}") }
    }

    private companion object {
        const val DIAG = "PARKDIAG/Route"
    }
}
