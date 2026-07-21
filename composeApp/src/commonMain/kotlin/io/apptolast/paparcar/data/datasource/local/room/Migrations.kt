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

/**
 * [AUDIT-DATA-001 M7] v5 → v6: IDENTITY migration (no schema change — verified against the
 * exported schemas: identical tables, columns and indices). The version was bumped without a
 * structural change; Room still demands a Migration object for the version delta. Registering it
 * completes the migration chain from the first exported schema (v5) upward, so a v5+ database no
 * longer falls into the destructive fallback on upgrade.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) { /* no-op: identical schema */ }
}

/**
 * v6 → v7: add color to the vehicles table.
 * - color: [VehicleColor] enum name, nullable — null = undefined → default green icon.
 *   Non-destructive so on-device-only fields (bluetoothDeviceId, licensePlate) survive. [VEH-COLOR-001]
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE vehicles ADD COLUMN color TEXT"
        )
    }
}

/**
 * [AUDIT-DATA-001 M7] v7 → v8: IDENTITY migration (no schema change — verified against the
 * exported schemas). The stale @Database comment claimed v8 added `addressCountryCode`, but that
 * column was actually introduced at v7 (MIGRATION_6_7's era). Empty by design; see MIGRATION_5_6.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(connection: SQLiteConnection) { /* no-op: identical schema */ }
}

/**
 * v8 → v9: add updatedAt + pendingSync to the vehicles table for inbound sync reconciliation.
 * Non-destructive (ADD COLUMN) so the on-device cache — including offline edits not yet synced —
 * survives the upgrade; that preservation is the whole point of the reconcile. Pre-existing rows
 * default to updatedAt=0 / pendingSync=0 (clean, remote-authoritative). [SYNC-RECONCILE-001]
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE vehicles ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0"
        )
        connection.execSQL(
            "ALTER TABLE vehicles ADD COLUMN pendingSync INTEGER NOT NULL DEFAULT 0"
        )
    }
}

/**
 * v9 → v10: add updatedAt + pendingSync to the zones table for inbound sync reconciliation — same
 * non-destructive treatment as vehicles (v8→v9). Preserves the cache incl. offline zone edits.
 * [SYNC-RECONCILE-001]
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE zones ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0"
        )
        connection.execSQL(
            "ALTER TABLE zones ADD COLUMN pendingSync INTEGER NOT NULL DEFAULT 0"
        )
    }
}

/**
 * v10 → v11: add tripMaxSpeedMps + armEvidence to parking_sessions — local-only detection
 * provenance (max session speed + arm evidence label) feeding the repark-plausibility guard.
 * Nullable ADD COLUMN, non-destructive; existing rows read as null (unknown provenance),
 * which the guard treats as "no evidence available" (permissive). [DET-SOLID-001]
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE parking_sessions ADD COLUMN tripMaxSpeedMps REAL"
        )
        connection.execSQL(
            "ALTER TABLE parking_sessions ADD COLUMN armEvidence TEXT"
        )
    }
}

/**
 * v11 → v12: add updatedAt + pendingSync to parking_sessions — the Last-Write-Wins sync columns
 * (same shape already on vehicles/zones). Mirrors the inbound reconcile so a stale remote snapshot
 * can no longer resurrect an ended session (field incident 2026-07-05). Additive, non-destructive;
 * existing rows read updatedAt=0 / pendingSync=false (already-synced, remote can win).
 * [SYNC-RECONCILE-USERPARKING-001]
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE parking_sessions ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0"
        )
        connection.execSQL(
            "ALTER TABLE parking_sessions ADD COLUMN pendingSync INTEGER NOT NULL DEFAULT 0"
        )
    }
}

/**
 * v12 → v13: add detectionPath to parking_sessions — the confirmation path that placed the pin
 * ("steps+egress" / "safety_net_backfill" / "bt" / "manual" / …), so a diagnostic can attribute a
 * parking to its trigger without cross-referencing the session log. Additive, non-destructive;
 * existing rows read null (unknown provenance / pre-provenance pin). [DET-PIN-PROVENANCE-001]
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE parking_sessions ADD COLUMN detectionPath TEXT"
        )
    }
}

