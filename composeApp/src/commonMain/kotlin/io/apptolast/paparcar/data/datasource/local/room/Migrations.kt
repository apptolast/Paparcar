package io.apptolast.paparcar.data.datasource.local.room

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Schema migrations for [AppDatabase].
 *
 * Add new entries chronologically. Each migration must be referenced from the
 * platform DI module (`androidPlatformModule`, `iosPlatformModule`) via
 * `.addMigrations(...)` — Room throws at startup otherwise. [DB-001]
 */

/**
 * v3 → v4: add `vehicleType TEXT NOT NULL DEFAULT 'CAR'` to `vehicles`.
 *
 * Pre-existing rows default to `CAR`. New rows get the user's explicit choice
 * from the registration screen. [BUG-SCOOTER-001a]
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE vehicles ADD COLUMN vehicleType TEXT NOT NULL DEFAULT 'CAR'"
        )
    }
}
