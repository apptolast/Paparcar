package io.apptolast.paparcar.data.datasource.local.room

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(
    entities = [
        UserParkingEntity::class,
        UserProfileEntity::class,
        VehicleEntity::class,
        SpotEntity::class,
        ZoneEntity::class,
        GeocoderCacheEntity::class,
    ],
    // v6/v8: identity bumps (no schema change) — MIGRATION_5_6 / MIGRATION_7_8 are empty, needed
    // only for a contiguous chain. addressCountryCode was actually added at v7 (MIGRATION_6_7),
    // not v8; the old comment here was wrong. [AUDIT-DATA-001 M7]
    // v9: vehicles gains updatedAt + pendingSync for inbound sync reconciliation. MIGRATION_8_9 is
    // registered (ADD COLUMN) so the cache — and any un-synced offline edits — survive.
    // v10: same treatment for zones (MIGRATION_9_10). [SYNC-RECONCILE-001]
    // v11: parking_sessions gains tripMaxSpeedMps + armEvidence — local-only detection
    // provenance for the repark-plausibility guard (MIGRATION_10_11). [DET-SOLID-001]
    // v12: parking_sessions gains updatedAt + pendingSync — Last-Write-Wins inbound-sync columns
    // so a stale remote snapshot can't resurrect an ended session (MIGRATION_11_12).
    // [SYNC-RECONCILE-USERPARKING-001]
    // v13: parking_sessions gains detectionPath — the confirmation path that placed the pin, synced
    // for remote provenance diagnostics (MIGRATION_12_13). [DET-PIN-PROVENANCE-001]
    version = 13,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun parkingSessionDao(): UserParkingDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun vehicleDao(): VehicleDao
    abstract fun spotDao(): SpotDao
    abstract fun zoneDao(): ZoneDao
    abstract fun geocoderCacheDao(): GeocoderCacheDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>