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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * [DATA-ROOM-RETURNS-TO-VERSION-ONE-001] What a real file meets when it opens against the collapsed
 * v1 baseline. Three rows, because the collapse gave the v1 number **two** possible meanings and
 * only one of them opens.
 *
 * | file on the device                       | outcome            |
 * |------------------------------------------|--------------------|
 * | v2, hash `e8bc446…` (the bench build)    | downgrade → wiped  |
 * | v1, hash `e8bc446…` (fresh post-change)  | opens, row survives|
 * | v1, hash `03390c9c…` (the OLD bench v1)  | **refused, forever**|
 *
 * The third row is the point. It is not a bug being pinned as correct — it is the boundary of what
 * `fallbackToDestructiveMigration` can do, measured instead of assumed, because the cost of
 * assuming it was already paid once: [DB-A-NEW-COLUMN-NEEDS-ITS-MIGRATION-001] (Redmi, 2026-09-01
 * 01:28) left the safety net unable to read sessions with a parked car being watched. Room decides
 * by VERSION; same version + different identity hash is invisible to it, so no code change can
 * rescue that file. Clearing the bench phones is therefore a step of the release, not advice.
 *
 * If someone ever makes the third row open, this test fails — and that failure is good news worth
 * reading before deleting the case.
 *
 * Every case drives [buildAppDatabase], the production builder, so a witness cannot drift from the
 * configuration it claims to describe.
 *
 * The seeded DDL is copied verbatim from the actual bench database (`Redmi 2201117TY`, pulled
 * 2026-09-01), and both identity hashes are the real ones read off that phone and off the field
 * error message.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDatabaseV1BaselineTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun should_wipeAndReopenAtV1_when_theFileIsTheV2BenchDatabase() = runTest {
        // The path every field-test phone takes on the release build: a valid v2 file, one version
        // ABOVE what the app now declares. Distinct from AppDatabaseDowngradeTest's hostile seed —
        // here the schema is legitimate and only the number is ahead, which is the real case.
        val name = "baseline-from-v2.db"
        seedBenchDatabase(name, version = 2, withRetractedColumn = true, identityHash = CURRENT_IDENTITY_HASH)

        val db = buildAppDatabase(context, name)
        val rows = db.parkingSessionDao().getByUser("user-bench")
        db.close()

        assertTrue(rows.isEmpty(), "a downgrade wipes: the v2 row must not come back")
        assertEquals(PAPARCAR_DB_VERSION, readUserVersion(name), "the file must end up at the declared version")
    }

    @Test
    fun should_openAndKeepTheRow_when_theFileIsV1WithTheCurrentSchema() = runTest {
        // Same version, same identity hash: nothing to migrate, nothing to wipe. This is also what
        // every fresh install produces, so it doubles as the proof that `1.json` still describes
        // the entities — a drift there would change the hash and turn this into the third case.
        val name = "baseline-v1-current.db"
        seedBenchDatabase(name, version = 1, withRetractedColumn = true, identityHash = CURRENT_IDENTITY_HASH)

        val db = buildAppDatabase(context, name)
        val rows = db.parkingSessionDao().getByUser("user-bench")
        db.close()

        assertEquals(1, rows.size, "an untouched schema must not cost the user their parked session")
        assertEquals("pin-1", rows.single().id)
        assertEquals(PAPARCAR_DB_VERSION, readUserVersion(name))
    }

    @Test
    fun should_refuseTheFile_when_theFileIsV1FromBeforeTheRetractedColumn() = runTest {
        // The uncoverable shape, and the reason `pm clear` is part of the release.
        val name = "baseline-v1-stale.db"
        seedBenchDatabase(name, version = 1, withRetractedColumn = false, identityHash = STALE_IDENTITY_HASH)

        val db = buildAppDatabase(context, name)
        val failure = assertFailsWith<IllegalStateException> {
            db.parkingSessionDao().getByUser("user-bench")
        }
        db.close()

        assertTrue(
            "identity hash" in failure.message.orEmpty(),
            "the refusal must be the integrity check, not some other failure; was: ${failure.message}",
        )
    }

    // ── seeding: the REAL bench schema, verbatim ────────────────────────────────────────────────

    /**
     * Writes a file Room did not create, at the given [version], carrying [identityHash] in its
     * `room_master_table` and one parked session. Only `parking_sessions` differs between the two
     * v1 variants ([withRetractedColumn]); the other tables were identical on the bench.
     */
    private fun seedBenchDatabase(
        name: String,
        version: Int,
        withRetractedColumn: Boolean,
        identityHash: String,
    ) {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        file.delete()
        val retracted = if (withRetractedColumn) ", `retractedAtMs` INTEGER" else ""
        SQLiteDatabase.openOrCreateDatabase(file, null).use { seed ->
            seed.execSQL(
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
            seed.execSQL(
                "CREATE TABLE `user_profile` (`userId` TEXT NOT NULL, `email` TEXT, `displayName` TEXT, " +
                    "`photoUrl` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                    "`defaultVehicleId` TEXT, PRIMARY KEY(`userId`))",
            )
            seed.execSQL(
                "CREATE TABLE `vehicles` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `name` TEXT, " +
                    "`brand` TEXT, `model` TEXT, `sizeCategory` TEXT NOT NULL, `carbodyType` TEXT, " +
                    "`vehicleType` TEXT NOT NULL, `bluetoothDeviceId` TEXT, " +
                    "`showBrandModelOnSpot` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, " +
                    "`licensePlate` TEXT, `color` TEXT, `updatedAt` INTEGER NOT NULL, " +
                    "`pendingSync` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            seed.execSQL(
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
            seed.execSQL(
                "CREATE TABLE `zones` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "`lat` REAL NOT NULL, `lon` REAL NOT NULL, `iconKey` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `radiusMeters` REAL NOT NULL, " +
                    "`isPrivate` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                    "`pendingSync` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            )
            seed.execSQL(
                "CREATE TABLE `geocoder_cache` (`locationKey` TEXT NOT NULL, `addressStreet` TEXT, " +
                    "`addressCity` TEXT, `addressRegion` TEXT, `addressCountry` TEXT, " +
                    "`addressCountryCode` TEXT, `placeInfoName` TEXT, `placeInfoCategory` TEXT, " +
                    "`cachedAt` INTEGER NOT NULL, `poiChecked` INTEGER NOT NULL, PRIMARY KEY(`locationKey`))",
            )
            seed.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            seed.execSQL(
                "INSERT INTO room_master_table (id, identity_hash) VALUES (42, ?)",
                arrayOf(identityHash),
            )
            seed.execSQL(
                "INSERT INTO parking_sessions (id, userId, latitude, longitude, accuracy, timestamp, " +
                    "isActive, routeSnapped, publishedSpot, updatedAt, pendingSync) " +
                    "VALUES ('pin-1', 'user-bench', 36.6084, -6.2781, 5.0, 1788204119405, 1, 0, 0, 1788204119405, 0)",
            )
            seed.version = version
        }
        assertEquals(version, readUserVersion(name), "the seed itself must be at the version claimed")
    }

    private fun readUserVersion(name: String): Int =
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY,
        ).use { it.version }

    private companion object {
        /** What the current entities hash to — identical in `1.json` and the retired `2.json`. */
        const val CURRENT_IDENTITY_HASH = "e8bc446bd23663f9f0febbeda06c375e"

        /** What the bench databases born on 2026-08-29 carry, from before `retractedAtMs`. */
        const val STALE_IDENTITY_HASH = "03390c9c44e62b92f2c0e85cf92e5eaa"
    }
}
