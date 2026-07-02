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
    // v8: parking_sessions gains addressCountryCode so the published Spot keeps its
    // country code on the Room round-trip (ProcessConfirmedDepartureUseCase reloads the
    // session from Room before publishing). No explicit MIGRATION_7_8 is registered;
    // the configured fallbackToDestructiveMigration recreates the table on upgrade and
    // the active session is restored from Firestore on next bootstrap.
    // v9: vehicles gains updatedAt + pendingSync for inbound sync reconciliation. MIGRATION_8_9 is
    // registered (ADD COLUMN) so the cache — and any un-synced offline edits — survive.
    // v10: same treatment for zones (MIGRATION_9_10). [SYNC-RECONCILE-001]
    version = 10,
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