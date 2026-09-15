package com.rndeveloper.paparcar.data.datasource.local.room

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The v11 zone-watch memory against a REAL SQLite file, opened by the production builder.
 * [ZONE-AN-ALERT-NOBODY-IS-AWAKE-TO-RECEIVE-001]
 *
 * ⛔ The point is that it SURVIVES: the whole ticket is that the "already announced" set stopped
 * being a `mutableSetOf` inside a stream. A test that only exercised the Kotlin would pass over the
 * bug, because the bug was the storage.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ZoneWatchStoreTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun alerted(zoneId: String, spotId: String, expiresAt: Long) = ZoneAlertedSpotEntity(
        zoneId = zoneId,
        spotId = spotId,
        alertedAtMs = 1_000L,
        spotExpiresAt = expiresAt,
    )

    @Test
    fun should_rememberWhatWasAnnounced_when_theProcessIsGone() = runTest {
        val name = "zone-watch-survives.db"
        val first = buildAppDatabase(context, name)
        first.zoneWatchDao().recordAlerted(listOf(alerted("z-1", "s-1", expiresAt = 9_999_999L)))
        first.zoneWatchDao().markBaseline(ZoneWatchBaselineEntity("z-1", baselineAtMs = 1_000L))
        first.close()

        // A new process opening the same file — the case the in-memory set could never survive.
        val second = buildAppDatabase(context, name)
        val ids = second.zoneWatchDao().alertedSpotIds("z-1")
        val baseline = second.zoneWatchDao().baselineFor("z-1")
        second.close()

        assertEquals(listOf("s-1"), ids)
        assertTrue(baseline != null, "the zone must still count as looked at")
    }

    /** One free space can sit inside two overlapping zones, and each is told separately. */
    @Test
    fun should_keepOneRowPerZone_when_twoZonesShareASpot() = runTest {
        val db = buildAppDatabase(context, "zone-watch-overlap.db")
        val dao = db.zoneWatchDao()

        dao.recordAlerted(
            listOf(
                alerted("z-1", "shared", expiresAt = 9_999_999L),
                alerted("z-2", "shared", expiresAt = 9_999_999L),
            ),
        )

        val forOne = dao.alertedSpotIds("z-1")
        val forTwo = dao.alertedSpotIds("z-2")
        db.close()

        assertEquals(listOf("shared"), forOne)
        assertEquals(listOf("shared"), forTwo)
    }

    /**
     * ⛔ Pruned by the SPOT's death, not by the age of the notification. A row dropped while its
     * spot is still free would let the same space be announced twice.
     */
    @Test
    fun should_dropRowsOfDeadSpotsOnly_when_pruning() = runTest {
        val db = buildAppDatabase(context, "zone-watch-prune.db")
        val dao = db.zoneWatchDao()

        dao.recordAlerted(
            listOf(
                alerted("z-1", "dead", expiresAt = 500L),
                alerted("z-1", "alive", expiresAt = 9_999_999L),
                // 0 = the spot carried no TTL; the expiry sweep must not touch it.
                alerted("z-1", "no-ttl", expiresAt = 0L),
            ),
        )
        dao.pruneExpired(nowMs = 1_000L)

        val ids = dao.alertedSpotIds("z-1")
        db.close()

        assertEquals(setOf("alive", "no-ttl"), ids.toSet())
    }

    /** A zone the user deleted takes its memory with it — closed in one place, not at every site. */
    @Test
    fun should_dropRowsOfZonesThatNoLongerExist_when_sweepingOrphans() = runTest {
        val db = buildAppDatabase(context, "zone-watch-orphans.db")
        val dao = db.zoneWatchDao()

        dao.recordAlerted(listOf(alerted("ghost-zone", "s-1", expiresAt = 9_999_999L)))
        dao.markBaseline(ZoneWatchBaselineEntity("ghost-zone", baselineAtMs = 1_000L))
        dao.deleteOrphanedAlerts()
        dao.deleteOrphanedBaselines()

        val ids = dao.alertedSpotIds("ghost-zone")
        val baseline = dao.baselineFor("ghost-zone")
        db.close()

        assertTrue(ids.isEmpty())
        assertFalse(baseline != null)
    }
}
