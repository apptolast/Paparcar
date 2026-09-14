package com.rndeveloper.paparcar.data.detection

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.rndeveloper.paparcar.data.datasource.local.room.AppDatabase
import com.rndeveloper.paparcar.data.datasource.local.room.buildAppDatabase
import com.rndeveloper.paparcar.domain.detection.DrivingRoute
import com.rndeveloper.paparcar.domain.model.GpsPoint
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Room-backed route store against a REAL database file (the production [buildAppDatabase]),
 * pinning what the blob stores it replaced could not do: an uncapped route, O(1) appends, and a
 * reborn process that keeps decimating against what it persisted before dying.
 * [TRIP-A-LONG-DRIVE-KEEPS-ITS-ORIGIN-001]
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomDrivingRouteStoreTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val openDbs = mutableListOf<AppDatabase>()

    @After
    fun closeAll() {
        openDbs.forEach { runCatching { it.close() } }
    }

    private fun db(name: String): AppDatabase = buildAppDatabase(context, name).also { openDbs += it }

    /** ~111 m per 0.001° of latitude — every step clears the 12 m decimation floor. */
    private fun p(lat: Double, ts: Long) = GpsPoint(lat, -3.0, 5f, ts, 10f, provider = "gps")

    @Test
    fun should_persistAdmittedPointsInDrivingOrder_when_appending() = runTest {
        val store = RoomDrivingRouteStore(db("route-order.db").tripRoutePointDao())

        store.append(p(40.000, 1_000L))
        store.append(p(40.001, 6_000L))
        store.append(p(40.002, 11_000L))

        assertEquals(listOf(40.000, 40.001, 40.002), store.points().map { it.latitude })
    }

    @Test
    fun should_skipTheWrite_when_theFixDecimatesOrIsHopeless() = runTest {
        val store = RoomDrivingRouteStore(db("route-decimate.db").tripRoutePointDao())

        store.append(p(40.000, 1_000L))
        store.append(p(40.00001, 6_000L)) // ~1 m — decimated
        store.append(GpsPoint(40.001, -3.0, DrivingRoute.HOPELESS_ACCURACY_METERS + 1f, 11_000L, 0f)) // rejected

        assertEquals(1, store.points().size, "neither the decimated nor the hopeless fix may land a row")
    }

    @Test
    fun should_wipeTheTableAndReseed_when_aNewTripGapArrives() = runTest {
        val store = RoomDrivingRouteStore(db("route-gap.db").tripRoutePointDao())

        store.append(p(40.000, 1_000L))
        store.append(p(40.001, 6_000L))
        store.append(p(41.000, 6_000L + DrivingRoute.NEW_TRIP_GAP_MS + 1))

        val points = store.points()
        assertEquals(1, points.size, "a new trip must not inherit the previous one's tail")
        assertEquals(41.000, points.single().latitude, 1e-9)
    }

    @Test
    fun should_answerEmpty_when_cleared() = runTest {
        val store = RoomDrivingRouteStore(db("route-clear.db").tripRoutePointDao())

        store.append(p(40.000, 1_000L))
        store.clear()

        assertTrue(store.points().isEmpty())
        // And the next trip seeds cleanly from the cleared state.
        store.append(p(42.000, 2_000L))
        assertEquals(42.000, store.points().single().latitude, 1e-9)
    }

    @Test
    fun should_keepTheOriginAndDecimateAgainstThePersistedLast_when_aNewInstanceReopens() = runTest {
        // The rebirth the store exists for: the process died mid-trip, a fresh instance opens the
        // same file and must CONTINUE the route — origin intact, decimation anchored on the last
        // point the dead process persisted, not restarted from scratch.
        val name = "route-rebirth.db"
        val before = RoomDrivingRouteStore(db(name).tripRoutePointDao())
        before.append(p(40.000, 1_000L))
        before.append(p(40.001, 6_000L))

        val reborn = RoomDrivingRouteStore(db(name).tripRoutePointDao())
        reborn.append(p(40.00101, 11_000L)) // ~1 m past the persisted last — must decimate
        assertEquals(2, reborn.points().size, "decimation must anchor on the persisted last point")

        reborn.append(p(40.002, 16_000L))
        val route = reborn.points()
        assertEquals(3, route.size)
        assertEquals(40.000, route.first().latitude, 1e-9, "the origin must survive the rebirth")
    }

    @Test
    fun should_keepEveryPointOfALongDrive_when_wellPastTheRetiredCap() = runTest {
        val store = RoomDrivingRouteStore(db("route-long.db").tripRoutePointDao())

        // 600 admitted fixes — beyond the old 500-point sliding cap that dropped the origin.
        for (i in 0 until 600) {
            store.append(p(40.0 + i * 0.001, 1_000L + i * 5_000L))
        }

        val route = store.points()
        assertEquals(600, route.size)
        assertEquals(40.0, route.first().latitude, 1e-9, "a long drive must keep its origin")
    }
}
