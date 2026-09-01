package com.rndeveloper.paparcar.data.datasource.local.room

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * [DB-A-NEW-COLUMN-NEEDS-ITS-MIGRATION-001] The migration chain, restarted at the v1 baseline of
 * [DATA-ROOM-STARTS-AT-VERSION-ONE-001].
 *
 * Every platform builder registers [ALL_MIGRATIONS] — one list, so a platform cannot silently miss
 * a step. This matters doubly here because every builder also carries
 * `fallbackToDestructiveMigration(dropAllTables = true)` (the v20→v1 downgrade needs it): a version
 * bump whose migration is missing from a builder does not crash there — it silently WIPES that
 * device. The single shared list is what keeps that from ever being a per-platform accident.
 */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    Migration1To2,
)

/**
 * v1 → v2: `parking_sessions` gains `retractedAtMs` — the withdrawal instant of
 * [PARK-A-REFUTED-PIN-LEAVES-THE-HISTORY-001], which added the column to [UserParkingEntity]
 * while the database version stayed 1.
 *
 * What that omission did, measured on the bench (Redmi, 2026-09-01 01:28): the first install of a
 * post-change build on a phone whose database predated it failed every Room read with *"Room cannot
 * verify the data integrity … Expected identity hash: e8bc446…, found: 03390c9c…"* — the safety net
 * could not read sessions, with a parked car being watched. Same version + different schema is the
 * one shape `fallbackToDestructiveMigration` does not cover: it neither migrates nor wipes, it
 * refuses the file on every open, forever.
 */
private object Migration1To2 : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        // Guarded because TWO v1 schemas exist in the field: databases created BEFORE the column
        // (the bench phones, 2026-08-29) and databases created fresh by a post-change build while
        // the version still said 1 — those already carry the column, and a blind ALTER would fail
        // with "duplicate column name" and crash the open. SQLite has no ADD COLUMN IF NOT EXISTS.
        val alreadyThere = connection
            .prepare("SELECT 1 FROM pragma_table_info('parking_sessions') WHERE name = 'retractedAtMs'")
            .use { it.step() }
        if (!alreadyThere) {
            // Nullable on the entity (`retractedAtMs: Long? = null`) → plain INTEGER, no default:
            // an existing row simply has no withdrawal, which is exactly what null means there.
            connection.execSQL("ALTER TABLE `parking_sessions` ADD COLUMN `retractedAtMs` INTEGER")
        }
    }
}
