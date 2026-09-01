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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Witness for the one thing [DATA-ROOM-STARTS-AT-VERSION-ONE-001] could not prove by reading.
 *
 * Collapsing [AppDatabase] from `version = 20` to `version = 1` means every pre-release install —
 * our own field-test phones — now holds a file whose version is *higher* than the one the app asks
 * for. Room calls that a downgrade, and it is a path this project had never exercised: the whole
 * migration chain only ever went upwards.
 *
 * Two readings disagreed about what happens on that path. A note written while planning the reset
 * claimed a device with the old database would crash on open unless its data was cleared first.
 * Reading Room's source says the opposite: `fallbackToDestructiveMigration(dropAllTables = true)`
 * sets `requireMigration = false`, and `isMigrationRequired` then returns false for *any* version
 * pair, downgrades included, so the tables are dropped and recreated instead of throwing.
 *
 * Neither reading is evidence, and the cost of being wrong is every tester's first launch crashing.
 * So it gets measured, and the measurement stays.
 *
 * The seeded file is deliberately hostile: a higher `user_version`, a `room_master_table` carrying
 * an identity hash that matches no schema we have ever shipped, and a table that v1 does not
 * declare. Those are the three ways Room can refuse to open a file, and a real v20 database from a
 * phone would present the first two exactly like this.
 *
 * It drives [buildAppDatabase], not a restated builder, so it fails if anyone drops the destructive
 * fallback from the real configuration.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDatabaseDowngradeTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun should_wipeAndReopenAtDeclaredVersion_when_theFileIsLeftFromAHigherPreReleaseVersion() = runTest {
        val name = "downgrade-from-20.db"
        seedPreReleaseDatabase(name, version = 20)

        val db = buildAppDatabase(context, name)
        // Any query forces the open; the assertion that matters is that this line does not throw.
        val vehicle = db.vehicleDao().getActive("nobody")
        db.close()

        assertNull(vehicle, "a wiped database cannot hand back rows")
        assertEquals(PAPARCAR_DB_VERSION, readUserVersion(name), "the file must end up at the version the app declares")
    }

    @Test
    fun should_recreateEverySchemaTable_when_theOldFileIsDropped() = runTest {
        val name = "downgrade-tables.db"
        seedPreReleaseDatabase(name, version = 20)

        val db = buildAppDatabase(context, name)
        db.vehicleDao().getActive("nobody")
        db.close()

        val tables = readTableNames(name)
        val expected = listOf(
            "parking_sessions", "user_profile", "vehicles",
            "cached_spots", "zones", "geocoder_cache",
        )
        expected.forEach { assertTrue(it in tables, "$it must exist after the wipe; found $tables") }
        assertFalse(
            "legacy_leftover" in tables,
            "a table the current schema does not declare must not survive the wipe; found $tables",
        )
    }

    /**
     * The same path from below. A pre-v5 file was the only case the destructive fallback could ever
     * fire for *before* the reset, so this pins that the fallback still covers plain upgrades with
     * no migration registered — the reset deleted every Migration object, so this is now the only
     * thing standing between an old file and a crash.
     */
    @Test
    fun should_wipeAndReopenAtDeclaredVersion_when_theFileIsFromTheNextPreReleaseVersion() = runTest {
        // [DB-A-NEW-COLUMN-NEEDS-ITS-MIGRATION-001] Re-framed when v1->v2 gained a real Migration:
        // there is no longer any UPGRADE gap without one, so the near-boundary case the fallback
        // still owns is the rollback from a build one version ahead (sideloaded newer APK, then
        // back to this one). Same path as the v20 file, pinned at the closest distance.
        val name = "downgrade-from-next.db"
        seedPreReleaseDatabase(name, version = PAPARCAR_DB_VERSION + 1)

        val db = buildAppDatabase(context, name)
        db.vehicleDao().getActive("nobody")
        db.close()

        assertEquals(PAPARCAR_DB_VERSION, readUserVersion(name))
        assertTrue("vehicles" in readTableNames(name))
    }

    // ── seeding / reading, straight through the framework so Room sees a real foreign file ──────

    /**
     * Writes a database Room did not create: the given [version], a `room_master_table` whose
     * identity hash belongs to no schema of ours, and a table the current entities do not declare.
     */
    private fun seedPreReleaseDatabase(name: String, version: Int) {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        file.delete()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { legacy ->
            legacy.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            legacy.execSQL(
                "INSERT INTO room_master_table (id, identity_hash) VALUES (42, ?)",
                arrayOf("an-identity-hash-from-a-schema-we-never-shipped"),
            )
            legacy.execSQL("CREATE TABLE legacy_leftover (id TEXT PRIMARY KEY NOT NULL)")
            legacy.execSQL("INSERT INTO legacy_leftover (id) VALUES ('a-row-that-must-not-survive')")
            legacy.version = version
        }
        assertEquals(version, readUserVersion(name), "the seed itself must be at the version claimed")
    }

    private fun readUserVersion(name: String): Int =
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY,
        ).use { it.version }

    private fun readTableNames(name: String): List<String> =
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY,
        ).use { db ->
            db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0)) }
            }
        }
}
