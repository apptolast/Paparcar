package com.rndeveloper.paparcar.data.datasource.local.room

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * The migration chain — the one obvious place every schema change lands, kept alive through the
 * v1 collapse of [DATA-ROOM-RETURNS-TO-VERSION-ONE-001] for exactly this moment. The plumbing was
 * bought with a real field failure — see [DB-A-NEW-COLUMN-NEEDS-ITS-MIGRATION-001], where a column
 * added without a version bump left the safety net unable to read sessions with a parked car being
 * watched.
 *
 * ⛔ One list, both platforms. Every builder also carries
 * `fallbackToDestructiveMigration(dropAllTables = true)`, so a version bump whose migration is
 * missing from a builder does not crash there — it silently WIPES that device. A single shared list
 * is what keeps that from ever being a per-platform accident.
 */

/**
 * v1 → v2: `zones.notifyOnSpot` — the Paparcar Plus zone-alert opt-in. Defaults to 0 (off) for
 * every existing zone; the flag survives downgrades untouched because pausing is an entitlement
 * decision, not a data mutation. [MONEY-001]
 */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `zones` ADD COLUMN `notifyOnSpot` INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v2 → v3: the vehicle learns what it burns — `fuelType`, the two consumption overrides and the
 * environmental label, all nullable (a pre-v3 row simply has not answered yet; the registration
 * edit screen asks). [VEH-FUEL-001]
 */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `vehicles` ADD COLUMN `fuelType` TEXT")
        connection.execSQL("ALTER TABLE `vehicles` ADD COLUMN `consumptionPer100` REAL")
        connection.execSQL("ALTER TABLE `vehicles` ADD COLUMN `consumptionElectricPer100` REAL")
        connection.execSQL("ALTER TABLE `vehicles` ADD COLUMN `energyLabel` TEXT")
    }
}

/**
 * v3 → v4: a cached community spot can be an AREA — `cached_spots.zoneRadiusMeters`, nullable
 * (a pre-v4 row is what it always was: an exact pin). [SPOT-A-ZONE-PARK-FREES-A-ZONE-SPOT-001]
 */
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `cached_spots` ADD COLUMN `zoneRadiusMeters` REAL")
    }
}

val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
