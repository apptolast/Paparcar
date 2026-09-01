package com.rndeveloper.paparcar.data.datasource.local.room

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [DB-A-NEW-COLUMN-NEEDS-ITS-MIGRATION-001] Witness for the v1→v2 migration, measured against the
 * TABLE — not against a fake — because the defect it answers was invisible from above: every
 * repository read failed identically, so no higher layer could tell "no rows" from "cannot open".
 *
 * The seeded v1 file is not an approximation: its `parking_sessions` DDL is copied from the actual
 * bench database (`Redmi 2201117TY`, pulled 2026-09-01) and its `room_master_table` carries the
 * REAL pre-change identity hash — the very pair that made the first post-change install fail every
 * read with *"Room cannot verify the data integrity … Expected identity hash: e8bc446…, found:
 * 03390c9c…"* (2026-09-01 01:28, safety net blind with a parked car being watched).
 *
 * Drives [buildAppDatabase] — the production builder — so it also proves the registered migration
 * WINS over `fallbackToDestructiveMigration`: the row must SURVIVE, not be wiped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDatabaseMigrationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun should_migrateAndKeepTheRow_when_theFileIsV1_withoutTheColumn() = runTest {
        val name = "migrate-v1-old.db"
        seedRealV1Database(name, withRetractedColumn = false, identityHash = OLD_V1_IDENTITY_HASH)

        val db = buildAppDatabase(context, name)
        val rows = db.parkingSessionDao().getByUser("user-bench")
        db.close()

        assertEquals(1, rows.size, "the parked session must SURVIVE the migration, not be wiped")
        assertEquals("pin-1", rows.single().id)
        assertNull(rows.single().retractedAtMs, "an existing row has no withdrawal — that is what null means")
        assertEquals(PAPARCAR_DB_VERSION, readUserVersion(name))
    }

    @Test
    fun should_migrateWithoutCrashing_when_theV1FileAlreadyCarriesTheColumn() = runTest {
        // The OTHER v1 in the field: a database created FRESH by a post-change build while the
        // version still said 1 — the column is already there, and a blind ALTER would die with
        // "duplicate column name" on open. The guard makes the migration idempotent.
        val name = "migrate-v1-new.db"
        seedRealV1Database(name, withRetractedColumn = true, identityHash = NEW_V1_IDENTITY_HASH)

        val db = buildAppDatabase(context, name)
        val rows = db.parkingSessionDao().getByUser("user-bench")
        db.close()

        assertEquals(1, rows.size, "the session survives here too")
        assertEquals(PAPARCAR_DB_VERSION, readUserVersion(name))
    }

    // ── seeding: the REAL v1 schema, verbatim from the bench database ───────────────────────────

    /**
     * DDL copied from the Redmi's `paparcar.db` (`sqlite_master`, 2026-09-01). Only
     * `parking_sessions` differs between the two v1 variants; the other tables were identical.
     */
    private fun seedRealV1Database(name: String, withRetractedColumn: Boolean, identityHash: String) {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        file.delete()
        val retracted = if (withRetractedColumn) ", `retractedAtMs` INTEGER" else ""
        SQLiteDatabase.openOrCreateDatabase(file, null).use { v1 ->
            v1.execSQL(
                "CREATE TABLE `parking_sessions` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, " +
                    "`vehicleId` TEXT, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, " +
                    "`accuracy` REAL NOT NULL, `timestamp` INTEGER NOT NULL, `spotId` TEXT, " +
                    "`geofenceId` TEXT, `isActive` INTEGER NOT NULL, `addressStreet` TEXT, " +
                    "`addressCity` TEXT, `addressRegion` TEXT, `addressCountry` TEXT, " +
                    "`addressCountryCode` TEXT, `placeInfoName` TEXT, `placeInfoCategory` TEXT, " +
                    "`detectionReliability` REAL, `sizeCategory` TEXT, `carbodyType` TEXT, " +
                    "`spotType` TEXT, `privateZoneId` TEXT, `tripMaxSpeedMps` REAL, " +
                    "`provisionalDepartureAtMs` INTEGER, `armEvidence` TEXT, `detectionPath` TEXT, " +
                    "`zoneRadiusMeters` REAL, `routePolyline` TEXT, `routeSnapped` INTEGER NOT NULL, " +
                    "`routeInferredSpans` TEXT, `routeInferredResolution` TEXT, " +
                    "`routeDistanceMeters` REAL, `endedAtMs` INTEGER, `publishedSpot` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, `pendingSync` INTEGER NOT NULL$retracted, " +
                    "PRIMARY KEY(`id`))",
            )
            v1.execSQL(
                "CREATE TABLE `user_profile` (`userId` TEXT NOT NULL, `email` TEXT, `displayName` TEXT, " +
                    "`photoUrl` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                    "`defaultVehicleId` TEXT, PRIMARY KEY(`userId`))",
            )
            v1.execSQL(
                "CREATE TABLE `vehicles` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `name` TEXT, " +
                    "`brand` TEXT, `model` TEXT, `sizeCategory` TEXT NOT NULL, `carbodyType` TEXT, " +
                    "`vehicleType` TEXT NOT NULL, `bluetoothDeviceId` TEXT, " +
                    "`showBrandModelOnSpot` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, " +
                    "`licensePlate` TEXT, `color` TEXT, `updatedAt` INTEGER NOT NULL, " +
                    "`pendingSync` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            v1.execSQL(
                "CREATE TABLE `cached_spots` (`id` TEXT NOT NULL, `latitude` REAL NOT NULL, " +
                    "`longitude` REAL NOT NULL, `accuracy` REAL NOT NULL, `reportedAt` INTEGER NOT NULL, " +
                    "`reportedBy` TEXT NOT NULL, `speed` REAL NOT NULL, `addressStreet` TEXT, " +
                    "`addressCity` TEXT, `addressRegion` TEXT, `addressCountry` TEXT, " +
                    "`addressCountryCode` TEXT, `geohash` TEXT, `placeInfoName` TEXT, " +
                    "`placeInfoCategory` TEXT, `type` TEXT NOT NULL, `confidence` REAL NOT NULL, " +
                    "`sizeCategory` TEXT, `carbodyType` TEXT, `enRouteCount` INTEGER NOT NULL, " +
                    "`expiresAt` INTEGER NOT NULL, `acceptCount` INTEGER NOT NULL, " +
                    "`rejectCount` INTEGER NOT NULL, `status` TEXT NOT NULL, PRIMARY KEY(`id`))",
            )
            v1.execSQL(
                "CREATE TABLE `zones` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "`lat` REAL NOT NULL, `lon` REAL NOT NULL, `iconKey` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `radiusMeters` REAL NOT NULL, " +
                    "`isPrivate` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                    "`pendingSync` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            v1.execSQL(
                "CREATE TABLE `geocoder_cache` (`locationKey` TEXT NOT NULL, `addressStreet` TEXT, " +
                    "`addressCity` TEXT, `addressRegion` TEXT, `addressCountry` TEXT, " +
                    "`addressCountryCode` TEXT, `placeInfoName` TEXT, `placeInfoCategory` TEXT, " +
                    "`cachedAt` INTEGER NOT NULL, `poiChecked` INTEGER NOT NULL, PRIMARY KEY(`locationKey`))",
            )
            v1.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            v1.execSQL(
                "INSERT INTO room_master_table (id, identity_hash) VALUES (42, ?)",
                arrayOf(identityHash),
            )
            v1.execSQL(
                "INSERT INTO parking_sessions (id, userId, latitude, longitude, accuracy, timestamp, " +
                    "isActive, routeSnapped, publishedSpot, updatedAt, pendingSync) " +
                    "VALUES ('pin-1', 'user-bench', 36.6084, -6.2781, 5.0, 1788204119405, 1, 0, 0, 1788204119405, 0)",
            )
            v1.version = 1
        }
    }

    private fun readUserVersion(name: String): Int =
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY,
        ).use { it.version }

    private companion object {
        /** What the bench databases actually carry (read from the Redmi's `room_master_table`). */
        const val OLD_V1_IDENTITY_HASH = "03390c9c44e62b92f2c0e85cf92e5eaa"

        /** What a post-change build stamps on a fresh v1 file (from the field error message). */
        const val NEW_V1_IDENTITY_HASH = "e8bc446bd23663f9f0febbeda06c375e"
    }
}
