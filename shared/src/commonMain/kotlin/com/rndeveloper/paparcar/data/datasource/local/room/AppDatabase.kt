package com.rndeveloper.paparcar.data.datasource.local.room

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

/** The declared schema version — single source for the annotation and the tests that pin what an
 *  opened file must end up at. [DB-A-NEW-COLUMN-NEEDS-ITS-MIGRATION-001] */
const val PAPARCAR_DB_VERSION = 1

@Database(
    entities = [
        UserParkingEntity::class,
        UserProfileEntity::class,
        VehicleEntity::class,
        SpotEntity::class,
        ZoneEntity::class,
        GeocoderCacheEntity::class,
    ],
    // v1 is the baseline of [DATA-ROOM-STARTS-AT-VERSION-ONE-001]: Paparcar had never shipped, so
    // the old v2..v20 chain described upgrades of databases that only ever existed on our own test
    // phones, and those were wiped along with their accounts on the 2026-08-30 reset.
    //
    // It briefly read 2. [DB-A-NEW-COLUMN-NEEDS-ITS-MIGRATION-001] had to bump it on 2026-09-01
    // because the bench phones held v1 data that MUST survive — a car was parked and being watched,
    // so clearing them was forbidden. [DATA-ROOM-RETURNS-TO-VERSION-ONE-001] retired that step on
    // the 09-03 release wipe, when nothing had to survive any more: `1.json` and `2.json` carried
    // the SAME identity hash, so v2 was a step from a v1 that will never exist on any device again.
    //
    // ⛔ The door this comment promises stays open only until the first public release. From the
    // first real user, data must survive: every schema change bumps this number AND ships its
    // Migration in [ALL_MIGRATIONS]. Skipping the bump is the worse half — same version + different
    // schema is the ONE shape `fallbackToDestructiveMigration` does not cover (it only watches
    // version CHANGES): it neither migrates nor wipes, it refuses the file on every open, forever.
    // Measured, not supposed: [AppDatabaseV1BaselineTest].
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