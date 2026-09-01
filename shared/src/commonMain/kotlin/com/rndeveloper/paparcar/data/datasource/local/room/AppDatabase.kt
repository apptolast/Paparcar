package com.rndeveloper.paparcar.data.datasource.local.room

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

/** The declared schema version — single source for the annotation and the tests that pin what an
 *  opened file must end up at. [DB-A-NEW-COLUMN-NEEDS-ITS-MIGRATION-001] */
const val PAPARCAR_DB_VERSION = 2

@Database(
    entities = [
        UserParkingEntity::class,
        UserProfileEntity::class,
        VehicleEntity::class,
        SpotEntity::class,
        ZoneEntity::class,
        GeocoderCacheEntity::class,
    ],
    // v1 was the baseline of [DATA-ROOM-STARTS-AT-VERSION-ONE-001]: Paparcar had never shipped, so
    // the old v2..v20 chain described upgrades of databases that only ever existed on our own test
    // phones, and those were wiped along with their accounts on the 2026-08-30 reset.
    //
    // That reset is also why the door its comment promised for "the first public release" closed
    // EARLY: the bench phones now hold v1 data that must survive (field-test state; wiping them is
    // forbidden), so from v1 onward every schema change bumps this number and ships its Migration.
    // [DB-A-NEW-COLUMN-NEEDS-ITS-MIGRATION-001] is the measurement of what happens otherwise:
    // PARK-A-REFUTED-PIN-LEAVES-THE-HISTORY-001 added `retractedAtMs` while this stayed 1, and the
    // first install on a live phone (Redmi, 2026-09-01 01:28) failed EVERY read with "Room cannot
    // verify the data integrity" — same version, different identity hash, a case the destructive
    // fallback does NOT cover (it only watches version CHANGES).
    version = PAPARCAR_DB_VERSION,
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