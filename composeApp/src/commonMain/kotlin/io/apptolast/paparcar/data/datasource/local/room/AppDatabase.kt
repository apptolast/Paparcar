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
    // v14: parking_sessions gains zoneRadiusMeters — the honest-close approximate-zone radius,
    // local-only, null = exact point (MIGRATION_13_14). [DET-HONEST-CLOSE-001]
    // v15: parking_sessions gains spotType — the parking origin (auto / manual / home), synced so the
    // history detail screen shows the real detection method (MIGRATION_14_15). [HISTORY-DETAIL-001]
    // v16: parking_sessions gains routePolyline — the driven route to the parking (Google-encoded
    // polyline), rendered in history detail and synced to Firestore (MIGRATION_15_16). [DET-ROUTE-TRACK-001]
    // v17: parking_sessions gains routeSnapped — whether routePolyline is the final on-road line vs raw
    // fixes still awaiting the one-time snap (MIGRATION_16_17). [DET-ROUTE-SNAP-STORE-001]
    // v18: parking_sessions gains routeInferredSpans + routeInferredResolution — provenance of
    // road-inferred stretches (reconstructed data holes) and the user's verdict on them
    // (MIGRATION_17_18). [ROUTE-GAP-HONEST-001]
    // v19: parking_sessions gains provisionalDepartureAtMs — a DEDUCED departure that published the
    // spot provisionally and kept the car, local-only (MIGRATION_18_19). [DET-HANDOFF-NOT-MANUAL-001 §B]
    // v20: cached_spots gains status — CONFIRMED / PROVISIONAL / RETRACTED, so a withdrawn report
    // can explain itself instead of silently vanishing (MIGRATION_19_20). [DET-HANDOFF-NOT-MANUAL-001 §B.3]
    version = 20,
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