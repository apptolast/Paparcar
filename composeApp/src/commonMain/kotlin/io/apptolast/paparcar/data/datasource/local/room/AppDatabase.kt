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
    // v1 is the baseline. Paparcar has never shipped to production, so the internal Play test
    // starts from an empty database on every install and there is nothing to migrate FROM. The old
    // v2..v20 chain and its 16 exported schemas described upgrades of databases that only ever
    // existed on our own test phones — those get wiped along with their accounts.
    // [DATA-ROOM-STARTS-AT-VERSION-ONE-001]
    //
    // The first public release closes this door: from then on there ARE users whose data must
    // survive, so every schema change needs its own Migration and its exported schema.
    version = 1,
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