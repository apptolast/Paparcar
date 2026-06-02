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

/**
 * v4 → v5: add `addressCountryCode TEXT` to `cached_spots`.
 *
 * Backfilled from the geocoder on next Firestore sync. Existing cached rows will have
 * NULL until the bounding-box listener replaces them with fresh data. [GEO-INDEX-001]
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE cached_spots ADD COLUMN addressCountryCode TEXT"
        )
    }
}

/**
 * v5 → v6: add `geohash TEXT` to `cached_spots`.
 *
 * Backfilled from Firestore on next bbox sync. Existing rows will have NULL
 * until replaced. [GEO-INDEX-001]
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE cached_spots ADD COLUMN geohash TEXT"
        )
    }
}

/**
 * v6 → v7: add `sizeCategory TEXT` to `parking_sessions`.
 *
 * Stores the vehicle's size at confirmation time so [DepartureDetectionWorker] can
 * forward it to the published [Spot], letting nearby drivers see whether the space fits
 * their vehicle. Existing rows default to NULL (unknown size). [COMPAT-ROW-001]
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE parking_sessions ADD COLUMN sizeCategory TEXT"
        )
    }
}

/**
 * v7 → v8: add `radiusMeters REAL NOT NULL DEFAULT 150` to `zones`.
 *
 * Pre-existing zones default to 150 m. The user can now adjust the radius
 * (50–300 m) when creating or editing a zone. [ZONE-RADIUS-001]
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE zones ADD COLUMN radiusMeters REAL NOT NULL DEFAULT 150"
        )
    }
}

/**
 * v8 → v9: private zone support.
 *
 * - `zones.isPrivate INTEGER NOT NULL DEFAULT 0` — marks the zone as a private
 *   garage/parking whose spot should never be published to the community.
 * - `parking_sessions.privateZoneId TEXT` — links a session to its private zone
 *   so [DepartureDetectionWorker] can skip spot publication on departure.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE zones ADD COLUMN isPrivate INTEGER NOT NULL DEFAULT 0"
        )
        connection.execSQL(
            "ALTER TABLE parking_sessions ADD COLUMN privateZoneId TEXT"
        )
    }
}
