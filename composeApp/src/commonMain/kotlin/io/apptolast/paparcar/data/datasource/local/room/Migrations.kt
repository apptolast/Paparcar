package io.apptolast.paparcar.data.datasource.local.room

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v2 → v3: add poiChecked column to geocoder_cache.
 * Existing rows default to 0 (false) so they are treated as incomplete entries;
 * Phase-2 (Overpass POI fetch) will run again and seal them with poiChecked=1.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE geocoder_cache ADD COLUMN poiChecked INTEGER NOT NULL DEFAULT 0"
        )
        // Entries that already have a placeInfoName were written by a completed
        // Phase-2 call — mark them complete so they survive as valid cache hits.
        connection.execSQL(
            "UPDATE geocoder_cache SET poiChecked = 1 WHERE placeInfoName IS NOT NULL"
        )
    }
}

/**
 * v3 → v4: add vehicle_type + license_plate to the vehicles table.
 * - vehicle_type: enum name, defaulted to 'CAR' for pre-existing rows [BUG-SCOOTER-001a].
 * - license_plate: on-device only (never synced to Firestore), nullable [MAP-MARKERS-REDESIGN-001].
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE vehicles ADD COLUMN vehicleType TEXT NOT NULL DEFAULT 'CAR'"
        )
        connection.execSQL(
            "ALTER TABLE vehicles ADD COLUMN licensePlate TEXT"
        )
    }
}

