package com.rndeveloper.paparcar.data.datasource.local.room

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The v11 watch store against a REAL SQLite file, opened by the production builder.
 * [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001]
 *
 * ⛔ The invariant under test is the one the schema — not the Kotlin — is responsible for: the
 * history is keyed by (station, fuel, DAY), so a user who opens the app eight times in an afternoon
 * ends the day with ONE row. Every verdict above reads two consecutive days and would otherwise be
 * comparing two readings of the same morning.
 *
 * Driven through [buildAppDatabase], the same builder production uses, so the witness cannot drift
 * from the configuration it describes — the pattern [AppDatabaseV1BaselineTest] established.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FuelWatchStoreTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun sample(day: Long, amount: Double, reportedAtMs: Long?) = FuelPriceHistoryEntity(
        stationKey = "minetur|1234",
        fuelType = "DIESEL",
        priceAmount = amount,
        priceCurrency = "EUR",
        reportedAtMs = reportedAtMs,
        observedAtMs = day * 86_400_000L,
        observedDay = day,
    )

    private val followed = FollowedFuelStationEntity(
        stationKey = "minetur|1234",
        source = "minetur",
        id = "1234",
        name = "Repsol Alcalá",
        latitude = 40.48,
        longitude = -3.36,
        followedAtMs = 1_000L,
    )

    @Test
    fun should_keepOneRowPerDay_when_theSameStationIsReadTwiceInOneDay() = runTest {
        val db = buildAppDatabase(context, "fuel-watch-collapse.db")
        val dao = db.fuelWatchDao()

        dao.follow(followed)
        dao.upsertSamples(listOf(sample(day = 100, amount = 1.659, reportedAtMs = 100_000L)))
        // Same day, later reading — the afternoon price, not a second measurement to compare with.
        dao.upsertSamples(listOf(sample(day = 100, amount = 1.649, reportedAtMs = 150_000L)))

        val history = dao.historyFor("minetur|1234", "DIESEL", sinceDay = 0)
        db.close()

        assertEquals(1, history.size, "eight readings in one afternoon are ONE row")
        assertEquals(1.649, history.single().priceAmount, 1e-9)
        assertEquals(150_000L, history.single().reportedAtMs, "the last reading of the day wins")
    }

    @Test
    fun should_returnTheSeriesOldestFirst_when_daysAccumulate() = runTest {
        val db = buildAppDatabase(context, "fuel-watch-series.db")
        val dao = db.fuelWatchDao()

        dao.follow(followed)
        // Inserted out of order on purpose: the ORDER BY is the contract, not the insertion.
        dao.upsertSamples(
            listOf(
                sample(day = 102, amount = 1.619, reportedAtMs = 300_000L),
                sample(day = 100, amount = 1.659, reportedAtMs = 100_000L),
                sample(day = 101, amount = 1.639, reportedAtMs = 200_000L),
            ),
        )

        val history = dao.historyFor("minetur|1234", "DIESEL", sinceDay = 0)
        db.close()

        assertEquals(listOf(100L, 101L, 102L), history.map { it.observedDay })
    }

    @Test
    fun should_honourTheRetentionWindow_when_pruning() = runTest {
        val db = buildAppDatabase(context, "fuel-watch-prune.db")
        val dao = db.fuelWatchDao()

        dao.follow(followed)
        dao.upsertSamples(listOf(sample(day = 70, amount = 1.7, reportedAtMs = 1L), sample(day = 100, amount = 1.6, reportedAtMs = 2L)))
        dao.pruneHistoryBefore(oldestDayKept = 71)

        val history = dao.historyFor("minetur|1234", "DIESEL", sinceDay = 0)
        db.close()

        assertEquals(listOf(100L), history.map { it.observedDay })
    }

    /** Unfollowing withdraws the subject; its series must not outlive it. */
    @Test
    fun should_dropTheSeries_when_theStationIsNoLongerFollowed() = runTest {
        val db = buildAppDatabase(context, "fuel-watch-orphans.db")
        val dao = db.fuelWatchDao()

        dao.follow(followed)
        dao.upsertSamples(listOf(sample(day = 100, amount = 1.659, reportedAtMs = 100_000L)))
        dao.unfollow(followed.stationKey)
        dao.deleteOrphanedHistory()

        val history = dao.historyFor("minetur|1234", "DIESEL", sinceDay = 0)
        val stillFollowed = dao.getFollowed()
        db.close()

        assertTrue(history.isEmpty())
        assertTrue(stillFollowed.isEmpty())
    }
}
