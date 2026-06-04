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
